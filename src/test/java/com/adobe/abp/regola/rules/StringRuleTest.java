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
import com.adobe.abp.regola.results.ValuesRuleResult;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Testing StringRule with operator")
class StringRuleTest {

    private StringRule rule;
    private final FactsResolver resolver = mock(FactsResolver.class);

    private static final String RULE_KEY = "string_fact";
    private static final String INVALID_FACT = "invalid-fact";

    @BeforeEach
    void setup() {
        rule = new StringRule();
        rule.setKey(RULE_KEY);
    }

    @Test
    @DisplayName("have the expected STRING type")
    void ruleHasExpectedType() {
        assertThat(rule.getType()).isEqualTo("STRING");
    }

    @Nested
    @DisplayName("equals should")
    class Equals {

        private ValuesRuleResult.RuleResultBuilder<String> ruleResultBuilder;

        @BeforeEach
        void setup() {
            rule.setOperator(Operator.EQUALS);
            rule.setValue("some-value");

            ruleResultBuilder = ValuesRuleResult.<String>builder().with(r -> {
                r.type = RuleType.STRING.getName();
                r.operator = Operator.EQUALS;
                r.key = RULE_KEY;
                r.expectedValue = "some-value";
            });
        }

        @Test
        @DisplayName("evaluate as valid if fact matches")
        void factIsValid() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> "some-value"));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID, "some-value", "some-value");
        }

        @Test
        @DisplayName("evaluate as invalid if fact does not match")
        void factIsNotValid() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> INVALID_FACT));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID, INVALID_FACT, "some-value");
        }

        @Test
        @DisplayName("evaluate as invalid if fact does not match the null value of the rule")
        void factIsNotValidOnNullRuleValue() {
            rule.setValue(null);
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> "some-value"));

            RuleTestUtils.evaluateAndTestWithNullValue(rule, resolver, ruleResultBuilder, Result.INVALID, "some-value");
        }
    }

    @Nested
    @DisplayName("greater-than should")
    class GreaterThan {

        private ValuesRuleResult.RuleResultBuilder<String> ruleResultBuilder;

        @BeforeEach
        void setup() {
            rule.setOperator(Operator.GREATER_THAN);
            rule.setValue("B");

            ruleResultBuilder = ValuesRuleResult.<String>builder().with(r -> {
                r.type = RuleType.STRING.getName();
                r.operator = Operator.GREATER_THAN;
                r.key = RULE_KEY;
                r.expectedValue = "B";
            });
        }

        @Test
        @DisplayName("evaluate as valid if fact matches")
        void factIsValid() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> "C"));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID, "C", "B");
        }

        @Test
        @DisplayName("evaluate as invalid if fact does not match")
        void factIsNotValid() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> "A"));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID, "A", "B");
        }

        @Test
        @DisplayName("evaluate as invalid if fact does not match (same value)")
        void factIsNotValidWithSameValue() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> "B"));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID, "B", "B");
        }

        @Test
        @DisplayName("evaluate as valid if fact matches (case sensitive)")
        void factIsValidCaseSensitive() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> "c"));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID, "c", "B");
        }

        @Test
        @DisplayName("evaluate as invalid if fact exists and rule has a null value")
        void factIsInvalidOnNullRuleValue() {
            rule.setValue(null);
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> "some-value"));

            RuleTestUtils.evaluateAndTestWithNullValue(rule, resolver, ruleResultBuilder, Result.INVALID, "some-value");
        }
    }

    @Nested
    @DisplayName("greater-than-equal should")
    class GreaterThanEqual {

        private ValuesRuleResult.RuleResultBuilder<String> ruleResultBuilder;

        @BeforeEach
        void setup() {
            rule.setOperator(Operator.GREATER_THAN_EQUAL);
            rule.setValue("B");

            ruleResultBuilder = ValuesRuleResult.<String>builder().with(r -> {
                r.type = RuleType.STRING.getName();
                r.operator = Operator.GREATER_THAN_EQUAL;
                r.key = RULE_KEY;
                r.expectedValue = "B";
            });
        }

        @Test
        @DisplayName("evaluate as valid if fact matches")
        void factIsValid() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> "C"));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID, "C", "B");
        }

        @Test
        @DisplayName("evaluate as invalid if fact does not match")
        void factIsNotValid() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> "A"));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID, "A", "B");
        }

        @Test
        @DisplayName("evaluate as valid if fact matches (same value)")
        void factIsValidWithSameValue() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> "B"));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID, "B", "B");
        }

        @Test
        @DisplayName("evaluate as valid if fact matches (case sensitive)")
        void factIsValidCaseSensitive() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> "c"));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID, "c", "B");
        }

        @Test
        @DisplayName("evaluate as invalid if fact exists and rule has a null value")
        void factIsInvalidOnNullRuleValue() {
            rule.setValue(null);
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> "some-value"));

            RuleTestUtils.evaluateAndTestWithNullValue(rule, resolver, ruleResultBuilder, Result.INVALID, "some-value");
        }
    }

    @Nested
    @DisplayName("less-than should")
    class LessThan {

        private ValuesRuleResult.RuleResultBuilder<String> ruleResultBuilder;

        @BeforeEach
        void setup() {
            rule.setOperator(Operator.LESS_THAN);
            rule.setValue("b");

            ruleResultBuilder = ValuesRuleResult.<String>builder().with(r -> {
                r.type = RuleType.STRING.getName();
                r.operator = Operator.LESS_THAN;
                r.key = RULE_KEY;
                r.expectedValue = "b";
            });
        }

        @Test
        @DisplayName("evaluate as valid if fact matches")
        void factIsValid() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> "a"));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID, "a", "b");
        }

        @Test
        @DisplayName("evaluate as invalid if fact does not match")
        void factIsNotValid() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> "c"));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID, "c", "b");
        }

        @Test
        @DisplayName("evaluate as invalid if fact does not match (same value)")
        void factIsNotValidWithSameValue() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> "b"));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID, "b", "b");
        }

        @Test
        @DisplayName("evaluate as valid if fact matches (case sensitive)")
        void factIsValidCaseSensitive() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> "C"));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID, "C", "b");
        }

        @Test
        @DisplayName("evaluate as invalid if fact exists and rule has a null value")
        void factIsNotValidOnNullRuleValue() {
            rule.setValue(null);
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> "some-value"));

            RuleTestUtils.evaluateAndTestWithNullValue(rule, resolver, ruleResultBuilder, Result.INVALID, "some-value");
        }
    }

    @Nested
    @DisplayName("less-than-equal should")
    class LessThanEqual {

        private ValuesRuleResult.RuleResultBuilder<String> ruleResultBuilder;

        @BeforeEach
        void setup() {
            rule.setOperator(Operator.LESS_THAN_EQUAL);
            rule.setValue("b");

            ruleResultBuilder = ValuesRuleResult.<String>builder().with(r -> {
                r.type = RuleType.STRING.getName();
                r.operator = Operator.LESS_THAN_EQUAL;
                r.key = RULE_KEY;
                r.expectedValue = "b";
            });
        }

        @Test
        @DisplayName("evaluate as valid if fact matches")
        void factIsValid() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> "a"));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID, "a", "b");
        }

        @Test
        @DisplayName("evaluate as invalid if fact does not match")
        void factIsNotValid() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> "c"));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID, "c", "b");
        }

        @Test
        @DisplayName("evaluate as valid if fact matches (same value)")
        void factIsNotValidWithSameValue() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> "b"));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID, "b", "b");
        }

        @Test
        @DisplayName("evaluate as valid if fact matches (case sensitive)")
        void factIsValidCaseSensitive() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> "C"));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID, "C", "b");
        }

        @Test
        @DisplayName("evaluate as invalid if fact exists and rule has a null value")
        void factIsNotValidOnNullRuleValue() {
            rule.setValue(null);
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> "some-value"));

            RuleTestUtils.evaluateAndTestWithNullValue(rule, resolver, ruleResultBuilder, Result.INVALID, "some-value");
        }
    }

    @Nested
    @DisplayName("contains should")
    class Contains {

        private ValuesRuleResult.RuleResultBuilder<String> ruleResultBuilder;

        @BeforeEach
        void setup() {
            rule.setOperator(Operator.CONTAINS);
            rule.setValue("some-value");

            ruleResultBuilder = ValuesRuleResult.<String>builder().with(r -> {
                r.type = RuleType.STRING.getName();
                r.operator = Operator.CONTAINS;
                r.key = RULE_KEY;
                r.expectedValue = "some-value";
            });
        }

        @Test
        @DisplayName("evaluate as valid if fact matches")
        void factIsValid() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> Set.of("before-value", "some-value", "after-value")));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID,
                    Set.of("before-value", "some-value", "after-value"), "some-value");
        }

        @Test
        @DisplayName("evaluate as invalid if fact does not match")
        void factIsNotValid() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> Set.of("before-value", INVALID_FACT, "after-value")));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID,
                    Set.of("before-value", INVALID_FACT, "after-value"), "some-value");
        }

        @Test
        @DisplayName("evaluate as invalid if fact does not match (case sensitive)")
        void factIsNotValidWithDifferentCase() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> Set.of("before-value", "SOME-VALUE", "after-value")));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID,
                    Set.of("before-value", "SOME-VALUE", "after-value"), "some-value");
        }

        @Test
        @DisplayName("evaluate as failed if fact is a list")
        void factIsFailedIfProvidingAList() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> List.of("before-value", "some-value", "after-value")));

            final var snapshot = RuleTestUtils.evaluateAndTestWithMessage(rule, resolver, ruleResultBuilder, Result.FAILED,
                    "java.lang.IllegalArgumentException: Fact must be a Set");
            assertThat(snapshot.getCause())
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Fact must be a Set");
        }

        @Test
        @DisplayName("evaluate as invalid if fact is null")
        void factIsFailedIfNull() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> null));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID);
        }
    }

    @Nested
    @DisplayName("not-supported should")
    class NotSupported {
        private ValuesRuleResult.RuleResultBuilder<String> ruleResultBuilder;

        void setup(Operator operator) {
            rule.setOperator(operator);
            rule.setValue("some-value");

            ruleResultBuilder = ValuesRuleResult.<String>builder().with(r -> {
                r.type = RuleType.STRING.getName();
                r.operator = operator;
                r.key = RULE_KEY;
                r.expectedValue = "some-value";
            });
        }

        @ParameterizedTest
        @EnumSource(value = Operator.class,
                names = {"EQUALS", "GREATER_THAN", "GREATER_THAN_EQUAL", "LESS_THAN", "LESS_THAN_EQUAL", "CONTAINS"},
                mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("evaluate as not supported")
        void factIsNotValidOnEmptyFact(Operator operator) {
            setup(operator);

            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> "some-value"));

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
        private ValuesRuleResult.RuleResultBuilder<String> ruleResultBuilder;

        void setup(Operator operator) {
            rule.setOperator(operator);
            rule.setValue("some-value");

            ruleResultBuilder = ValuesRuleResult.<String>builder().with(r -> {
                r.type = RuleType.STRING.getName();
                r.operator = operator;
                r.key = RULE_KEY;
                r.expectedValue = "some-value";
            });
        }

        @Test
        @DisplayName("evaluate and return description in result")
        void descriptionInResult() {
            setup(Operator.EQUALS);
            rule.setDescription("String should match");
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> "some-value"));
            ruleResultBuilder = ruleResultBuilder.with(r -> r.description = "String should match");

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID, "some-value", "some-value");
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
        @DisplayName("evaluate as invalid if rule has an empty value and fact is null")
        void emptyValueButFactIsNull(Operator operator) {
            setup(operator);

            rule.setValue("");
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> null));

            RuleTestUtils.evaluateAndTestWithValue(rule, resolver, ruleResultBuilder, Result.INVALID, "");
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

        @Test
        @DisplayName("evaluate action on completion")
        void actionOnCompletion() {
            setup(Operator.EQUALS);
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> "some-value"));

            var integerAtomicReference = new AtomicInteger(0);
            var action = new Action().setOnCompletion((result, throwable, ruleResult) -> integerAtomicReference.getAndIncrement());
            rule.setAction(action);

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID, "some-value", "some-value");

            assertThat(integerAtomicReference.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("evaluate action on completion with exception")
        void actionOnCompletionWithException() {
            setup(Operator.EQUALS);
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.failedFuture(new IllegalArgumentException("Failed to fetch fact")));

            var throwableAtomicReference = new AtomicReference<Throwable>();
            var action = new Action().setOnCompletion((result, throwable, ruleResult) -> throwableAtomicReference.set(throwable));
            rule.setAction(action);

            RuleTestUtils.evaluateAndTestWithMessage(rule, resolver, ruleResultBuilder, Result.FAILED,
                    "java.lang.IllegalArgumentException: Failed to fetch fact");

            assertThat(throwableAtomicReference.get()).hasMessageContaining("Failed to fetch fact");
        }
    }

    @Nested
    @DisplayName("with custom executor should")
    class WithCustomExecutor {

        private ValuesRuleResult.RuleResultBuilder<String> ruleResultBuilder;
        private final Executor executor = Executors.newCachedThreadPool();

        void setup(Operator operator) {
            rule = new StringRule(executor);
            rule.setKey(RULE_KEY);
            rule.setOperator(operator);
            rule.setValue("some-value");

            ruleResultBuilder = ValuesRuleResult.<String>builder().with(r -> {
                r.type = RuleType.STRING.getName();
                r.operator = operator;
                r.key = RULE_KEY;
                r.expectedValue = "some-value";
            });
        }

        @ParameterizedTest
        @EnumSource(value = Operator.class,
                names = {"EQUALS", "GREATER_THAN", "GREATER_THAN_EQUAL", "LESS_THAN", "LESS_THAN_EQUAL", "CONTAINS"},
                mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("evaluate as not supported")
        void factIsNotValidOnEmptyFact(Operator operator) {
            setup(operator);

            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> "any"));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.OPERATION_NOT_SUPPORTED);
        }
    }
}
