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
import com.adobe.abp.regola.mockrules.DelayedRule;
import com.adobe.abp.regola.mockrules.ExceptionRule;
import com.adobe.abp.regola.mockrules.MockRule;
import com.adobe.abp.regola.results.Result;
import com.adobe.abp.regola.results.RuleResult;
import com.adobe.abp.regola.results.UnaryBooleanRuleResult;
import com.adobe.abp.regola.results.ValuesRuleResult;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("Testing NotRule")
class NotRuleTest {

    private NotRule rule;

    private final FactsResolver resolver = mock(FactsResolver.class);

    private UnaryBooleanRuleResult.RuleResultBuilder ruleResultBuilder;

    @BeforeEach
    void setup() {
        rule = new NotRule();

        ruleResultBuilder = UnaryBooleanRuleResult.builder().with(r -> r.type = RuleType.NOT.getName());
    }

    @Test
    @DisplayName("should have the expected NOT type")
    void ruleHasExpectedType() {
        assertThat(rule.getType()).isEqualTo("NOT");
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

    @Nested
    @DisplayName("with mock rule should")
    class WithMockRuleTest {

        @RepeatedTest(1000) // repeat this test many times to increase trust on thread synchronization
        @DisplayName("evaluate as valid if subrule is not valid")
        void factIsValid() {
            final var subrule = new MockRule("subrule", Result.INVALID);
            rule.setRule(subrule);

            final var evaluationResult = rule.evaluate(resolver);

            final var interimResult = ruleResultBuilder.with(r -> r.result = Result.MAYBE).build();
            assertThat(evaluationResult.snapshot()).isEqualTo(interimResult);

            final var result = evaluationResult.status().join();

            assertThat(result).isEqualTo(Result.VALID);
            final var finalResult = ruleResultBuilder.with(r -> {
                r.result = Result.VALID;
                r.rule = buildRuleResult(subrule, Result.INVALID);
            }).build();
            assertThat(evaluationResult.snapshot()).isEqualTo(finalResult);
        }

        @Test
        @DisplayName("evaluate as not valid if subrule is valid")
        void factIsNotValid() {
            final var subrule = new MockRule("subrule", Result.VALID);
            rule.setRule(subrule);

            final var evaluationResult = rule.evaluate(resolver);

            final var interimResult = ruleResultBuilder.with(r -> r.result = Result.MAYBE).build();
            assertThat(evaluationResult.snapshot()).isEqualTo(interimResult);

            final var result = evaluationResult.status().join();

            assertThat(result).isEqualTo(Result.INVALID);
            final var finalResult = ruleResultBuilder.with(r -> {
                r.result = Result.INVALID;
                r.rule = buildRuleResult(subrule, Result.VALID);
            }).build();
            assertThat(evaluationResult.snapshot()).isEqualTo(finalResult);
        }

        @ParameterizedTest
        @EnumSource(value = Result.class)
        @DisplayName("evaluate always as valid if subrule is marked as ignored")
        void factIsNotValidButIgnored(Result controlResult) {
            final var subrule = new MockRule("subrule", controlResult);
            subrule.setIgnore(true);
            rule.setRule(subrule);

            final var evaluationResult = rule.evaluate(resolver);

            final var interimResult = ruleResultBuilder.with(r -> r.result = Result.MAYBE).build();
            assertThat(evaluationResult.snapshot()).isEqualTo(interimResult);

            final var result = evaluationResult.status().join();

            assertThat(result).isEqualTo(Result.VALID);
            final var finalResult = ruleResultBuilder.with(r -> {
                r.result = Result.VALID;
                r.rule = buildRuleResult(subrule, controlResult, true);
            }).build();
            assertThat(evaluationResult.snapshot()).isEqualTo(finalResult);
        }

        @ParameterizedTest
        @EnumSource(value = Result.class,
                names = {"VALID", "INVALID"},
                mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("evaluate as subresult if subrule is not VALID nor INVALID")
        void factIsEvaluatedAsSubresult(Result subResult) {
            final var subrule = new MockRule("subrule", subResult);
            rule.setRule(subrule);

            final var evaluationResult = rule.evaluate(resolver);

            final var interimResult = ruleResultBuilder.with(r -> r.result = Result.MAYBE).build();
            assertThat(evaluationResult.snapshot()).isEqualTo(interimResult);

            final var result = evaluationResult.status().join();

            assertThat(result).isEqualTo(subResult);
            final var finalResult = ruleResultBuilder.with(r -> {
                r.result = subResult;
                r.rule = buildRuleResult(subrule, subResult);
            }).build();
            assertThat(evaluationResult.snapshot()).isEqualTo(finalResult);
        }

        @Test
        @DisplayName("evaluate and return description in result")
        void descriptionInResult() {
            final var subrule = new MockRule("subrule", Result.INVALID);
            rule.setRule(subrule);
            rule.setDescription("NOTing rule");

            ruleResultBuilder = ruleResultBuilder.with(r -> r.description = "NOTing rule");

            final var evaluationResult = rule.evaluate(resolver);

            final var interimResult = ruleResultBuilder.with(r -> r.result = Result.MAYBE).build();
            assertThat(evaluationResult.snapshot()).isEqualTo(interimResult);

            final var result = evaluationResult.status().join();

            assertThat(result).isEqualTo(Result.VALID);
            final var finalResult = ruleResultBuilder.with(r -> {
                r.result = Result.VALID;
                r.rule = buildRuleResult(subrule, Result.INVALID);
            }).build();
            assertThat(evaluationResult.snapshot()).isEqualTo(finalResult);
        }

        @Test
        @DisplayName("evaluate action on completion")
        void actionOnCompletion() {
            final var subrule = new MockRule("subrule", Result.INVALID);
            rule.setRule(subrule);

            var integerAtomicReference = new AtomicInteger(0);
            var action = new Action().setOnCompletion((result, throwable, ruleResult) -> integerAtomicReference.getAndIncrement());
            rule.setAction(action);

            final var result = rule.evaluate(resolver).status().join();
            assertThat(result).isEqualTo(Result.VALID);

            assertThat(integerAtomicReference.get()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("with delayed rule should")
    class WithDelayedRuleTest {

        @Test
        @DisplayName("evaluate as valid if subrule is not valid and should be bound by time its time of execution")
        void factIsValid() {
            final var subrule = new DelayedRule("subrule", Result.INVALID, 1000);
            rule.setRule(subrule);

            final var evaluationResult = rule.evaluate(resolver);

            final var interimResult = ruleResultBuilder.with(r -> r.result = Result.MAYBE).build();
            assertThat(evaluationResult.snapshot()).isEqualTo(interimResult);

            assertThat(evaluationResult.status())
                    .succeedsWithin(Duration.ofMillis(1100))
                    .isEqualTo(Result.VALID);
        }
    }

    @Nested
    @DisplayName("with exception rule should")
    class WithExceptionRuleTest {

        @Test
        @DisplayName("evaluate as failed if subrule throws an exception")
        void validAndException() {
            final var subrule = new ExceptionRule("rule-2");
            rule.setRule(subrule);

            final var evaluationResult = rule.evaluate(resolver);

            final var interimResult = ruleResultBuilder.with(r -> r.result = Result.MAYBE).build();
            assertThat(evaluationResult.snapshot()).isEqualTo(interimResult);

            assertThat(evaluationResult.status())
                    .failsWithin(Duration.ofMillis(50));

            assertThat(evaluationResult.snapshot())
                    .extracting(RuleResult::getType, RuleResult::getResult)
                    .containsExactly("NOT", Result.FAILED);
            assertThat(evaluationResult.snapshot())
                    .isInstanceOfSatisfying(UnaryBooleanRuleResult.class,
                            ruleResult -> assertThat(ruleResult.getRule())
                                    .extracting(RuleResult::getType, RuleResult::getResult)
                                    .containsExactly("EXCEPTION_RULE", Result.FAILED));
        }

        @Test
        @DisplayName("evaluate as valid if subrule throws an exception, but marked as ignored")
        void validAndExceptionButIgnored() {
            final var subrule = new ExceptionRule("rule-2");
            subrule.setIgnore(true);
            rule.setRule(subrule);

            final var evaluationResult = rule.evaluate(resolver);

            final var interimResult = ruleResultBuilder.with(r -> r.result = Result.MAYBE).build();
            assertThat(evaluationResult.snapshot()).isEqualTo(interimResult);

            final var result = evaluationResult.status().join();

            assertThat(result).isEqualTo(Result.VALID);
            final var finalResult = ruleResultBuilder.with(r -> {
                r.result = Result.VALID;
                r.rule = buildRuleResult(subrule, Result.FAILED, true);
            }).build();
            assertThat(evaluationResult.snapshot()).isEqualTo(finalResult);
        }

        @Test
        @DisplayName("evaluate action on completion with exception")
        void actionOnCompletionWithException() {
            final var subrule = new ExceptionRule("rule-2");
            rule.setRule(subrule);

            var throwableAtomicReference = new AtomicReference<Throwable>();
            var action = new Action().setOnCompletion((result, throwable, ruleResult) -> throwableAtomicReference.set(throwable));
            rule.setAction(action);

            final var evaluationResult = rule.evaluate(resolver);
            assertThat(evaluationResult.status())
                    .failsWithin(Duration.ofMillis(50));

            assertThat(throwableAtomicReference.get()).hasMessageContaining("Intentionally failing this rule with an exception");
        }
    }
}