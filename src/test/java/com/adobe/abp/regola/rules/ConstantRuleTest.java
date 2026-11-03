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
import com.adobe.abp.regola.facts.FactsResolver;
import com.adobe.abp.regola.results.Result;
import com.adobe.abp.regola.results.RuleResult;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("Testing ConstantRule should")
class ConstantRuleTest {

    private final FactsResolver resolver = mock(FactsResolver.class);

    @Test
    @DisplayName("have the expected CONSTANT type")
    void ruleHasExpectedType() {
        ConstantRule rule = new ConstantRule(Result.VALID);

        assertThat(rule.getType()).isEqualTo("CONSTANT");
    }

    @ParameterizedTest
    @EnumSource(Result.class)
    @DisplayName("evaluate to the result specified")
    void evaluateToGivenResult(Result result) {
        ConstantRule rule = new ConstantRule(result);
        rule.setDescription("This rule will always evaluate to the same result");

        final var ruleResultBuilder = RuleResult.baseBuilder().with(r -> {
            r.type = RuleType.CONSTANT.getName();
            r.description = "This rule will always evaluate to the same result";
        });

        RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, result);
    }

    @Test
    @DisplayName("evaluate to VALID by default")
    void evaluateToGivenResult() {
        ConstantRule rule = new ConstantRule();

        final var ruleResultBuilder = RuleResult.baseBuilder().with(r -> r.type = RuleType.CONSTANT.getName());

        RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID);
    }

    @Test
    @DisplayName("evaluate action on completion")
    void actionOnCompletion() {
        ConstantRule rule = new ConstantRule(Result.VALID);

        var integerAtomicReference = new AtomicInteger(0);
        var action = new Action().setOnCompletion((result, throwable, ruleResult) -> integerAtomicReference.getAndIncrement());
        rule.setAction(action);

        final var ruleResultBuilder = RuleResult.baseBuilder().with(r -> r.type = RuleType.CONSTANT.getName());
        RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID);

        assertThat(integerAtomicReference.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("run the completion action once across repeated status() calls")
    void actionOnCompletionRunsOnce() {
        final var completions = new AtomicInteger();
        final var rule = new ConstantRule(Result.VALID);
        rule.setAction(new Action()
                .setOnCompletion((result, throwable, ruleResult) -> completions.incrementAndGet()));

        final var evaluationResult = rule.evaluate(resolver);
        evaluationResult.status();
        evaluationResult.status();

        assertThat(completions)
                .hasValue(1);
    }

    @Test
    @DisplayName("report that it is ignored")
    void reportsIgnored() {
        final var rule = new ConstantRule(Result.VALID);
        rule.setIgnore(true);

        final var evaluationResult = rule.evaluate(resolver);
        evaluationResult.status();

        assertThat(evaluationResult.snapshot().isIgnored())
                .isTrue();
    }

    @Nested
    @DisplayName("when cancelled before being started")
    class CancellationTest {

        private final AtomicInteger completions = new AtomicInteger();
        private final AtomicInteger cancellations = new AtomicInteger();
        private final ConstantRule rule = new ConstantRule(Result.VALID);

        @BeforeEach
        void setup() {
            rule.setAction(new Action()
                    .setOnCompletion((result, throwable, ruleResult) ->
                            completions.incrementAndGet())
                    .setOnCancellation(ruleResult -> cancellations.incrementAndGet()));
        }

        @Test
        @DisplayName("report MAYBE flagged as skipped")
        void reportsSkipped() {
            final var evaluationResult = rule.evaluate(resolver);
            evaluationResult.cancel();

            assertThat(evaluationResult.snapshot())
                    .extracting(RuleResult::getResult, RuleResult::isSkipped)
                    .containsExactly(Result.MAYBE, true);
        }

        @Test
        @DisplayName("run the cancellation action")
        void runsCancellationAction() {
            rule.evaluate(resolver).cancel();

            assertThat(cancellations)
                    .hasValue(1);
        }

        @Test
        @DisplayName("cancel its status")
        void cancelsStatus() {
            final var evaluationResult = rule.evaluate(resolver);
            evaluationResult.cancel();

            assertThat(evaluationResult.status())
                    .isCancelled();
        }

        @Test
        @DisplayName("not run the completion action")
        void doesNotRunCompletionAction() {
            final var evaluationResult = rule.evaluate(resolver);
            evaluationResult.cancel();
            evaluationResult.status();

            assertThat(completions)
                    .hasValue(0);
        }
    }
}
