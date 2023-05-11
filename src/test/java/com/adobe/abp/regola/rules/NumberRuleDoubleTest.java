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

import com.adobe.abp.regola.facts.FactsResolver;
import com.adobe.abp.regola.results.Result;
import com.adobe.abp.regola.results.ValuesRuleResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Testing NumberRule<Double> with operator")
class NumberRuleDoubleTest {

    private NumberRule<Double> rule;
    private final FactsResolver resolver = mock(FactsResolver.class);

    private static final String RULE_KEY = "number_fact";
    private static final Double INVALID_FACT = -0.1;

    @BeforeEach
    void setup() {
        rule = new NumberRule<>();
        rule.setKey(RULE_KEY);
    }

    @Test
    @DisplayName("have the expected NUMBER type")
    void ruleHasExpectedType() {
        assertThat(rule.getType()).isEqualTo("NUMBER");
    }

    @Nested
    @DisplayName("equals should")
    class Equals {

        private ValuesRuleResult.RuleResultBuilder<Double> ruleResultBuilder;
        private ValuesRuleResult.RuleResultBuilder<Object> ruleResultBuilderMixedTypes;

        @BeforeEach
        void setup() {
            rule.setOperator(Operator.EQUALS);
            rule.setValue(0.1);

            ruleResultBuilder = ValuesRuleResult.<Double>builder().with(r -> {
                r.type = RuleType.NUMBER.getName();
                r.operator = Operator.EQUALS;
                r.key = RULE_KEY;
                r.expectedValue = 0.1;
            });
            ruleResultBuilderMixedTypes = ValuesRuleResult.builder().with(r -> {
                r.type = RuleType.NUMBER.getName();
                r.operator = Operator.EQUALS;
                r.key = RULE_KEY;
                r.expectedValue = 0.1;
            });
        }

