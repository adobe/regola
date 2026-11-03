/*
 *  Copyright 2026 Adobe. All rights reserved.
 *  This file is licensed to you under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License. You may obtain a copy
 *  of the License at http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software distributed under
 *  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
 *  OF ANY KIND, either express or implied. See the License for the specific language
 *  governing permissions and limitations under the License
 */

package com.adobe.abp.regola.rules;

import com.adobe.abp.regola.facts.FactsResolver;
import com.adobe.abp.regola.results.Result;
import com.adobe.abp.regola.results.RuleResult;
import com.adobe.abp.regola.results.ValuesRuleResult;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeafRuleSnapshotTest {

    private static final String KEY = "key";
    private static final Executor DIRECT_EXECUTOR = Runnable::run;

    @Mock
    private FactsResolver resolver;

    static Stream<Arguments> leafRules() {
        return Stream.of(
                Arguments.of(
                        "StringRule EQUALS",
                        new StringRule(KEY, Operator.EQUALS, "value"),
                        "value",
                        Result.VALID,
                        false),
                Arguments.of(
                        "StringRule CONTAINS",
                        new StringRule(KEY, Operator.CONTAINS, "value"),
                        Set.of("value"),
                        Result.VALID,
                        true),
                Arguments.of(
                        "SetRule IN",
                        new SetRule<String>(DIRECT_EXECUTOR)
                                .setValues(Set.of("value"))
                                .setOperator(Operator.IN)
                                .setKey(KEY),
                        "value",
                        Result.VALID,
                        false),
                Arguments.of(
                        "SetRule INTERSECTS",
                        new SetRule<String>(DIRECT_EXECUTOR)
                                .setValues(Set.of("value"))
                                .setOperator(Operator.INTERSECTS)
                                .setKey(KEY),
                        Set.of("value"),
                        Result.VALID,
                        true),
                Arguments.of(
                        "RangeRule BETWEEN",
                        new RangeRule<Integer>(DIRECT_EXECUTOR)
                                .setMin(1)
                                .setMax(10)
                                .setOperator(Operator.BETWEEN)
                                .setKey(KEY),
                        5,
                        Result.VALID,
                        false),
                Arguments.of(
                        "ExistsRule",
                        new ExistsRule(DIRECT_EXECUTOR, KEY),
                        "value",
                        Result.VALID,
                        false),
                Arguments.of(
                        "NullRule",
                        new NullRule(DIRECT_EXECUTOR, KEY),
                        "value",
                        Result.INVALID,
                        false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("leafRules")
    @DisplayName("should publish fact details before a terminal result is visible to a poller")
    void publishesFactBeforeResult(
            String name, Rule rule, Object value, Result expectedResult, boolean collection)
            throws Exception {
        final var readerExecutor = Executors.newSingleThreadExecutor();
        try {
            for (int i = 0; i < 100; i++) {
                final var fact = new CompletableFuture<Object>();
                when(resolver.resolveFact(KEY))
                        .thenReturn(fact);
                final var evaluation = rule.evaluate(resolver);
                final var status = evaluation.status();
                final var readerReady = new CountDownLatch(1);
                final var reader = CompletableFuture.supplyAsync(() -> {
                    readerReady.countDown();
                    return awaitTerminalSnapshot(evaluation);
                }, readerExecutor);

                assertThat(readerReady.await(3, TimeUnit.SECONDS))
                        .isTrue();
                fact.complete(value);

                final var snapshot = reader.get(3, TimeUnit.SECONDS);
                assertThat(snapshot.getResult())
                        .isEqualTo(expectedResult);
                assertThat(collection ? snapshot.getActualValues() : snapshot.getActualValue())
                        .isEqualTo(value);
                assertThat(status.get(3, TimeUnit.SECONDS))
                        .isEqualTo(expectedResult);
            }
        } finally {
            readerExecutor.shutdownNow();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("leafRules")
    @DisplayName("should publish failure details before FAILED is visible to a poller")
    void publishesFailureBeforeResult(
            String name, Rule rule, Object value, Result expectedResult, boolean collection)
            throws Exception {
        final var fact = new CompletableFuture<Object>();
        when(resolver.resolveFact(KEY))
                .thenReturn(fact);
        final var evaluation = rule.evaluate(resolver);
        final var status = evaluation.status();
        final var messageRequested = new CountDownLatch(1);
        final var releaseMessage = new CountDownLatch(1);
        final var failure = new BlockingFailure(messageRequested, releaseMessage);
        final var writerExecutor = Executors.newSingleThreadExecutor();
        try {
            final var writer = CompletableFuture.runAsync(
                    () -> fact.completeExceptionally(failure), writerExecutor);

            assertThat(messageRequested.await(3, TimeUnit.SECONDS))
                    .isTrue();
            assertThat(evaluation.snapshot().getResult())
                    .isEqualTo(Result.MAYBE);
            releaseMessage.countDown();

            final var snapshot = awaitTerminalSnapshot(evaluation);
            assertThat(snapshot)
                    .extracting(RuleResult::getResult, RuleResult::getMessage, RuleResult::getCause)
                    .containsExactly(Result.FAILED, "fetch failed", failure);
            assertThat(status.get(3, TimeUnit.SECONDS))
                    .isEqualTo(Result.FAILED);
            writer.get(3, TimeUnit.SECONDS);
        } finally {
            releaseMessage.countDown();
            writerExecutor.shutdownNow();
        }
    }

    private static ValuesRuleResult<?> awaitTerminalSnapshot(EvaluationResult evaluation) {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline && !Thread.currentThread().isInterrupted()) {
            final var snapshot = (ValuesRuleResult<?>) evaluation.snapshot();
            if (snapshot.getResult() != Result.MAYBE) {
                return snapshot;
            }
            Thread.onSpinWait();
        }
        throw new AssertionError("No terminal snapshot was published");
    }

    private static final class BlockingFailure extends CompletionException {
        private static final long serialVersionUID = 1L;
        private final transient CountDownLatch messageRequested;
        private final transient CountDownLatch releaseMessage;

        private BlockingFailure(CountDownLatch messageRequested, CountDownLatch releaseMessage) {
            super("fetch failed", null);
            this.messageRequested = messageRequested;
            this.releaseMessage = releaseMessage;
        }

        @Override
        public String getMessage() {
            messageRequested.countDown();
            try {
                if (!releaseMessage.await(3, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Failure message was not released");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while reading failure message", e);
            }
            return super.getMessage();
        }
    }
}
