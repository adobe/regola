/*
 *  Copyright 2025 Adobe. All rights reserved.
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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Testing RangeRule")
class RangeRuleTest {

    private final FactsResolver resolver = mock(FactsResolver.class);

    private static final String RULE_KEY = "range_fact";

    @Nested
    @DisplayName("generic tests")
    class Generic {
        private RangeRule<String> rule;
        private ValuesRuleResult.RuleResultBuilder<String> ruleResultBuilder;

        @BeforeEach
        void setUp() {
            rule = new RangeRule<>();
            rule.setKey(RULE_KEY);
            rule.setOperator(Operator.BETWEEN);
            rule.setMin("a");
            rule.setMax("z");

            ruleResultBuilder = ValuesRuleResult.<String>builder().with(r -> {
                r.type = RuleType.RANGE.getName();
                r.operator = Operator.BETWEEN;
                r.key = RULE_KEY;
                r.expectedValues = List.of("a", "z");
            });
        }

        @Test
        void ruleHasExpectedType() {
            assertEquals(RuleType.RANGE.getName(), rule.getType());
        }

        @Test
        @DisplayName("evaluate and return description in result")
        void descriptionInResult() {
            rule.setDescription("character should be in range");

            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> "y"));
            ruleResultBuilder = ruleResultBuilder.with(r -> r.description = "character should be in range");

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID, "y", List.of("a", "z"));
        }

        @Test
        @DisplayName("evaluate as invalid if fact is null")
        void factIsNotValidOnNullFact() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> null));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID);
        }

        @Test
        @DisplayName("evaluate action on completion")
        void actionOnCompletion() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> "y"));

            var integerAtomicReference = new AtomicInteger(0);
            var action = new Action().setOnCompletion((result, throwable, ruleResult) -> integerAtomicReference.getAndIncrement());
            rule.setAction(action);

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID, "y", List.of("a", "z"));

            assertThat(integerAtomicReference.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("evaluate action on completion with exception")
        void actionOnCompletionWithException() {
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
    @DisplayName("with numbers")
    class WithNumbers {

        private RangeRule<Integer> rule;
        private ValuesRuleResult.RuleResultBuilder<Integer> ruleResultBuilder;

        @BeforeEach
        void setUp() {
            rule = new RangeRule<>();
            rule.setKey(RULE_KEY);
        }

        @Nested
        @DisplayName("value is between a range")
        class Between {

            @BeforeEach
            void setup() {
                rule.setOperator(Operator.BETWEEN);
                rule.setMin(-10);
                rule.setMax(5);

                ruleResultBuilder = ValuesRuleResult.<Integer>builder().with(r -> {
                    r.type = RuleType.RANGE.getName();
                    r.operator = Operator.BETWEEN;
                    r.key = RULE_KEY;
                    r.expectedValues = List.of(-10, 5);
                });
            }

            @ParameterizedTest
            @ValueSource(ints = { -10, -9, 0, 4, 5 })
            void factIsValid(Integer fact) {
                when(resolver.resolveFact(RULE_KEY))
                        .thenReturn(CompletableFuture.supplyAsync(() -> fact));

                RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID, fact, List.of(-10, 5));
            }

            @ParameterizedTest
            @ValueSource(ints = { -200, -11, 6, 100 })
            void factIsNotValid(Integer fact) {
                when(resolver.resolveFact(RULE_KEY))
                        .thenReturn(CompletableFuture.supplyAsync(() -> fact));

                RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID, fact, List.of(-10, 5));
            }

            @ParameterizedTest
            @ValueSource(ints = { -10, 5 })
            void factIsNotValidForExclusives(Integer fact) {
                rule.setMinExclusive(true);
                rule.setMaxExclusive(true);

                when(resolver.resolveFact(RULE_KEY))
                        .thenReturn(CompletableFuture.supplyAsync(() -> fact));

                RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID, fact, List.of(-10, 5));
            }

            @Test
            @DisplayName("evaluate as invalid if fact does not match the null value of the rule")
            void factIsNotValidOnNullRuleValue() {
                rule.setMin(null);
                rule.setMax(null);

                when(resolver.resolveFact(RULE_KEY))
                        .thenReturn(CompletableFuture.supplyAsync(() -> 10));

                RuleTestUtils.evaluateAndTestWithValueAndMessage(rule, resolver, ruleResultBuilder,
                        Result.FAILED, 10, null, List.of(), "min or max are null");
            }
        }

        @Nested
        @DisplayName("value is before range")
        class IsBefore {

            @BeforeEach
            void setup() {
                rule.setOperator(Operator.IS_BEFORE);
                rule.setMin(-10);
                rule.setMax(5);

                ruleResultBuilder = ValuesRuleResult.<Integer>builder().with(r -> {
                    r.type = RuleType.RANGE.getName();
                    r.operator = Operator.IS_BEFORE;
                    r.key = RULE_KEY;
                    r.expectedValues = List.of(-10, 5);
                });
            }

            @ParameterizedTest
            @ValueSource(ints = { -100, -11 })
            void factIsValid(Integer fact) {
                when(resolver.resolveFact(RULE_KEY))
                        .thenReturn(CompletableFuture.supplyAsync(() -> fact));

                RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID, fact, List.of(-10));
            }

            @ParameterizedTest
            @ValueSource(ints = { -10, -9, 0, 4, 5, 6, 10 })
            void factIsNotValid(Integer fact) {
                when(resolver.resolveFact(RULE_KEY))
                        .thenReturn(CompletableFuture.supplyAsync(() -> fact));

                RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID, fact, List.of(-10));
            }

            @ParameterizedTest
            @ValueSource(ints = { -9, 0, 4 })
            void factIsNotValidForExclusives(Integer fact) {
                rule.setMinExclusive(true);

                when(resolver.resolveFact(RULE_KEY))
                        .thenReturn(CompletableFuture.supplyAsync(() -> fact));

                RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID, fact, List.of(-10));
            }

            @ParameterizedTest
            @ValueSource(ints = { -100, -11, -10 })
            void factIsValidForExclusives(Integer fact) {
                rule.setMinExclusive(true);

                when(resolver.resolveFact(RULE_KEY))
                        .thenReturn(CompletableFuture.supplyAsync(() -> fact));

                RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID, fact, List.of(-10));
            }

            @Test
            @DisplayName("evaluate as invalid if fact does not match the null value of the rule")
            void factIsNotValidOnNullRuleValue() {
                rule.setMin(null);

                when(resolver.resolveFact(RULE_KEY))
                        .thenReturn(CompletableFuture.supplyAsync(() -> 10));

                RuleTestUtils.evaluateAndTestWithValueAndMessage(rule, resolver, ruleResultBuilder,
                        Result.FAILED, 10, null, List.of(), "min is null");
            }
        }

        @Nested
        @DisplayName("value is after range")
        class IsAfter {

            @BeforeEach
            void setup() {
                rule.setOperator(Operator.IS_AFTER);
                rule.setMin(-10);
                rule.setMax(5);

                ruleResultBuilder = ValuesRuleResult.<Integer>builder().with(r -> {
                    r.type = RuleType.RANGE.getName();
                    r.operator = Operator.IS_AFTER;
                    r.key = RULE_KEY;
                    r.expectedValues = List.of(-10, 5);
                });
            }

            @ParameterizedTest
            @ValueSource(ints = { 6, 100 })
            void factIsValid(Integer fact) {
                when(resolver.resolveFact(RULE_KEY))
                        .thenReturn(CompletableFuture.supplyAsync(() -> fact));

                RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID, fact, List.of(5));
            }

            @ParameterizedTest
            @ValueSource(ints = { -11, -10, -9, 0, 4, 5 })
            void factIsNotValid(Integer fact) {
                when(resolver.resolveFact(RULE_KEY))
                        .thenReturn(CompletableFuture.supplyAsync(() -> fact));

                RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID, fact, List.of(5));
            }

            @ParameterizedTest
            @ValueSource(ints = { -9, 0, 4 })
            void factIsNotValidForExclusives(Integer fact) {
                rule.setMaxExclusive(true);

                when(resolver.resolveFact(RULE_KEY))
                        .thenReturn(CompletableFuture.supplyAsync(() -> fact));

                RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID, fact, List.of(5));
            }

            @ParameterizedTest
            @ValueSource(ints = { 5, 6, 100 })
            void factIsValidForExclusives(Integer fact) {
                rule.setMaxExclusive(true);

                when(resolver.resolveFact(RULE_KEY))
                        .thenReturn(CompletableFuture.supplyAsync(() -> fact));

                RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID, fact, List.of(5));
            }

            @Test
            @DisplayName("evaluate as invalid if fact does not match the null value of the rule")
            void factIsNotValidOnNullRuleValue() {
                rule.setMax(null);

                when(resolver.resolveFact(RULE_KEY))
                        .thenReturn(CompletableFuture.supplyAsync(() -> 10));

                RuleTestUtils.evaluateAndTestWithValueAndMessage(rule, resolver, ruleResultBuilder,
                        Result.FAILED, 10, null, List.of(), "max is null");
            }
        }
    }

    @Nested
    @DisplayName("with strings")
    class WithStrings {

        private RangeRule<String> rule;
        private ValuesRuleResult.RuleResultBuilder<String> ruleResultBuilder;

        @BeforeEach
        void setUp() {
            rule = new RangeRule<>();
            rule.setKey(RULE_KEY);
        }

        @Nested
        @DisplayName("value is between a range")
        class Between {

            @BeforeEach
            void setup() {
                rule.setOperator(Operator.BETWEEN);
                rule.setMin("abba");
                rule.setMax("zoro");

                ruleResultBuilder = ValuesRuleResult.<String>builder().with(r -> {
                    r.type = RuleType.RANGE.getName();
                    r.operator = Operator.BETWEEN;
                    r.key = RULE_KEY;
                    r.expectedValues = List.of("abba", "zoro");
                });
            }

            @ParameterizedTest
            @ValueSource(strings = { "abbazzo", "castle", "zeppelin" })
            void factIsValid(String fact) {
                when(resolver.resolveFact(RULE_KEY))
                        .thenReturn(CompletableFuture.supplyAsync(() -> fact));

                RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID, fact, List.of("abba", "zoro"));
            }

            @ParameterizedTest
            @ValueSource(strings = { "a", "aba", "zorro", "zzz" })
            void factIsNotValid(String fact) {
                when(resolver.resolveFact(RULE_KEY))
                        .thenReturn(CompletableFuture.supplyAsync(() -> fact));

                RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID, fact, List.of("abba", "zoro"));
            }

            @ParameterizedTest
            @ValueSource(strings = { "abba", "zoro" })
            void factIsNotValidForExclusives(String fact) {
                rule.setMinExclusive(true);
                rule.setMaxExclusive(true);

                when(resolver.resolveFact(RULE_KEY))
                        .thenReturn(CompletableFuture.supplyAsync(() -> fact));

                RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID, fact, List.of("abba", "zoro"));
            }

            @Test
            @DisplayName("evaluate as invalid if fact does not match the null value of the rule")
            void factIsNotValidOnNullRuleValue() {
                rule.setMin(null);
                rule.setMax(null);

                when(resolver.resolveFact(RULE_KEY))
                        .thenReturn(CompletableFuture.supplyAsync(() -> "yuppi"));

                RuleTestUtils.evaluateAndTestWithValueAndMessage(rule, resolver, ruleResultBuilder,
                        Result.FAILED, "yuppi", null, List.of(), "min or max are null");
            }
        }

        @Nested
        @DisplayName("value is before range")
        class IsBefore {

            @BeforeEach
            void setup() {
                rule.setOperator(Operator.IS_BEFORE);
                rule.setMin("abba");
                rule.setMax("zoro");

                ruleResultBuilder = ValuesRuleResult.<String>builder().with(r -> {
                    r.type = RuleType.RANGE.getName();
                    r.operator = Operator.IS_BEFORE;
                    r.key = RULE_KEY;
                    r.expectedValues = List.of("abba", "zoro");
                });
            }

            @ParameterizedTest
            @ValueSource(strings = { "a", "abaa" })
            void factIsValid(String fact) {
                when(resolver.resolveFact(RULE_KEY))
                        .thenReturn(CompletableFuture.supplyAsync(() -> fact));

                RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID, fact, List.of("abba"));
            }

            @ParameterizedTest
            @ValueSource(strings = { "abba", "abbab", "castle", "zeppelin", "zoro" })
            void factIsNotValid(String fact) {
                when(resolver.resolveFact(RULE_KEY))
                        .thenReturn(CompletableFuture.supplyAsync(() -> fact));

                RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID, fact, List.of("abba"));
            }

            @ParameterizedTest
            @ValueSource(strings = { "abbab", "abbazzo" })
            void factIsNotValidForExclusives(String fact) {
                rule.setMinExclusive(true);

                when(resolver.resolveFact(RULE_KEY))
                        .thenReturn(CompletableFuture.supplyAsync(() -> fact));

                RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID, fact, List.of("abba"));
            }

            @ParameterizedTest
            @ValueSource(strings = { "a", "aba", "abaa", "abba" })
            void factIsValidForExclusives(String fact) {
                rule.setMinExclusive(true);

                when(resolver.resolveFact(RULE_KEY))
                        .thenReturn(CompletableFuture.supplyAsync(() -> fact));

                RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID, fact, List.of("abba"));
            }

            @Test
            @DisplayName("evaluate as invalid if fact does not match the null value of the rule")
            void factIsNotValidOnNullRuleValue() {
                rule.setMin(null);

                when(resolver.resolveFact(RULE_KEY))
                        .thenReturn(CompletableFuture.supplyAsync(() -> "yuppi"));

                RuleTestUtils.evaluateAndTestWithValueAndMessage(rule, resolver, ruleResultBuilder,
                        Result.FAILED, "yuppi", null, List.of(), "min is null");
            }
        }

        @Nested
        @DisplayName("value is after range")
        class IsAfter {

            @BeforeEach
            void setup() {
                rule.setOperator(Operator.IS_AFTER);
                rule.setMin("abba");
                rule.setMax("zoro");

                ruleResultBuilder = ValuesRuleResult.<String>builder().with(r -> {
                    r.type = RuleType.RANGE.getName();
                    r.operator = Operator.IS_AFTER;
                    r.key = RULE_KEY;
                    r.expectedValues = List.of("abba", "zoro");
                });
            }

            @ParameterizedTest
            @ValueSource(strings = { "zorro", "zzz" })
            void factIsValid(String fact) {
                when(resolver.resolveFact(RULE_KEY))
                        .thenReturn(CompletableFuture.supplyAsync(() -> fact));

                RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID, fact, List.of("zoro"));
            }

            @ParameterizedTest
            @ValueSource(strings = { "a", "abaa", "abba", "castle", "zora", "zoro" })
            void factIsNotValid(String fact) {
                when(resolver.resolveFact(RULE_KEY))
                        .thenReturn(CompletableFuture.supplyAsync(() -> fact));

                RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID, fact, List.of("zoro"));
            }

            @ParameterizedTest
            @ValueSource(strings = { "castle", "zorn" })
            void factIsNotValidForExclusives(String fact) {
                rule.setMaxExclusive(true);

                when(resolver.resolveFact(RULE_KEY))
                        .thenReturn(CompletableFuture.supplyAsync(() -> fact));

                RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID, fact, List.of("zoro"));
            }

            @ParameterizedTest
            @ValueSource(strings = { "zoro", "zorp", "zzz" })
            void factIsValidForExclusives(String fact) {
                rule.setMaxExclusive(true);

                when(resolver.resolveFact(RULE_KEY))
                        .thenReturn(CompletableFuture.supplyAsync(() -> fact));

                RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID, fact, List.of("zoro"));
            }

            @Test
            @DisplayName("evaluate as invalid if fact does not match the null value of the rule")
            void factIsNotValidOnNullRuleValue() {
                rule.setMax(null);

                when(resolver.resolveFact(RULE_KEY))
                        .thenReturn(CompletableFuture.supplyAsync(() -> "yuppi"));

                RuleTestUtils.evaluateAndTestWithValueAndMessage(rule, resolver, ruleResultBuilder,
                        Result.FAILED, "yuppi", null, List.of(), "max is null");
            }
        }
    }

    @Nested
    @DisplayName("with custom executor should")
    class WithCustomExecutor {

        private ValuesRuleResult.RuleResultBuilder<String> ruleResultBuilder;
        private final Executor executor = Executors.newCachedThreadPool();
        private RangeRule<String> rule;

        @BeforeEach
        void setUp() {
            rule = new RangeRule<>(executor);
            rule.setKey(RULE_KEY);
            rule.setOperator(Operator.EQUALS);
            rule.setMin("a");
            rule.setMax("z");

            ruleResultBuilder = ValuesRuleResult.<String>builder().with(r -> {
                r.type = RuleType.RANGE.getName();
                r.operator = Operator.EQUALS;
                r.key = RULE_KEY;
                r.expectedValues = List.of();
            });
        }

        @Test
        @DisplayName("evaluate as not supported")
        void operationIsNotSupported() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> " "));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.OPERATION_NOT_SUPPORTED);
        }
    }
}
