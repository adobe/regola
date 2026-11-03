/*
 *  Copyright 2023 Adobe. All rights reserved.
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
import com.adobe.abp.regola.datafetchers.Context;
import com.adobe.abp.regola.datafetchers.DataFetcher;
import com.adobe.abp.regola.facts.DataSource;
import com.adobe.abp.regola.facts.Fact;
import com.adobe.abp.regola.facts.FactsResolver;
import com.adobe.abp.regola.facts.SimpleFactsResolver;
import com.adobe.abp.regola.mockdatafetchers.BlockingBeforeFutureDataFetcher;
import com.adobe.abp.regola.mockdatafetchers.SynchronouslyFailingDataFetcher;
import com.adobe.abp.regola.mockrules.BlockingStartRule;
import com.adobe.abp.regola.mockrules.DelayedRule;
import com.adobe.abp.regola.mockrules.ErrorRule;
import com.adobe.abp.regola.mockrules.ExceptionRule;
import com.adobe.abp.regola.mockrules.ImmediateRule;
import com.adobe.abp.regola.mockrules.MockRule;
import com.adobe.abp.regola.mockrules.PendingRule;
import com.adobe.abp.regola.results.MultiaryBooleanRuleResult;
import com.adobe.abp.regola.results.Result;
import com.adobe.abp.regola.results.RuleResult;
import com.adobe.abp.regola.results.ValuesRuleResult;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Testing AndRule")
class AndRuleTest {

    private AndRule rule;

    private MultiaryBooleanRuleResult.RuleResultBuilder ruleResultBuilder;

    private final FactsResolver resolver = mock(FactsResolver.class);

    private enum BlockingDataSources implements DataSource {
        FIRST,
        SECOND
    }

    @BeforeEach
    void setup() {
        rule = new AndRule();

        ruleResultBuilder = MultiaryBooleanRuleResult.builder().with(r -> r.type = RuleType.AND.getName());
    }

    @Test
    @DisplayName("should have the expected AND type")
    void ruleHasExpectedType() {
        assertThat(rule.getType()).isEqualTo("AND");
    }

    private ValuesRuleResult<?> buildRuleResult(KeyBasedRule rule, Result result) {
        return buildRuleResult(rule, result, false);
    }

    private ValuesRuleResult<?> buildRuleResult(KeyBasedRule rule, Result result, boolean ignore) {
        return ValuesRuleResult.builder().with(r -> {
            r.type = rule.getType();
            r.key = rule.getKey();
            r.result = result;
            r.ignored = ignore;
        }).build();
    }

    private MultiaryBooleanRuleResult buildRuleResult(MultiaryBooleanRuleResult.RuleResultBuilder ruleResultBuilder, Result result) {
        return ruleResultBuilder.with(r -> {
            r.result = result;
            r.rules = Set.of();
        }).build();
    }

    private MultiaryBooleanRuleResult buildInterimResult(
            KeyBasedRule ruleOne,
            KeyBasedRule ruleTwo) {
        return ruleResultBuilder.with(r -> {
            r.result = Result.MAYBE;
            r.rules = Set.of(
                    buildRuleResult(ruleOne, Result.MAYBE, ruleOne.isIgnore()),
                    buildRuleResult(ruleTwo, Result.MAYBE, ruleTwo.isIgnore()));
        }).build();
    }

    @Nested
    @DisplayName("with synchronously blocking data fetchers should")
    class WithSynchronouslyBlockingDataFetchersTest {

        @Test
        @DisplayName("start independent fetches concurrently when the facts resolver has a concurrent executor")
        void startsIndependentFetchesConcurrently() throws InterruptedException {
            final var fetchesStarted = new CountDownLatch(2);
            final var releaseFetches = new CountDownLatch(1);
            final ExecutorService resolverExecutor = Executors.newFixedThreadPool(2);
            final ExecutorService evaluationExecutor = Executors.newSingleThreadExecutor();

            try {
                final Context context = new Context() {
                };
                final Map<DataSource, DataFetcher<?, Context>> dataFetchers = Map.of(
                        BlockingDataSources.FIRST,
                        new BlockingBeforeFutureDataFetcher(
                                "first-request", "first-value", fetchesStarted, releaseFetches),
                        BlockingDataSources.SECOND,
                        new BlockingBeforeFutureDataFetcher(
                                "second-request", "second-value", fetchesStarted, releaseFetches)
                );
                final var factsResolver = new SimpleFactsResolver<>(
                        context, dataFetchers, resolverExecutor);
                factsResolver.addFact(new Fact<>(
                        "first-fact", BlockingDataSources.FIRST, data -> data));
                factsResolver.addFact(new Fact<>(
                        "second-fact", BlockingDataSources.SECOND, data -> data));

                rule.setRules(List.of(
                        new StringRule("first-fact", Operator.EQUALS, "first-value"),
                        new StringRule("second-fact", Operator.EQUALS, "second-value")
                ));

                final CompletableFuture<Result> evaluation = CompletableFuture.supplyAsync(
                        () -> rule.evaluate(factsResolver).status().join(),
                        evaluationExecutor);

                try {
                    assertThat(fetchesStarted.await(5, TimeUnit.SECONDS)).isTrue();
                } finally {
                    releaseFetches.countDown();
                }

                assertThat(evaluation.join()).isEqualTo(Result.VALID);
            } finally {
                releaseFetches.countDown();
                shutdown(evaluationExecutor);
                shutdown(resolverExecutor);
            }
        }

        private void shutdown(ExecutorService executor) throws InterruptedException {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Nested
    @DisplayName("with synchronous failures should")
    class SynchronousFailureTest {

        @Test
        @DisplayName("evaluate as failed when a subrule's data fetcher fails synchronously")
        void synchronousFetchFailureEvaluatesAsFailed() {
            final var factsResolver = new SimpleFactsResolver<>(
                    new Context() {
                    },
                    Map.<DataSource, DataFetcher<?, Context>>of(BlockingDataSources.FIRST, new SynchronouslyFailingDataFetcher()));
            factsResolver.addFact(new Fact<>("failing-fact", BlockingDataSources.FIRST, data -> data));
            rule.setRules(List.of(
                    new StringRule("failing-fact", Operator.EQUALS, "value"),
                    new ImmediateRule("rule-2", Result.VALID)));

            assertThat(rule.evaluate(factsResolver).status()).isCompletedWithValue(Result.FAILED);
        }

        @Test
        @DisplayName("ignore an ignored subrule that throws synchronously when started")
        void ignoredSubruleThrowingSynchronouslyIsIgnored() {
            final FactsResolver throwingResolver = mock(FactsResolver.class);
            when(throwingResolver.resolveFact("throwing-fact")).thenThrow(new IllegalStateException("boom"));
            final var ruleOne = new StringRule("throwing-fact", Operator.EQUALS, "value");
            ruleOne.setIgnore(true);
            final var ruleTwo = new ImmediateRule("rule-2", Result.VALID);
            rule.setRules(List.of(ruleOne, ruleTwo));

            assertThat(rule.evaluate(throwingResolver).status()).isCompletedWithValue(Result.VALID);
            assertThat(ruleTwo.getStarts()).isEqualTo(1);
        }

        @Test
        @DisplayName("ignore an ignored subrule that throws an error synchronously when started")
        void ignoredSubruleThrowingErrorSynchronouslyIsIgnored() {
            final var ruleOne = new ErrorRule("rule-1");
            ruleOne.setIgnore(true);
            rule.setRules(List.of(ruleOne, new ImmediateRule("rule-2", Result.VALID)));

            assertThat(rule.evaluate(resolver).status())
                    .isCompletedWithValue(Result.VALID);
        }

        @Test
        @DisplayName("fail through its status when a subrule cannot be evaluated")
        void unevaluableSubruleFailsStatus() {
            rule.setRules(Arrays.asList(new ImmediateRule("rule-1", Result.VALID), null));

            final var evaluationResult = rule.evaluate(resolver);

            assertThat(evaluationResult.status())
                    .isCompletedExceptionally();
            assertThat(evaluationResult.snapshot().getResult())
                    .isEqualTo(Result.FAILED);
        }
    }

    @Nested
    @DisplayName("once the result is decided should")
    class EarlyStopTest {

        @ParameterizedTest(name = "not start later subrules after a {0} subrule")
        @EnumSource(value = Result.class, names = {"VALID"}, mode = EnumSource.Mode.EXCLUDE)
        void doesNotStartLaterSubrules(Result decidingResult) {
            final var ruleOne = new ImmediateRule("rule-1", decidingResult);
            final var ruleTwo = new ImmediateRule("rule-2", Result.VALID);
            rule.setRules(List.of(ruleOne, ruleTwo));

            final var evaluationResult = rule.evaluate(resolver);

            assertThat(evaluationResult.status()).isCompletedWithValue(decidingResult);
            assertThat(ruleTwo.getStarts()).isZero();
            assertThat(evaluationResult.snapshot())
                    .isInstanceOfSatisfying(MultiaryBooleanRuleResult.class,
                            ruleResult -> assertThat(ruleResult.getRules())
                                    .extracting(RuleResult::getType, RuleResult::getResult)
                                    .containsExactlyInAnyOrder(
                                            Tuple.tuple("IMMEDIATE_RULE", decidingResult),
                                            Tuple.tuple("IMMEDIATE_RULE", Result.MAYBE)));
        }

        @Test
        @DisplayName("keep starting subrules after an ignored subrule that is not valid")
        void ignoredSubruleDoesNotStopEvaluation() {
            final var ruleOne = new ImmediateRule("rule-1", Result.INVALID);
            ruleOne.setIgnore(true);
            final var ruleTwo = new ImmediateRule("rule-2", Result.VALID);
            rule.setRules(List.of(ruleOne, ruleTwo));

            assertThat(rule.evaluate(resolver).status()).isCompletedWithValue(Result.VALID);
            assertThat(ruleTwo.getStarts()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("when cancelling should")
    class CancellationTest {

        @Test
        @DisplayName("cancel running subrules once the result is decided")
        void cancelsRunningSubrulesOnceDecided() {
            final var running = new PendingRule("running");
            rule.setRules(List.of(running, new ImmediateRule("rule-2", Result.INVALID)));

            assertThat(rule.evaluate(resolver).status()).isCompletedWithValue(Result.INVALID);
            assertThat(running.isCancelled()).isTrue();
        }

        @Test
        @DisplayName("stop a pending evaluation without a result and without running its action")
        void cancelPendingEvaluation() {
            final var running = new PendingRule("running");
            final var actionCalls = new AtomicInteger();
            final var cancellations = new AtomicInteger();
            rule.setRules(List.of(running));
            rule.setAction(new Action()
                    .setOnCompletion((result, throwable, ruleResult) -> actionCalls.incrementAndGet())
                    .setOnCancellation(ruleResult -> cancellations.incrementAndGet()));

            final var evaluationResult = rule.evaluate(resolver);
            final var status = evaluationResult.status();
            evaluationResult.cancel();

            assertThat(status).isCancelled();
            assertThat(evaluationResult.snapshot().getResult()).isEqualTo(Result.MAYBE);
            assertThat(evaluationResult.snapshot().isCancelled()).isTrue();
            assertThat(evaluationResult.snapshot().isSkipped()).isFalse();
            assertThat(actionCalls).hasValue(0);
            assertThat(cancellations).hasValue(1);
            assertThat(running.isCancelled()).isTrue();
        }

        @Test
        @DisplayName("not start subrules when cancelled before being started")
        void cancelBeforeStart() {
            final var subrule = new PendingRule("subrule");
            rule.setRules(List.of(subrule));

            final var evaluationResult = rule.evaluate(resolver);
            evaluationResult.cancel();

            assertThat(evaluationResult.status()).isCancelled();
            assertThat(evaluationResult.snapshot().isSkipped()).isTrue();
            assertThat(evaluationResult.snapshot().isCancelled()).isFalse();
            assertThat(subrule.getStarts()).isZero();
        }

        @Test
        @DisplayName("mark running subrules as cancelled and unstarted subrules as skipped once decided")
        void marksCancelledAndSkippedSubrules() {
            when(resolver.resolveFact("running")).thenReturn(new CompletableFuture<>());
            rule.setRules(List.of(
                    new StringRule("running", Operator.EQUALS, "value"),
                    new ImmediateRule("deciding", Result.INVALID),
                    new StringRule("unstarted", Operator.EQUALS, "value")));

            final var evaluationResult = rule.evaluate(resolver);

            assertThat(evaluationResult.status()).isCompletedWithValue(Result.INVALID);
            assertThat(evaluationResult.snapshot())
                    .isInstanceOfSatisfying(MultiaryBooleanRuleResult.class,
                            ruleResult -> assertThat(ruleResult.getRules())
                                    .extracting(RuleResult::getResult, RuleResult::isCancelled, RuleResult::isSkipped)
                                    .containsExactlyInAnyOrder(
                                            Tuple.tuple(Result.MAYBE, true, false),
                                            Tuple.tuple(Result.INVALID, false, false),
                                            Tuple.tuple(Result.MAYBE, false, true)));
            assertThat(evaluationResult.snapshot().isCancelled()).isFalse();
        }

        @Test
        @DisplayName("keep cancelling subrules when a cancellation callback throws")
        void cancellationCallbackFailureDoesNotStopCancellingSiblings() {
            when(resolver.resolveFact("first")).thenReturn(new CompletableFuture<>());
            when(resolver.resolveFact("second")).thenReturn(new CompletableFuture<>());
            final var first = new StringRule("first", Operator.EQUALS, "value");
            first.setAction(new Action().setOnCancellation(ruleResult -> {
                throw new IllegalStateException("boom");
            }));
            final var secondCancellations = new AtomicInteger();
            final var second = new StringRule("second", Operator.EQUALS, "value");
            second.setAction(new Action().setOnCancellation(ruleResult -> secondCancellations.incrementAndGet()));
            rule.setRules(List.of(first, second, new ImmediateRule("deciding", Result.INVALID)));

            final var evaluationResult = rule.evaluate(resolver);

            assertThat(evaluationResult.status()).isCompletedWithValue(Result.INVALID);
            assertThat(secondCancellations).hasValue(1);
        }

        @Test
        @DisplayName("report every rule of a nested subrule that was never started as skipped")
        void reportsSubtreeOfSkippedNestedRule() {
            rule.setRules(List.of(
                    new ConstantRule(Result.INVALID),
                    new AndRule(List.of(new ExistsRule("nested")))));

            final var evaluationResult = rule.evaluate(resolver);

            assertThat(evaluationResult.status())
                    .isCompletedWithValue(Result.INVALID);
            assertThat(nestedRuleResults(evaluationResult.snapshot()))
                    .extracting(RuleResult::getResult, RuleResult::isSkipped)
                    .containsExactly(Tuple.tuple(Result.MAYBE, true));
        }

        @Test
        @DisplayName("run the cancellation action of every rule of a nested subrule never started")
        void cancelsSubtreeOfSkippedNestedRule() {
            final var cancellations = new AtomicInteger();
            final var nestedLeaf = new ExistsRule("nested");
            nestedLeaf.setAction(new Action()
                    .setOnCancellation(ruleResult -> cancellations.incrementAndGet()));
            rule.setRules(List.of(
                    new ConstantRule(Result.INVALID),
                    new AndRule(List.of(nestedLeaf))));

            rule.evaluate(resolver).status();

            assertThat(cancellations)
                    .hasValue(1);
        }

        @Test
        @DisplayName("complete with the decided result when a cancellation callback throws")
        void cancellationCallbackErrorDoesNotStopCompletion() {
            rule.setRules(List.of(new ConstantRule(Result.INVALID), ruleFailingOnCancellation()));

            assertThat(rule.evaluate(resolver).status())
                    .isCompletedWithValue(Result.INVALID);
        }

        @Test
        @DisplayName("run its completion action when a cancellation callback throws an error")
        void cancellationCallbackErrorDoesNotStopCompletionAction() {
            final var completed = new AtomicReference<Result>();
            rule.setRules(List.of(new ConstantRule(Result.INVALID), ruleFailingOnCancellation()));
            rule.setAction(new Action()
                    .setOnCompletion((result, throwable, ruleResult) -> completed.set(result)));

            rule.evaluate(resolver).status();

            assertThat(completed)
                    .hasValue(Result.INVALID);
        }

        @Test
        @DisplayName("cancel its status when cancelled while a cancellation callback throws")
        void cancelWithCancellationCallbackError() {
            when(resolver.resolveFact("running")).thenReturn(new CompletableFuture<>());
            rule.setRules(List.of(ruleFailingOnCancellation()));

            final var evaluationResult = rule.evaluate(resolver);
            final var status = evaluationResult.status();
            evaluationResult.cancel();

            assertThat(status)
                    .isCancelled();
        }

        @Test
        @DisplayName("skip subrules not yet started when cancelled while starting its subrules")
        void cancelWhileStartingSkipsRemainingSubrules() throws Exception {
            final var cancelledSnapshot = new AtomicReference<RuleResult>();
            final var firstStarting = new CountDownLatch(1);
            final var releaseFirst = new CountDownLatch(1);
            final var second = new PendingRule("second");
            final var first = new BlockingStartRule("first", firstStarting, releaseFirst);
            rule.setRules(List.of(first, second));
            rule.setAction(new Action().setOnCancellation(cancelledSnapshot::set));
            final var evaluationResult = rule.evaluate(resolver);
            final ExecutorService executor = Executors.newSingleThreadExecutor();

            try {
                final var starting = executor.submit(evaluationResult::status);
                assertThat(firstStarting.await(5, TimeUnit.SECONDS))
                        .isTrue();
                evaluationResult.cancel();
                releaseFirst.countDown();
                starting.get(5, TimeUnit.SECONDS);
            } finally {
                releaseFirst.countDown();
                executor.shutdownNow();
            }

            assertThat(second.getStarts())
                    .isZero();
            assertThat(second.isCancelled())
                    .isTrue();
            assertThat(cancelledSnapshot.get())
                    .isInstanceOfSatisfying(MultiaryBooleanRuleResult.class,
                            ruleResult -> assertThat(ruleResult.getRules())
                                    .extracting(RuleResult::getType)
                                    .containsExactlyInAnyOrder(
                                            "BLOCKING_START_RULE",
                                            "PENDING_RULE"));
        }

        @Test
        @DisplayName("keep the decided result when cancelled after completion")
        void cancelAfterCompletion() {
            rule.setRules(List.of(new ImmediateRule("rule-1", Result.VALID)));

            final var evaluationResult = rule.evaluate(resolver);
            evaluationResult.status().join();
            evaluationResult.cancel();

            assertThat(evaluationResult.status()).isCompletedWithValue(Result.VALID);
            assertThat(evaluationResult.snapshot().getResult()).isEqualTo(Result.VALID);
        }

        private Rule ruleFailingOnCancellation() {
            return new ExistsRule("running").setAction(new Action()
                    .setOnCancellation(ruleResult -> {
                        throw new AssertionError("boom");
                    }));
        }

        private Collection<RuleResult> nestedRuleResults(RuleResult ruleResult) {
            return ((MultiaryBooleanRuleResult) ruleResult).getRules()
                    .stream()
                    .filter(MultiaryBooleanRuleResult.class::isInstance)
                    .map(nested -> ((MultiaryBooleanRuleResult) nested).getRules())
                    .findFirst()
                    .orElseThrow();
        }
    }

    @Nested
    @DisplayName("with mock rules should")
    class WithMockRulesTest {

        @Test
        @DisplayName("evaluate as valid if both subrules are valid")
        void validAndValid() {
            final var ruleOne = new MockRule("rule-1", Result.VALID);
            final var ruleTwo = new MockRule("rule-2", Result.VALID);
            rule.setRules(List.of(ruleOne, ruleTwo));

            final var evaluationResult = rule.evaluate(resolver);

            final var interimResult = buildInterimResult(ruleOne, ruleTwo);
            assertThat(evaluationResult.snapshot()).isEqualTo(interimResult);

            final var result = evaluationResult.status().join();

            assertThat(result).isEqualTo(Result.VALID);
            final var finalResult = ruleResultBuilder.with(r -> {
                r.result = Result.VALID;
                r.rules = Set.of(
                        buildRuleResult(ruleOne, Result.VALID),
                        buildRuleResult(ruleTwo, Result.VALID)
                );
            }).build();
            assertThat(evaluationResult.snapshot()).isEqualTo(finalResult);
        }

        @ParameterizedTest
        @EnumSource(value = Result.class,
                names = {"VALID"},
                mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("evaluate as invalid if first of the subrules is valid, but the second one is not")
        void validAndNotValid(Result controlResult) {
            final var ruleOne = new MockRule("rule-1", Result.VALID);
            final var ruleTwo = new MockRule("rule-2", controlResult);
            rule.setRules(List.of(ruleOne, ruleTwo));

            final var evaluationResult = rule.evaluate(resolver);

            final var interimResult = buildInterimResult(ruleOne, ruleTwo);
            assertThat(evaluationResult.snapshot()).isEqualTo(interimResult);

            final var result = evaluationResult.status().join();

            assertThat(result).isEqualTo(controlResult);
            assertThat(evaluationResult.snapshot())
                    .extracting(RuleResult::getType, RuleResult::getResult)
                    .containsExactly("AND", controlResult);
            assertThat(evaluationResult.snapshot())
                    .isInstanceOfSatisfying(MultiaryBooleanRuleResult.class,
                            ruleResult -> assertThat(ruleResult.getRules())
                                    .extracting(RuleResult::getType, RuleResult::getResult)
                                    .contains(Tuple.tuple("MOCK_RULE", controlResult))); // at least one of the subrules should evaluate to 'controlResult'
        }

        @ParameterizedTest
        @EnumSource(value = Result.class,
                names = {"VALID"},
                mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("evaluate as valid if first of the subrules is valid, but the second one is not but marked as ignored")
        void validAndNotValidButIgnored(Result controlResult) {
            final var ruleOne = new MockRule("rule-1", Result.VALID);
            final var ruleTwo = new MockRule("rule-2", controlResult);
            ruleTwo.setIgnore(true);
            rule.setRules(List.of(ruleOne, ruleTwo));

            final var evaluationResult = rule.evaluate(resolver);

            final var interimResult = buildInterimResult(ruleOne, ruleTwo);
            assertThat(evaluationResult.snapshot()).isEqualTo(interimResult);

            final var result = evaluationResult.status().join();

            assertThat(result).isEqualTo(Result.VALID);
            final var finalResult = ruleResultBuilder.with(r -> {
                r.result = Result.VALID;
                r.rules = Set.of(
                        buildRuleResult(ruleOne, Result.VALID),
                        buildRuleResult(ruleTwo, controlResult, true)
                );
            }).build();
            assertThat(evaluationResult.snapshot()).isEqualTo(finalResult);
        }

        @ParameterizedTest
        @EnumSource(value = Result.class,
                names = {"VALID"},
                mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("evaluate as invalid if second of the subrules is valid, but the first one is not")
        void notValidAndValid(Result controlResult) {
            final var ruleOne = new MockRule("rule-1", controlResult);
            final var ruleTwo = new MockRule("rule-2", Result.VALID);
            rule.setRules(List.of(ruleOne, ruleTwo));

            final var evaluationResult = rule.evaluate(resolver);

            final var interimResult = buildInterimResult(ruleOne, ruleTwo);
            assertThat(evaluationResult.snapshot()).isEqualTo(interimResult);

            final var result = evaluationResult.status().join();

            assertThat(result).isEqualTo(controlResult);
            assertThat(evaluationResult.snapshot())
                    .extracting(RuleResult::getType, RuleResult::getResult)
                    .containsExactly("AND", controlResult);
            assertThat(evaluationResult.snapshot())
                    .isInstanceOfSatisfying(MultiaryBooleanRuleResult.class,
                            ruleResult -> assertThat(ruleResult.getRules())
                                    .extracting(RuleResult::getType, RuleResult::getResult)
                                    .contains(Tuple.tuple("MOCK_RULE", controlResult))); // at least one of the subrules should evaluate to 'controlResult'
        }

        @ParameterizedTest
        @EnumSource(value = Result.class,
                names = {"VALID"},
                mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("evaluate as valid if second of the subrules is valid, but the first one is not but marked as ignored")
        void notValidButIgnorerAndValid(Result controlResult) {
            final var ruleOne = new MockRule("rule-1", controlResult);
            ruleOne.setIgnore(true);
            final var ruleTwo = new MockRule("rule-2", Result.VALID);
            rule.setRules(List.of(ruleOne, ruleTwo));

            final var evaluationResult = rule.evaluate(resolver);

            final var interimResult = buildInterimResult(ruleOne, ruleTwo);
            assertThat(evaluationResult.snapshot()).isEqualTo(interimResult);

            final var result = evaluationResult.status().join();

            assertThat(result).isEqualTo(Result.VALID);
            final var finalResult = ruleResultBuilder.with(r -> {
                r.result = Result.VALID;
                r.rules = Set.of(
                        buildRuleResult(ruleOne, controlResult, true),
                        buildRuleResult(ruleTwo, Result.VALID)
                );
            }).build();
            assertThat(evaluationResult.snapshot()).isEqualTo(finalResult);
        }

        @RepeatedTest(1000) // repeat this test many times to increase trust on thread synchronization
        void validAndNotValid() {
            final var ruleOne = new MockRule("rule-1", Result.VALID);
            final var ruleTwo = new MockRule("rule-2", Result.INVALID);
            rule.setRules(List.of(ruleOne, ruleTwo));

            final var evaluationResult = rule.evaluate(resolver);

            final var interimResult = buildInterimResult(ruleOne, ruleTwo);
            assertThat(evaluationResult.snapshot()).isEqualTo(interimResult);

            final var result = evaluationResult.status().join();

            assertThat(result).isEqualTo(Result.INVALID);
            assertThat(evaluationResult.snapshot())
                    .extracting(RuleResult::getType, RuleResult::getResult)
                    .containsExactly("AND", Result.INVALID);
            assertThat(evaluationResult.snapshot())
                    .isInstanceOfSatisfying(MultiaryBooleanRuleResult.class,
                            ruleResult -> assertThat(ruleResult.getRules())
                                    .extracting(RuleResult::getType, RuleResult::getResult)
                                    .contains(Tuple.tuple("MOCK_RULE", Result.INVALID))); // at least one of the subrules should evaluate to 'INVALID'
        }

        @Test
        @DisplayName("evaluate as valid if both subrules are invalid and marked as ignored")
        void notValidAndNotValidButBothIgnored() {
            final var ruleOne = new MockRule("rule-1", Result.INVALID);
            ruleOne.setIgnore(true);
            final var ruleTwo = new MockRule("rule-2", Result.INVALID);
            ruleTwo.setIgnore(true);
            rule.setRules(List.of(ruleOne, ruleTwo));

            final var evaluationResult = rule.evaluate(resolver);

            final var interimResult = buildInterimResult(ruleOne, ruleTwo);
            assertThat(evaluationResult.snapshot()).isEqualTo(interimResult);

            final var result = evaluationResult.status().join();

            assertThat(result).isEqualTo(Result.VALID);
            final var finalResult = ruleResultBuilder.with(r -> {
                r.result = Result.VALID;
                r.rules = Set.of(
                        buildRuleResult(ruleOne, Result.INVALID, true),
                        buildRuleResult(ruleTwo, Result.INVALID, true)
                );
            }).build();
            assertThat(evaluationResult.snapshot()).isEqualTo(finalResult);
        }

        @Test
        @DisplayName("evaluate and return description in result")
        void descriptionInResult() {
            final var ruleOne = new MockRule("rule-1", Result.VALID);
            final var ruleTwo = new MockRule("rule-2", Result.VALID);
            rule.setRules(List.of(ruleOne, ruleTwo));
            rule.setDescription("ANDing of two rules");

            ruleResultBuilder = ruleResultBuilder.with(r -> r.description = "ANDing of two rules");

            final var evaluationResult = rule.evaluate(resolver);

            final var interimResult = buildInterimResult(ruleOne, ruleTwo);
            assertThat(evaluationResult.snapshot()).isEqualTo(interimResult);

            final var result = evaluationResult.status().join();

            assertThat(result).isEqualTo(Result.VALID);
            final var finalResult = ruleResultBuilder.with(r -> {
                r.result = Result.VALID;
                r.rules = Set.of(
                        buildRuleResult(ruleOne, Result.VALID),
                        buildRuleResult(ruleTwo, Result.VALID)
                );
            }).build();
            assertThat(evaluationResult.snapshot()).isEqualTo(finalResult);
        }

        @Test
        @DisplayName("evaluate action on completion")
        void actionOnCompletion() {
            final var ruleOne = new MockRule("rule-1", Result.VALID);
            final var ruleTwo = new MockRule("rule-2", Result.VALID);
            rule.setRules(List.of(ruleOne, ruleTwo));

            var integerAtomicReference = new AtomicInteger(0);
            var action = new Action().setOnCompletion((result, throwable, ruleResult) -> integerAtomicReference.getAndIncrement());
            rule.setAction(action);

            final var result = rule.evaluate(resolver).status().join();
            assertThat(result).isEqualTo(Result.VALID);

            assertThat(integerAtomicReference.get()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("with startup edge cases should")
    class StartupEdgeCases {

        @Test
        @DisplayName("evaluate an empty AND as VALID (identity of conjunction)")
        void emptyAndIsValid() {
            rule.setRules(List.of());

            final var evaluationResult = rule.evaluate(resolver);

            assertThat(evaluationResult.snapshot().getResult()).isEqualTo(Result.MAYBE);

            final var result = evaluationResult.status().join();

            assertThat(result).isEqualTo(Result.VALID);
            assertThat(evaluationResult.snapshot()).isEqualTo(buildRuleResult(ruleResultBuilder, Result.VALID));
        }

        @Test
        @DisplayName("return the same future and not restart evaluation on repeated status() calls")
        void repeatedStatusReturnsSameFuture() {
            final var ruleOne = new MockRule("rule-1", Result.VALID);
            final var ruleTwo = new MockRule("rule-2", Result.VALID);
            rule.setRules(List.of(ruleOne, ruleTwo));

            final var evaluationResult = rule.evaluate(resolver);
            final var first = evaluationResult.status();
            final var second = evaluationResult.status();

            assertThat(first).isSameAs(second);
            assertThat(first.join()).isEqualTo(Result.VALID);
        }

        @Test
        @DisplayName("fire the completion action exactly once even across repeated status() calls")
        void actionFiresExactlyOnceAcrossRepeatedStatus() {
            final var ruleOne = new MockRule("rule-1", Result.VALID);
            final var ruleTwo = new MockRule("rule-2", Result.VALID);
            rule.setRules(List.of(ruleOne, ruleTwo));

            var counter = new AtomicInteger(0);
            rule.setAction(new Action().setOnCompletion((result, throwable, ruleResult) -> counter.getAndIncrement()));

            final var evaluationResult = rule.evaluate(resolver);
            evaluationResult.status().join();
            evaluationResult.status().join();
            evaluationResult.status().join();

            assertThat(counter.get()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("with delayed rules should")
    class WithDelayedRulesTest {

        @Test
        @DisplayName("evaluate as valid if both subrules are valid and should be bound by slowest rule")
        void validAndValid() {
            final var ruleOne = new DelayedRule("rule-1", Result.VALID, 1000);
            final var ruleTwo = new DelayedRule("rule-2", Result.VALID, 500);
            rule.setRules(List.of(ruleOne, ruleTwo));

            final var evaluationResult = rule.evaluate(resolver);

            final var interimResult = buildInterimResult(ruleOne, ruleTwo);
            assertThat(evaluationResult.snapshot()).isEqualTo(interimResult);

            assertThat(evaluationResult.status())
                    .succeedsWithin(Duration.ofMillis(2000)) // delay is 1000ms; extra slack absorbs scheduling/GC jitter on slow/busy machines
                    .isEqualTo(Result.VALID);
        }

        @Test
        @DisplayName("evaluate as invalid if one subrule is not valid and should be bound by failing rule")
        void validAndNotValid() {
            final var ruleOne = new DelayedRule("rule-1", Result.VALID, 1000);
            final var ruleTwo = new DelayedRule("rule-2", Result.INVALID, 500);
            rule.setRules(List.of(ruleOne, ruleTwo));

            final var evaluationResult = rule.evaluate(resolver);

            final var interimResult = buildInterimResult(ruleOne, ruleTwo);
            assertThat(evaluationResult.snapshot()).isEqualTo(interimResult);

            assertThat(evaluationResult.status())
                    .succeedsWithin(Duration.ofMillis(1500)) // delay is 500ms; extra slack absorbs scheduling/GC jitter on slow/busy machines
                    .isEqualTo(Result.INVALID);
        }

        @Test
        @DisplayName("evaluate as invalid if one subrule is not valid (and ignored) and should be bound by slowest rule, since failing rule is ignored")
        void validAndNotValidButIgnored() {
            final var ruleOne = new DelayedRule("rule-1", Result.VALID, 1000);
            final var ruleTwo = new DelayedRule("rule-2", Result.INVALID, 500);
            ruleTwo.setIgnore(true);
            rule.setRules(List.of(ruleOne, ruleTwo));

            final var evaluationResult = rule.evaluate(resolver);

            final var interimResult = buildInterimResult(ruleOne, ruleTwo);
            assertThat(evaluationResult.snapshot()).isEqualTo(interimResult);

            assertThat(evaluationResult.status())
                    .succeedsWithin(Duration.ofMillis(2000)) // delay is 1000ms; extra slack absorbs scheduling/GC jitter on slow/busy machines
                    .isEqualTo(Result.VALID);
        }
    }

    @Nested
    @DisplayName("with exception rules should")
    class WithExceptionRuleTest {

        @Test
        @DisplayName("evaluate as failed if one of the subrules throws an exception")
        void validAndException() {
            final var ruleOne = new MockRule("rule-1", Result.VALID);
            final var ruleTwo = new ExceptionRule("rule-2");
            rule.setRules(List.of(ruleOne, ruleTwo));

            final var evaluationResult = rule.evaluate(resolver);

            final var interimResult = buildInterimResult(ruleOne, ruleTwo);
            assertThat(evaluationResult.snapshot()).isEqualTo(interimResult);

            assertThat(evaluationResult.status())
                    .failsWithin(Duration.ofMillis(200));

            assertThat(evaluationResult.snapshot())
                    .extracting(RuleResult::getType, RuleResult::getResult)
                    .containsExactly("AND", Result.FAILED);
            assertThat(evaluationResult.snapshot())
                    .isInstanceOfSatisfying(MultiaryBooleanRuleResult.class,
                            ruleResult -> assertThat(ruleResult.getRules())
                                    .extracting(RuleResult::getType, RuleResult::getResult)
                                    .contains(Tuple.tuple("EXCEPTION_RULE", Result.FAILED)));
        }

        @Test
        @DisplayName("evaluate as valid if one of the subrules throws an exception but marked as ignored")
        void validAndExceptionButIgnored() {
            final var ruleOne = new MockRule("rule-1", Result.VALID);
            final var ruleTwo = new ExceptionRule("rule-2");
            ruleTwo.setIgnore(true);
            rule.setRules(List.of(ruleOne, ruleTwo));

            final var evaluationResult = rule.evaluate(resolver);

            final var interimResult = buildInterimResult(ruleOne, ruleTwo);
            assertThat(evaluationResult.snapshot()).isEqualTo(interimResult);

            final var result = evaluationResult.status().join();

            assertThat(result).isEqualTo(Result.VALID);
            final var finalResult = ruleResultBuilder.with(r -> {
                r.result = Result.VALID;
                r.rules = Set.of(
                        buildRuleResult(ruleOne, Result.VALID),
                        buildRuleResult(ruleTwo, Result.FAILED, true)
                );
            }).build();
            assertThat(evaluationResult.snapshot()).isEqualTo(finalResult);
        }

        @Test
        @DisplayName("evaluate action on completion with exception")
        void actionOnCompletionWithException() {
            final var ruleOne = new MockRule("rule-1", Result.VALID);
            final var ruleTwo = new ExceptionRule("rule-2");
            rule.setRules(List.of(ruleOne, ruleTwo));

            var throwableAtomicReference = new AtomicReference<Throwable>();
            var action = new Action().setOnCompletion((result, throwable, ruleResult) -> throwableAtomicReference.set(throwable));
            rule.setAction(action);

            final var evaluationResult = rule.evaluate(resolver);
            assertThat(evaluationResult.status())
                    .failsWithin(Duration.ofMillis(200));

            assertThat(throwableAtomicReference.get()).hasMessageContaining("Intentionally failing this rule with an exception");
        }
    }
}
