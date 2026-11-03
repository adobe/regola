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

import com.adobe.abp.regola.actions.Action;
import com.adobe.abp.regola.facts.FactsResolver;
import com.adobe.abp.regola.results.Result;
import com.adobe.abp.regola.results.ValuesRuleResult;
import java.util.ArrayDeque;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LeafRuleCancellationTest {

    private static final String KEY = "key";

    static Stream<Arguments> leafRules() {
        return Stream.of(
                Arguments.of("StringRule EQUALS", (Supplier<Rule>) () -> new StringRule(KEY, Operator.EQUALS, "value")),
                Arguments.of("StringRule CONTAINS", (Supplier<Rule>) () -> new StringRule(KEY, Operator.CONTAINS, "value")),
                Arguments.of("SetRule IN", (Supplier<Rule>) () -> new SetRule<String>()
                        .setValues(Set.of("value"))
                        .setOperator(Operator.IN)
                        .setKey(KEY)),
                Arguments.of("RangeRule BETWEEN", (Supplier<Rule>) () -> new RangeRule<Integer>()
                        .setMin(1)
                        .setMax(10)
                        .setOperator(Operator.BETWEEN)
                        .setKey(KEY)),
                Arguments.of("ExistsRule", (Supplier<Rule>) () -> new ExistsRule(KEY)),
                Arguments.of("NullRule", (Supplier<Rule>) () -> new NullRule(KEY)));
    }

    static Stream<Arguments> queuedLeafRules() {
        return Stream.of(
                Arguments.of("SetRule IN", (Function<Executor, Rule>) executor ->
                        new SetRule<Integer>(executor)
                                .setValues(Set.of(1))
                                .setOperator(Operator.IN)
                                .setKey(KEY)),
                Arguments.of("SetRule INTERSECTS", (Function<Executor, Rule>) executor ->
                        new SetRule<Integer>(executor)
                                .setValues(Set.of(1))
                                .setOperator(Operator.INTERSECTS)
                                .setKey(KEY)),
                Arguments.of("RangeRule BETWEEN", (Function<Executor, Rule>) executor ->
                        new RangeRule<Integer>(executor)
                                .setMin(1)
                                .setMax(10)
                                .setOperator(Operator.BETWEEN)
                                .setKey(KEY)),
                Arguments.of("ExistsRule", (Function<Executor, Rule>) executor ->
                        new ExistsRule(executor, KEY)),
                Arguments.of("NullRule", (Function<Executor, Rule>) executor ->
                        new NullRule(executor, KEY)),
                Arguments.of("StringRule unsupported", (Function<Executor, Rule>) executor ->
                        new StringRule(executor, KEY, Operator.IN, "value")),
                Arguments.of("SetRule unsupported", (Function<Executor, Rule>) executor ->
                        new SetRule<Integer>(executor)
                                .setOperator(Operator.EQUALS)
                                .setKey(KEY)),
                Arguments.of("RangeRule unsupported", (Function<Executor, Rule>) executor ->
                        new RangeRule<Integer>(executor)
                                .setOperator(Operator.EQUALS)
                                .setKey(KEY)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("queuedLeafRules")
    @DisplayName("should skip checks still queued on the rule executor when cancelled")
    void cancelQueuedCheck(String name, Function<Executor, Rule> ruleFactory) {
        final var tasks = new ArrayDeque<Runnable>();
        final var resolver = mock(FactsResolver.class);
        when(resolver.resolveFact(KEY))
                .thenReturn(CompletableFuture.completedFuture(1));
        final var actionCalls = new AtomicInteger();
        final var rule = ruleFactory.apply(tasks::add);
        rule.setAction(new Action()
                .setOnCompletion((result, throwable, ruleResult) -> actionCalls.incrementAndGet()));
        final var evaluationResult = rule.evaluate(resolver);
        final var status = evaluationResult.status();

        assertThat(tasks)
                .hasSize(1);
        assertThat(status)
                .isNotDone();

        evaluationResult.cancel();
        tasks.remove().run();

        final var snapshot = (ValuesRuleResult<?>) evaluationResult.snapshot();
        assertThat(status)
                .isCancelled();
        assertThat(snapshot.getResult())
                .isEqualTo(Result.MAYBE);
        assertThat(snapshot.getActualValue())
                .isNull();
        assertThat(snapshot.getActualValues())
                .isNull();
        assertThat(actionCalls)
                .hasValue(0);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("leafRules")
    @DisplayName("should cancel the pending fact, keep MAYBE and not run its action when cancelled")
    void cancelPendingLeafRule(String name, Supplier<Rule> ruleSupplier) {
        final var fact = new CompletableFuture<Object>();
        final var resolver = mock(FactsResolver.class);
        when(resolver.resolveFact(KEY)).thenReturn(fact);
        when(resolver.supportsCancellation()).thenReturn(true);
        final var actionCalls = new AtomicInteger();
        final var rule = ruleSupplier.get();
        rule.setAction(new Action().setOnCompletion((result, throwable, ruleResult) -> actionCalls.incrementAndGet()));

        final var evaluationResult = rule.evaluate(resolver);
        final var status = evaluationResult.status();
        evaluationResult.cancel();

        final var snapshot = evaluationResult.snapshot();
        assertThat(fact).isCancelled();
        assertThat(status).isCancelled();
        assertThat(snapshot.getResult()).isEqualTo(Result.MAYBE);
        assertThat(snapshot.isCancelled()).isTrue();
        assertThat(snapshot.isSkipped()).isFalse();
        assertThat(actionCalls).hasValue(0);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("leafRules")
    @DisplayName("should leave the fact untouched when the resolver does not support cancellation")
    void cancelWithResolverNotSupportingCancellation(String name, Supplier<Rule> ruleSupplier) {
        final var sharedFact = new CompletableFuture<Object>();
        final var resolver = mock(FactsResolver.class);
        when(resolver.resolveFact(KEY)).thenReturn(sharedFact);

        final var cancelled = ruleSupplier.get().evaluate(resolver);
        final var cancelledStatus = cancelled.status();
        cancelled.cancel();
        final var other = ruleSupplier.get().evaluate(resolver);
        final var otherStatus = other.status();
        sharedFact.complete("value");

        assertThat(cancelledStatus).isCancelled();
        assertThat(sharedFact).isCompletedWithValue("value");
        assertThat(otherStatus.join()).isNotEqualTo(Result.MAYBE);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("leafRules")
    @DisplayName("should not resolve the fact when cancelled before being started")
    void cancelBeforeStart(String name, Supplier<Rule> ruleSupplier) {
        final var resolver = mock(FactsResolver.class);
        final var rule = ruleSupplier.get();

        final var evaluationResult = rule.evaluate(resolver);
        evaluationResult.cancel();

        final var snapshot = evaluationResult.snapshot();
        assertThat(evaluationResult.status()).isCancelled();
        assertThat(snapshot.getResult()).isEqualTo(Result.MAYBE);
        assertThat(snapshot.isSkipped()).isTrue();
        assertThat(snapshot.isCancelled()).isFalse();
        verifyNoInteractions(resolver);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("leafRules")
    @DisplayName("should keep the result when cancelled after completion")
    void cancelAfterCompletion(String name, Supplier<Rule> ruleSupplier) {
        final var resolver = mock(FactsResolver.class);
        when(resolver.resolveFact(KEY)).thenReturn(CompletableFuture.completedFuture("value"));
        final var rule = ruleSupplier.get();

        final var evaluationResult = rule.evaluate(resolver);
        final var result = evaluationResult.status().join();
        evaluationResult.cancel();

        final var snapshot = evaluationResult.snapshot();
        assertThat(snapshot.getResult()).isEqualTo(result);
        assertThat(snapshot.isCancelled()).isFalse();
        assertThat(snapshot.isSkipped()).isFalse();
        assertThat(evaluationResult.status().join()).isEqualTo(result);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("leafRules")
    @DisplayName("should keep the result when cancelled after the result is decided but before the status completes")
    void cancelAfterDecision(String name, Supplier<Rule> ruleSupplier) {
        final var resolver = mock(FactsResolver.class);
        when(resolver.resolveFact(KEY)).thenReturn(CompletableFuture.completedFuture("value"));
        final var rule = ruleSupplier.get();
        final var evaluationResult = new AtomicReference<EvaluationResult>();
        rule.setAction(new Action()
                .setOnCompletion((result, throwable, ruleResult) -> evaluationResult.get().cancel()));

        evaluationResult.set(rule.evaluate(resolver));
        final var result = evaluationResult.get().status().join();

        assertThat(result).isNotEqualTo(Result.MAYBE);
        assertThat(evaluationResult.get().snapshot().getResult()).isEqualTo(result);
    }
}