        @Test
        @DisplayName("evaluate as valid if fact matches")
        void factIsValid() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> 0.1));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID, 0.1, 0.1);
        }

        @Test
        @DisplayName("evaluate as invalid if fact does not match")
        void factIsNotValid() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> INVALID_FACT));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID, INVALID_FACT, 0.1);
        }

        @Test
        @DisplayName("evaluate as invalid if fact does not match the null value of the rule")
        void factIsNotValidOnNullRuleValue() {
            rule.setValue(null);
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> 0.1));

            RuleTestUtils.evaluateAndTestWithNullValue(rule, resolver, ruleResultBuilder, Result.INVALID, 0.1);
        }

        @Test
        @DisplayName("evaluate as valid if (integer) fact matches")
        void integerFactIsValid() {
            rule.setValue(0.0);
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> 0));

            RuleTestUtils.evaluateAndTestWithAnyTypeValue(rule, resolver, ruleResultBuilderMixedTypes, Result.VALID, 0, 0.0);
        }

        @Test
        @DisplayName("evaluate as invalid if (integer) fact does not match")
        void integerFactIsNotValid() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> 0)); // no rounding of operation should happen between 0.1 and 0

            RuleTestUtils.evaluateAndTestWithAnyTypeValue(rule, resolver, ruleResultBuilderMixedTypes, Result.INVALID, 0, 0.1);
        }
    }

    @Nested
    @DisplayName("greater-than should")
    class GreaterThan {

        private ValuesRuleResult.RuleResultBuilder<Double> ruleResultBuilder;
        private ValuesRuleResult.RuleResultBuilder<Object> ruleResultBuilderMixedTypes;

        @BeforeEach
        void setup() {
            rule.setOperator(Operator.GREATER_THAN);
            rule.setValue(0.);

            ruleResultBuilder = ValuesRuleResult.<Double>builder().with(r -> {
                r.type = RuleType.NUMBER.getName();
                r.operator = Operator.GREATER_THAN;
                r.key = RULE_KEY;
                r.expectedValue = 0.;
            });
            ruleResultBuilderMixedTypes = ValuesRuleResult.builder().with(r -> {
                r.type = RuleType.NUMBER.getName();
                r.operator = Operator.GREATER_THAN;
                r.key = RULE_KEY;
                r.expectedValue = 0.;
            });
        }

        @Test
        @DisplayName("evaluate as valid if fact matches")
        void factIsValid() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> 0.1));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID, 0.1, 0.0);
        }

        @Test
        @DisplayName("evaluate as invalid if fact does not match")
        void factIsNotValid() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> -0.1));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID, -0.1, 0.0);
        }

        @Test
        @DisplayName("evaluate as invalid if fact does not match (same value)")
        void factIsNotValidWithSameValue() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> 0.));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID, 0.0, 0.0);
        }

        @Test
        @DisplayName("evaluate as invalid if fact exists and rule has a null value")
        void factIsInvalidOnNullRuleValue() {
            rule.setValue(null);
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> 0.));

            RuleTestUtils.evaluateAndTestWithNullValue(rule, resolver, ruleResultBuilder, Result.INVALID, 0.0);
        }

        @Test
        @DisplayName("evaluate as valid if (integer) fact matches")
        void integerFactIsValid() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> 1));

            RuleTestUtils.evaluateAndTestWithAnyTypeValue(rule, resolver, ruleResultBuilderMixedTypes, Result.VALID, 1, 0.0);
        }

        @Test
        @DisplayName("evaluate as invalid if (integer) fact does not match")
        void integerFactIsNotValid() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> 0)); // no rounding of operation should happen between 0. and 0

            RuleTestUtils.evaluateAndTestWithAnyTypeValue(rule, resolver, ruleResultBuilderMixedTypes, Result.INVALID, 0, 0.0);
        }
    }

    @Nested
    @DisplayName("greater-than-equal should")
    class GreaterThanEqual {

        private ValuesRuleResult.RuleResultBuilder<Double> ruleResultBuilder;
        private ValuesRuleResult.RuleResultBuilder<Object> ruleResultBuilderMixedTypes;

        @BeforeEach
        void setup() {
            rule.setOperator(Operator.GREATER_THAN_EQUAL);
            rule.setValue(0.);

            ruleResultBuilder = ValuesRuleResult.<Double>builder().with(r -> {
                r.type = RuleType.NUMBER.getName();
                r.operator = Operator.GREATER_THAN_EQUAL;
                r.key = RULE_KEY;
                r.expectedValue = 0.;
            });
            ruleResultBuilderMixedTypes = ValuesRuleResult.builder().with(r -> {
                r.type = RuleType.NUMBER.getName();
                r.operator = Operator.GREATER_THAN_EQUAL;
                r.key = RULE_KEY;
                r.expectedValue = 0.;
            });
        }

        @Test
        @DisplayName("evaluate as valid if fact matches")
        void factIsValid() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> 0.1));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID, 0.1, 0.0);
        }

        @Test
        @DisplayName("evaluate as invalid if fact does not match")
        void factIsNotValid() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> -0.1));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID, -0.1, 0.0);
        }

        @Test
        @DisplayName("evaluate as valid if fact matches (same value)")
        void factIsValidWithSameValue() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> 0.));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID, 0.0, 0.0);
        }

        @Test
        @DisplayName("evaluate as invalid if fact exists and rule has a null value")
        void factIsInvalidOnNullRuleValue() {
            rule.setValue(null);
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> 0.));

            RuleTestUtils.evaluateAndTestWithNullValue(rule, resolver, ruleResultBuilder, Result.INVALID, 0.0);
        }

        @Test
        @DisplayName("evaluate as valid if (integer) fact matches")
        void integerFactIsValid() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> 1));

            RuleTestUtils.evaluateAndTestWithAnyTypeValue(rule, resolver, ruleResultBuilderMixedTypes, Result.VALID, 1, 0.0);
        }

        @Test
        @DisplayName("evaluate as valid if (integer) fact matches (same value)")
        void integerFactIsValidWithSameValue() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> 0));

            RuleTestUtils.evaluateAndTestWithAnyTypeValue(rule, resolver, ruleResultBuilderMixedTypes, Result.VALID, 0, 0.0);
        }

        @Test
        @DisplayName("evaluate as invalid if (integer) fact does not match")
        void integerFactIsNotValid() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> -1));

            RuleTestUtils.evaluateAndTestWithAnyTypeValue(rule, resolver, ruleResultBuilderMixedTypes, Result.INVALID, -1, 0.0);
        }
    }

    @Nested
    @DisplayName("less-than should")
    class LessThan {

        private ValuesRuleResult.RuleResultBuilder<Double> ruleResultBuilder;
        private ValuesRuleResult.RuleResultBuilder<Object> ruleResultBuilderMixedTypes;

        @BeforeEach
        void setup() {
            rule.setOperator(Operator.LESS_THAN);
            rule.setValue(0.);

            ruleResultBuilder = ValuesRuleResult.<Double>builder().with(r -> {
                r.type = RuleType.NUMBER.getName();
                r.operator = Operator.LESS_THAN;
                r.key = RULE_KEY;
                r.expectedValue = 0.;
            });
            ruleResultBuilderMixedTypes = ValuesRuleResult.builder().with(r -> {
                r.type = RuleType.NUMBER.getName();
                r.operator = Operator.LESS_THAN;
                r.key = RULE_KEY;
                r.expectedValue = 0.;
            });
        }

        @Test
        @DisplayName("evaluate as valid if fact matches")
        void factIsValid() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> -0.1));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID, -0.1, 0.0);
        }

        @Test
        @DisplayName("evaluate as invalid if fact does not match")
        void factIsNotValid() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> 0.1));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID, 0.1, 0.0);
        }

        @Test
        @DisplayName("evaluate as invalid if fact does not match (same value)")
        void factIsNotValidWithSameValue() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> 0.));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID, 0.0, 0.0);
        }

        @Test
        @DisplayName("evaluate as invalid valid if fact exists and rule has a null value")
        void factIsNotValidOnNullRuleValue() {
            rule.setValue(null);
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> 0.));

            RuleTestUtils.evaluateAndTestWithNullValue(rule, resolver, ruleResultBuilder, Result.INVALID, 0.0);
        }

        @Test
        @DisplayName("evaluate as valid if (integer) fact matches")
        void integerFactIsValid() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> -1));

            RuleTestUtils.evaluateAndTestWithAnyTypeValue(rule, resolver, ruleResultBuilderMixedTypes, Result.VALID, -1, 0.0);
        }

        @Test
        @DisplayName("evaluate as invalid if (integer) fact does not match")
        void integerFactIsNotValid() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> 0)); // no rounding of operation should happen between 0. and 0

            RuleTestUtils.evaluateAndTestWithAnyTypeValue(rule, resolver, ruleResultBuilderMixedTypes, Result.INVALID, 0, 0.0);
        }
    }

    @Nested
    @DisplayName("less-than-equal should")
    class LessThanEqual {

        private ValuesRuleResult.RuleResultBuilder<Double> ruleResultBuilder;
        private ValuesRuleResult.RuleResultBuilder<Object> ruleResultBuilderMixedTypes;

        @BeforeEach
        void setup() {
            rule.setOperator(Operator.LESS_THAN_EQUAL);
            rule.setValue(0.);

            ruleResultBuilder = ValuesRuleResult.<Double>builder().with(r -> {
                r.type = RuleType.NUMBER.getName();
                r.operator = Operator.LESS_THAN_EQUAL;
                r.key = RULE_KEY;
                r.expectedValue = 0.;
            });
            ruleResultBuilderMixedTypes = ValuesRuleResult.builder().with(r -> {
                r.type = RuleType.NUMBER.getName();
                r.operator = Operator.LESS_THAN_EQUAL;
                r.key = RULE_KEY;
                r.expectedValue = 0.;
            });
        }

        @Test
        @DisplayName("evaluate as valid if fact matches")
        void factIsValid() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> -0.1));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID, -0.1, 0.0);
        }

        @Test
        @DisplayName("evaluate as invalid if fact does not match")
        void factIsNotValid() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> 0.1));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID, 0.1, 0.0);
        }

        @Test
        @DisplayName("evaluate as valid if fact matches (same value)")
        void factIsValidWithSameValue() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> 0.));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID, 0.0, 0.0);
        }

        @Test
        @DisplayName("evaluate as invalid valid if fact exists and rule has a null value")
        void factIsNotValidOnNullRuleValue() {
            rule.setValue(null);
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> 0.));

            RuleTestUtils.evaluateAndTestWithNullValue(rule, resolver, ruleResultBuilder, Result.INVALID, 0.0);
        }

        @Test
        @DisplayName("evaluate as valid if (integer) fact matches")
        void integerFactIsValid() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> -1));

            RuleTestUtils.evaluateAndTestWithAnyTypeValue(rule, resolver, ruleResultBuilderMixedTypes, Result.VALID, -1, 0.0);
        }

        @Test
        @DisplayName("evaluate as valid if (integer) fact matches (same value)")
        void integerFactIsValidWithSameValue() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> 0));

            RuleTestUtils.evaluateAndTestWithAnyTypeValue(rule, resolver, ruleResultBuilderMixedTypes, Result.VALID, 0, 0.0);
        }

        @Test
        @DisplayName("evaluate as invalid if (integer) fact does not match")
        void integerFactIsNotValid() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> 1));

            RuleTestUtils.evaluateAndTestWithAnyTypeValue(rule, resolver, ruleResultBuilderMixedTypes, Result.INVALID, 1, 0.0);
        }
    }

    @Nested
    @DisplayName("contains should")
    class Contains {

        private ValuesRuleResult.RuleResultBuilder<Double> ruleResultBuilder;
        private ValuesRuleResult.RuleResultBuilder<Object> ruleResultBuilderMixedTypes;

        @BeforeEach
        void setup() {
            rule.setOperator(Operator.CONTAINS);
            rule.setValue(0.1);

            ruleResultBuilder = ValuesRuleResult.<Double>builder().with(r -> {
                r.type = RuleType.NUMBER.getName();
                r.operator = Operator.CONTAINS;
                r.key = RULE_KEY;
                r.expectedValue = 0.1;
            });
            ruleResultBuilderMixedTypes = ValuesRuleResult.builder().with(r -> {
                r.type = RuleType.NUMBER.getName();
                r.operator = Operator.CONTAINS;
                r.key = RULE_KEY;
                r.expectedValue = 0.;
            });
        }

        @Test
        @DisplayName("evaluate as valid if fact matches")
        void factIsValid() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> Set.of(0., 0.1, 2.)));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID, Set.of(0.0, 0.1, 2.0), 0.1);
        }

        @Test
        @DisplayName("evaluate as invalid if fact does not match")
        void factIsNotValid() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> Set.of(0., -0.1, 2.)));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID, Set.of(0.0, -0.1, 2.0), 0.1);
        }

        @Test
        @DisplayName("evaluate as invalid if (integer) fact matches, but different type")
        void integerFactIsNotValid() {
            rule.setValue(0.);
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> Set.of(-1, 0, 1)));

            RuleTestUtils.evaluateAndTestWithAnyTypeValue(rule, resolver, ruleResultBuilderMixedTypes, Result.INVALID, Set.of(-1, 0, 1), 0.0);
        }

        @Test
        @DisplayName("evaluate as failed if fact is list")
        void factIsFailedOnList() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> List.of(0., 0.1, 2.)));

            final var snapshot = RuleTestUtils.evaluateAndTestWithMessage(rule, resolver, ruleResultBuilder, Result.FAILED,
                    "java.lang.IllegalArgumentException: Fact must be a Set");
            assertThat(snapshot.getCause())
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Fact must be a Set");
        }
    }

    @Nested
    @DisplayName("not-supported should")
    class NotSupported {
        private ValuesRuleResult.RuleResultBuilder<Double> ruleResultBuilder;

        void setup(Operator operator) {
            rule.setOperator(operator);
            rule.setValue(0.1);

            ruleResultBuilder = ValuesRuleResult.<Double>builder().with(r -> {
                r.type = RuleType.NUMBER.getName();
                r.operator = operator;
                r.key = RULE_KEY;
                r.expectedValue = 0.1;
            });
        }

        @ParameterizedTest
        @EnumSource(value = Operator.class,
                names = {"EQUALS", "GREATER_THAN", "GREATER_THAN_EQUAL", "LESS_THAN", "LESS_THAN_EQUAL", "CONTAINS"},
                mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("evaluate as invalid")
        void factIsNotValidOnEmptyFact(Operator operator) {
            setup(operator);

            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> 0.1));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.OPERATION_NOT_SUPPORTED);
        }

        @ParameterizedTest
        @EnumSource(value = Operator.class,
                names = {"EQUALS", "GREATER_THAN", "GREATER_THAN_EQUAL", "LESS_THAN", "LESS_THAN_EQUAL", "CONTAINS"},
                mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("evaluate as not supported even if both fact and rule value are null")
        void factAndRuleValueAreNull(Operator operator) {
            setup(operator);

            rule.setValue(null);
            ruleResultBuilder = ruleResultBuilder.with(r -> r.expectedValue = null);
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> null));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.OPERATION_NOT_SUPPORTED);
        }
    }

    @Nested
    @DisplayName("any-supported-rule should")
    class GenericTests {
        private ValuesRuleResult.RuleResultBuilder<Double> ruleResultBuilder;

        void setup(Operator operator) {
            rule.setOperator(operator);
            rule.setValue(0.1);

            ruleResultBuilder = ValuesRuleResult.<Double>builder().with(r -> {
                r.type = RuleType.NUMBER.getName();
                r.operator = operator;
                r.key = RULE_KEY;
                r.expectedValue = 0.1;
            });
        }

        @ParameterizedTest
        @EnumSource(value = Operator.class,
                names = {"EQUALS", "GREATER_THAN", "GREATER_THAN_EQUAL", "LESS_THAN", "LESS_THAN_EQUAL", "CONTAINS"})
        @DisplayName("evaluate as invalid if fact is null")
        void factIsNotValidOnNullFact(Operator operator) {
            setup(operator);

            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> null));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID);
        }

        @ParameterizedTest
        @EnumSource(value = Operator.class,
                names = {"EQUALS", "GREATER_THAN", "GREATER_THAN_EQUAL", "LESS_THAN", "LESS_THAN_EQUAL", "CONTAINS"})
        @DisplayName("evaluate as invalid if fact and value are both null")
        void nullFactIsInvalidIfRuleValueIsNull(Operator operator) {
            setup(operator);

            rule.setValue(null);
            ruleResultBuilder = ruleResultBuilder.with(r -> r.expectedValue = null);
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> null));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID);
        }
    }
}
