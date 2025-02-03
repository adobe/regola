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
import com.adobe.abp.regola.mockrules.DelayedExceptionRule;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("Testing OrRule")
class OrRuleTest {

    private OrRule rule;

    private MultiaryBooleanRuleResult.RuleResultBuilder ruleResultBuilder;

    private final FactsResolver resolver = mock(FactsResolver.class);

    @BeforeEach
    void setup() {
        rule = new OrRule();

        ruleResultBuilder = MultiaryBooleanRuleResult.builder().with(r -> r.type = RuleType.OR.getName());
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

    @Test
    @DisplayName("should have the expected OR type")
    void ruleHasExpectedType() {
        assertThat(rule.getType()).isEqualTo("OR");
    }

    @Nested
    @DisplayName("with mock rules should")
    class WithMockRulesTest {

        @RepeatedTest(1000) // repeat this test many times to increase trust on thread synchronization
        @DisplayName("evaluate as valid if first subrule is valid")
        void validOrNotValid() {
            final var ruleOne = new MockRule("rule-1", Result.VALID);
            final var ruleTwo = new MockRule("rule-2", Result.INVALID);
            rule.setRules(List.of(ruleOne, ruleTwo));

            final var evaluationResult = rule.evaluate(resolver);

            final var interimResult = buildRuleResult(ruleResultBuilder, Result.MAYBE);
            assertThat(evaluationResult.snapshot()).isEqualTo(interimResult);

            final var result = evaluationResult.status().join();

            assertThat(result).isEqualTo(Result.VALID);
            assertThat(evaluationResult.snapshot())
                    .extracting(RuleResult::getType, RuleResult::getResult)
                    .containsExactly("OR", Result.VALID);
            assertThat(evaluationResult.snapshot())
                    .isInstanceOfSatisfying(MultiaryBooleanRuleResult.class,
                            ruleResult -> assertThat(ruleResult.getRules())
                                    .extracting(RuleResult::getType, RuleResult::getResult)
                                    .contains(Tuple.tuple("MOCK_RULE", Result.VALID))); // at least one of the subrules should evaluate to 'controlResult'
        }

        @Test
        @DisplayName("evaluate as valid if first subrule is not valid, but second one is")
        void notValidOrValid() {
            final var ruleOne = new MockRule("rule-1", Result.INVALID);
            final var ruleTwo = new MockRule("rule-2", Result.VALID);
            rule.setRules(List.of(ruleOne, ruleTwo));

            final var evaluationResult = rule.evaluate(resolver);

            final var interimResult = buildRuleResult(ruleResultBuilder, Result.MAYBE);
            assertThat(evaluationResult.snapshot()).isEqualTo(interimResult);

            final var result = evaluationResult.status().join();

            assertThat(result).isEqualTo(Result.VALID);
            assertThat(evaluationResult.snapshot())
                    .extracting(RuleResult::getType, RuleResult::getResult)
                    .containsExactly("OR", Result.VALID);
            assertThat(evaluationResult.snapshot())
                    .isInstanceOfSatisfying(MultiaryBooleanRuleResult.class,
                            ruleResult -> assertThat(ruleResult.getRules())
                                    .extracting(RuleResult::getType, RuleResult::getResult)
                                    .contains(Tuple.tuple("MOCK_RULE", Result.VALID))); // at least one of the subrules should evaluate to 'VALID'
        }

        @Test
        @DisplayName("evaluate as valid if first subrule is not valid but ignored, but second one is valid")
        void notValidButIgnoredOrValid() {
            final var ruleOne = new MockRule("rule-1", Result.INVALID);
            ruleOne.setIgnore(true); // Note: this rule is ignored irrespective of this flag because of how OR works
            final var ruleTwo = new MockRule("rule-2", Result.VALID);
            rule.setRules(List.of(ruleOne, ruleTwo));

            final var evaluationResult = rule.evaluate(resolver);

            final var interimResult = buildRuleResult(ruleResultBuilder, Result.MAYBE);
            assertThat(evaluationResult.snapshot()).isEqualTo(interimResult);

            final var result = evaluationResult.status().join();

            assertThat(result).isEqualTo(Result.VALID);
            assertThat(evaluationResult.snapshot())
                    .extracting(RuleResult::getType, RuleResult::getResult)
                    .containsExactly("OR", Result.VALID);
            assertThat(evaluationResult.snapshot())
                    .isInstanceOfSatisfying(MultiaryBooleanRuleResult.class,
                            ruleResult -> assertThat(ruleResult.getRules())
                                    .extracting(RuleResult::getType, RuleResult::getResult)
                                    .contains(Tuple.tuple("MOCK_RULE", Result.VALID))); // at least one of the subrules should evaluate to 'VALID'
        }

        @ParameterizedTest
        @EnumSource(value = Result.class,
                names = {"VALID"},
                mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("evaluate as valid if first subrule is not valid, but second one is valid but ignored")
        void notValidOrValidButIgnored(Result controlResult) {
            final var ruleOne = new MockRule("rule-1", controlResult);
            final var ruleTwo = new MockRule("rule-2", Result.VALID);
            ruleTwo.setIgnore(true);
            rule.setRules(List.of(ruleOne, ruleTwo));

            final var evaluationResult = rule.evaluate(resolver);

            final var interimResult = buildRuleResult(ruleResultBuilder, Result.MAYBE);
            assertThat(evaluationResult.snapshot()).isEqualTo(interimResult);

            final var result = evaluationResult.status().join();

            assertThat(result).isEqualTo(controlResult);
            final var finalResult = ruleResultBuilder.with(r -> {
                r.result = controlResult;
                r.rules = Set.of(
                        buildRuleResult(ruleOne, controlResult),
                        buildRuleResult(ruleTwo, Result.VALID, true) // Ignored
                );
            }).build();
            assertThat(evaluationResult.snapshot()).isEqualTo(finalResult);
        }

        @Test
        @DisplayName("evaluate as invalid if both subrules are not valid (invalid, invalid)")
        void noRulesAreValid() {
            final var ruleOne = new MockRule("rule-1", Result.INVALID);
            final var ruleTwo = new MockRule("rule-2", Result.INVALID);
            rule.setRules(List.of(ruleOne, ruleTwo));

            final var evaluationResult = rule.evaluate(resolver);

            final var interimResult = buildRuleResult(ruleResultBuilder, Result.MAYBE);
            assertThat(evaluationResult.snapshot()).isEqualTo(interimResult);

            final var result = evaluationResult.status().join();

            assertThat(result).isEqualTo(Result.INVALID);
            assertThat(evaluationResult.snapshot())
                    .extracting(RuleResult::getType, RuleResult::getResult)
                    .containsExactly("OR", Result.INVALID);
            assertThat(evaluationResult.snapshot())
                    .isInstanceOfSatisfying(MultiaryBooleanRuleResult.class,
                            ruleResult -> assertThat(ruleResult.getRules())
                                    .extracting(RuleResult::getType, RuleResult::getResult)
                                    .contains(Tuple.tuple("MOCK_RULE", Result.INVALID))); // at least one of the subrules should evaluate to 'INVALID'
        }

        @Test
        @DisplayName("evaluate as valid if both subrules are not valid (invalid, invalid) and both are ignored")
        void noRulesAreValidButBothAreIgnored() {
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

        @RepeatedTest(1000)
        @DisplayName("evaluate as invalid if both subrules are not valid (invalid, maybe)")
        void noRulesAreValidAgainstMaybe() {
            final var ruleOne = new MockRule("rule-1", Result.INVALID);
            final var ruleTwo = new MockRule("rule-2", Result.MAYBE);
            rule.setRules(List.of(ruleOne, ruleTwo));

            final var evaluationResult = rule.evaluate(resolver);

            final var interimResult = buildRuleResult(ruleResultBuilder, Result.MAYBE);
            assertThat(evaluationResult.snapshot()).isEqualTo(interimResult);

            final var result = evaluationResult.status().join();

            assertThat(result).isEqualTo(Result.INVALID);
            assertThat(evaluationResult.snapshot())
                    .extracting(RuleResult::getType, RuleResult::getResult)
                    .containsExactly("OR", Result.INVALID);
            assertThat(evaluationResult.snapshot())
                    .isInstanceOfSatisfying(MultiaryBooleanRuleResult.class,
                            ruleResult -> assertThat(ruleResult.getRules())
                                    .extracting(RuleResult::getType, RuleResult::getResult)
                                    .containsExactlyInAnyOrder(Tuple.tuple("MOCK_RULE", Result.MAYBE),
                                            Tuple.tuple("MOCK_RULE", Result.INVALID)));
        }

        @ParameterizedTest
        @EnumSource(value = Result.class,
                names = {"VALID"},
                mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("evaluate as VALID if subrules are (valid, not valid)")
        void evaluateAsValidIsOneOfTheSubrulesIsSuch(Result controlResult) {
            final var ruleOne = new MockRule("rule-1", Result.VALID);
            final var ruleTwo = new MockRule("rule-2", controlResult);
            rule.setRules(List.of(ruleOne, ruleTwo));

            final var evaluationResult = rule.evaluate(resolver);

            final var interimResult = buildRuleResult(ruleResultBuilder, Result.MAYBE);
            assertThat(evaluationResult.snapshot()).isEqualTo(interimResult);

            final var result = evaluationResult.status().join();

            assertThat(result).isEqualTo(Result.VALID);
            assertThat(evaluationResult.snapshot())
                    .extracting(RuleResult::getType, RuleResult::getResult)
                    .containsExactly("OR", Result.VALID);
            assertThat(evaluationResult.snapshot())
                    .isInstanceOfSatisfying(MultiaryBooleanRuleResult.class,
                            ruleResult -> assertThat(ruleResult.getRules())
                                    .extracting(RuleResult::getType, RuleResult::getResult)
                                    .containsExactlyInAnyOrder(Tuple.tuple("MOCK_RULE", controlResult),
                                            Tuple.tuple("MOCK_RULE", Result.VALID)));
        }

        @RepeatedTest(30)
        @DisplayName("evaluate as invalid if both subrules are not valid (invalid, not supported)")
        void evaluateNonValidRulesBasedOnPriority() {
            final var firstResult = pickRandomNonValidResult();
            final var secondResult = pickRandomNonValidResult();

            final var ruleOne = new MockRule("rule-1", firstResult);
            final var ruleTwo = new MockRule("rule-2", secondResult);
            List<Rule> rules = new ArrayList<>();
            rules.add(ruleOne);
            rules.add(ruleTwo);
            Collections.shuffle(rules);
            rule.setRules(rules);

            final var evaluationResult = rule.evaluate(resolver);

            final var interimResult = buildRuleResult(ruleResultBuilder, Result.MAYBE);
            assertThat(evaluationResult.snapshot()).isEqualTo(interimResult);

            final var result = evaluationResult.status().join();

            final var expectedResult = determineResult(firstResult, secondResult);

            assertThat(result).isEqualTo(expectedResult);
            assertThat(evaluationResult.snapshot())
                    .extracting(RuleResult::getType, RuleResult::getResult)
                    .containsExactly("OR", expectedResult);
            assertThat(evaluationResult.snapshot())
                    .isInstanceOfSatisfying(MultiaryBooleanRuleResult.class,
                            ruleResult -> assertThat(ruleResult.getRules())
                                    .extracting(RuleResult::getType, RuleResult::getResult)
                                    .containsExactlyInAnyOrder(Tuple.tuple("MOCK_RULE", firstResult),
                                            Tuple.tuple("MOCK_RULE", secondResult)));
        }

        @Test
        @DisplayName("evaluate and return description in result")
        void descriptionInResult() {
            final var ruleOne = new MockRule("rule-1", Result.VALID);
            final var ruleTwo = new MockRule("rule-2", Result.INVALID);
            rule.setRules(List.of(ruleOne, ruleTwo));
            rule.setDescription("ORing of two rules");

            ruleResultBuilder = ruleResultBuilder.with(r -> r.description = "ORing of two rules");

            final var evaluationResult = rule.evaluate(resolver);

            final var interimResult = buildRuleResult(ruleResultBuilder, Result.MAYBE);
            assertThat(evaluationResult.snapshot()).isEqualTo(interimResult);

            final var result = evaluationResult.status().join();

            assertThat(result).isEqualTo(Result.VALID);
            final var finalResult = ruleResultBuilder.with(r -> {
                r.result = Result.VALID;
                r.rules = Set.of(
                        buildRuleResult(ruleOne, Result.VALID),
                        buildRuleResult(ruleTwo, Result.INVALID)
                );
            }).build();
            assertThat(evaluationResult.snapshot()).isEqualTo(finalResult);
        }

        @Test
        @DisplayName("evaluate action on completion")
        void actionOnCompletion() {
            final var ruleOne = new MockRule("rule-1", Result.VALID);
            final var ruleTwo = new MockRule("rule-2", Result.INVALID);
            rule.setRules(List.of(ruleOne, ruleTwo));

            var integerAtomicReference = new AtomicInteger(0);
            var action = new Action().setOnCompletion((result, throwable, ruleResult) -> integerAtomicReference.getAndIncrement());
            rule.setAction(action);

            final var result = rule.evaluate(resolver).status().join();
            assertThat(result).isEqualTo(Result.VALID);

            assertThat(integerAtomicReference.get()).isEqualTo(1);
        }

        private Result pickRandomNonValidResult() {
            List<Result> nonValidResultTypes = new ArrayList<>(List.of(Result.values()));
            nonValidResultTypes.remove(Result.VALID);
            Collections.shuffle(nonValidResultTypes);
            return nonValidResultTypes.get(0);
        }

        private final List<Result> RESULT_PRIORITY = List.of(Result.FAILED, Result.OPERATION_NOT_SUPPORTED, Result.INVALID, Result.MAYBE);

        // Return the result with the higher priority
        private Result determineResult(Result intermediateResult, Result result) {
            return RESULT_PRIORITY.indexOf(result) < RESULT_PRIORITY.indexOf(intermediateResult) ?
                    result : intermediateResult;
        }
    }

    @Nested
    @DisplayName("with delayed rules should")
    class WithDelayedRulesTest {

        @Test
        @DisplayName("evaluate as valid if first subrule is valid and should be bound by fastest rule")
        void validOrNotValid() {
            final var ruleOne = new DelayedRule("rule-1", Result.VALID, 1000);
            final var ruleTwo = new DelayedRule("rule-2", Result.VALID, 500);
            rule.setRules(List.of(ruleOne, ruleTwo));

            final var evaluationResult = rule.evaluate(resolver);

            final var interimResult = buildRuleResult(ruleResultBuilder, Result.MAYBE);
            assertThat(evaluationResult.snapshot()).isEqualTo(interimResult);

            assertThat(evaluationResult.status())
                    .succeedsWithin(Duration.ofMillis(600))
                    .isEqualTo(Result.VALID);
        }

        @Test
        @DisplayName("evaluate as invalid as both subrules are invalid and should be bound by slowest rule")
        void noRulesAreValid() {
            final var ruleOne = new DelayedRule("rule-1", Result.INVALID, 1000);
            final var ruleTwo = new DelayedRule("rule-2", Result.INVALID, 500);
            rule.setRules(List.of(ruleOne, ruleTwo));

            final var evaluationResult = rule.evaluate(resolver);

            final var interimResult = buildRuleResult(ruleResultBuilder, Result.MAYBE);
            assertThat(evaluationResult.snapshot()).isEqualTo(interimResult);

            assertThat(evaluationResult.status())
                    .succeedsWithin(Duration.ofMillis(1100))
                    .isEqualTo(Result.INVALID);
        }
    }

    @Nested
    @DisplayName("with exception rules should")
    class WithExceptionRuleTest {

        @Test
        @DisplayName("evaluate as failed if one of the subrules throws an exception before the other one succeeds")
        void validAndException() {
            final var ruleOne = new DelayedRule("rule-1", Result.VALID, 500);
            final var ruleTwo = new ExceptionRule("rule-2");
            rule.setRules(List.of(ruleOne, ruleTwo));

            final var evaluationResult = rule.evaluate(resolver);

            final var interimResult = buildRuleResult(ruleResultBuilder, Result.MAYBE);
            assertThat(evaluationResult.snapshot()).isEqualTo(interimResult);

            assertThat(evaluationResult.status())
                    .failsWithin(Duration.ofMillis(50));

            assertThat(evaluationResult.snapshot())
                    .extracting(RuleResult::getType, RuleResult::getResult)
                    .containsExactly("OR", Result.FAILED);
            assertThat(evaluationResult.snapshot())
                    .isInstanceOfSatisfying(MultiaryBooleanRuleResult.class,
                            ruleResult -> assertThat(ruleResult.getRules())
                                    .extracting(RuleResult::getType, RuleResult::getResult)
                                    .contains(Tuple.tuple("EXCEPTION_RULE", Result.FAILED)));
        }

        @Test
        @DisplayName("evaluate as valid if one of the subrules throws an exception, but is marked as ignore, before the other one succeeds")
        void validAndExceptionButIgnored() {
            final var ruleOne = new DelayedRule("rule-1", Result.VALID, 500);
            final var ruleTwo = new ExceptionRule("rule-2");
            ruleTwo.setIgnore(true);
            rule.setRules(List.of(ruleOne, ruleTwo));

            final var evaluationResult = rule.evaluate(resolver);

            final var interimResult = buildRuleResult(ruleResultBuilder, Result.MAYBE);
            assertThat(evaluationResult.snapshot()).isEqualTo(interimResult);

            final var result = evaluationResult.status().join();

            assertThat(result).isEqualTo(Result.VALID);
            assertThat(evaluationResult.snapshot())
                    .extracting(RuleResult::getType, RuleResult::getResult)
                    .containsExactly("OR", Result.VALID);
        }

        @Test
        @DisplayName("evaluate as valid if one of the subrules throws an exception but the other one succeeds first")
        void validAndExceptionWithDelay() {
            final var ruleOne = new MockRule("rule-1", Result.VALID);
            final var ruleTwo = new DelayedExceptionRule("rule-2", 500);
            rule.setRules(List.of(ruleOne, ruleTwo));

            final var evaluationResult = rule.evaluate(resolver);

            final var interimResult = buildRuleResult(ruleResultBuilder, Result.MAYBE);
            assertThat(evaluationResult.snapshot()).isEqualTo(interimResult);

            final var result = evaluationResult.status().join();

            assertThat(result).isEqualTo(Result.VALID);
            assertThat(evaluationResult.snapshot())
                    .extracting(RuleResult::getType, RuleResult::getResult)
                    .containsExactly("OR", Result.VALID);
            assertThat(evaluationResult.snapshot())
                    .isInstanceOfSatisfying(MultiaryBooleanRuleResult.class,
                            ruleResult -> assertThat(ruleResult.getRules())
                                    .extracting(RuleResult::getType, RuleResult::getResult)
                                    .contains(Tuple.tuple("MOCK_RULE", Result.VALID)));
        }

        @Test
        @DisplayName("evaluate action on completion with exception")
        void actionOnCompletionWithException() {
            final var ruleOne = new DelayedRule("rule-1", Result.VALID, 500);
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