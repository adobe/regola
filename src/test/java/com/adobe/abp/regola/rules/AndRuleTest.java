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
import com.adobe.abp.regola.results.MultiaryBooleanRuleResult;
import com.adobe.abp.regola.results.Result;
import com.adobe.abp.regola.results.RuleResult;
import com.adobe.abp.regola.results.ValuesRuleResult;
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

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("Testing AndRule")
class AndRuleTest {

    private AndRule rule;

    private MultiaryBooleanRuleResult.RuleResultBuilder ruleResultBuilder;

    private final FactsResolver resolver = mock(FactsResolver.class);

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

            final var interimResult = buildRuleResult(ruleResultBuilder, Result.MAYBE);
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

            final var interimResult = buildRuleResult(ruleResultBuilder, Result.MAYBE);
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

            final var interimResult = buildRuleResult(ruleResultBuilder, Result.MAYBE);
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

            final var interimResult = buildRuleResult(ruleResultBuilder, Result.MAYBE);
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

            final var interimResult = buildRuleResult(ruleResultBuilder, Result.MAYBE);
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

            final var interimResult = buildRuleResult(ruleResultBuilder, Result.MAYBE);
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

            final var interimResult = buildRuleResult(ruleResultBuilder, Result.MAYBE);
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

            final var interimResult = buildRuleResult(ruleResultBuilder, Result.MAYBE);
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
    @DisplayName("with delayed rules should")
    class WithDelayedRulesTest {

        @Test
        @DisplayName("evaluate as valid if both subrules are valid and should be bound by slowest rule")
        void validAndValid() {
            final var ruleOne = new DelayedRule("rule-1", Result.VALID, 1000);
            final var ruleTwo = new DelayedRule("rule-2", Result.VALID, 500);
            rule.setRules(List.of(ruleOne, ruleTwo));

            final var evaluationResult = rule.evaluate(resolver);

            final var interimResult = buildRuleResult(ruleResultBuilder, Result.MAYBE);
            assertThat(evaluationResult.snapshot()).isEqualTo(interimResult);

            assertThat(evaluationResult.status())
                    .succeedsWithin(Duration.ofMillis(1100))
                    .isEqualTo(Result.VALID);
        }

        @Test
        @DisplayName("evaluate as invalid if one subrule is not valid and should be bound by failing rule")
        void validAndNotValid() {
            final var ruleOne = new DelayedRule("rule-1", Result.VALID, 1000);
            final var ruleTwo = new DelayedRule("rule-2", Result.INVALID, 500);
            rule.setRules(List.of(ruleOne, ruleTwo));

            final var evaluationResult = rule.evaluate(resolver);

            final var interimResult = buildRuleResult(ruleResultBuilder, Result.MAYBE);
            assertThat(evaluationResult.snapshot()).isEqualTo(interimResult);

            assertThat(evaluationResult.status())
                    .succeedsWithin(Duration.ofMillis(600))
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

            final var interimResult = buildRuleResult(ruleResultBuilder, Result.MAYBE);
            assertThat(evaluationResult.snapshot()).isEqualTo(interimResult);

            assertThat(evaluationResult.status())
                    .succeedsWithin(Duration.ofMillis(1100))
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

            final var interimResult = buildRuleResult(ruleResultBuilder, Result.MAYBE);
            assertThat(evaluationResult.snapshot()).isEqualTo(interimResult);

            assertThat(evaluationResult.status())
                    .failsWithin(Duration.ofMillis(50));

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

            final var interimResult = buildRuleResult(ruleResultBuilder, Result.MAYBE);
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
                    .failsWithin(Duration.ofMillis(50));

            assertThat(throwableAtomicReference.get()).hasMessageContaining("Intentionally failing this rule with an exception");
        }
    }
}
