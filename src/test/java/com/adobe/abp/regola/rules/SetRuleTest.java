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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Testing SetRule<T>")
class SetRuleTest {

    private SetRule<String> rule;
    private final FactsResolver resolver = mock(FactsResolver.class);

    private static final String RULE_KEY = "string_set_fact";
    private static final String INVALID_FACT = "invalid-fact";

    @Nested
    @DisplayName("with custom executor and with operator")
    class WithCustomExecutor {
        private final Executor executor = Executors.newCachedThreadPool();

        @BeforeEach
        void setup() {
            rule = new SetRule<>(executor);
            rule.setKey(RULE_KEY);
        }

        @Test
        @DisplayName("evaluate and return description in result")
        void descriptionInResult() {
            rule.setOperator(Operator.IN);
            rule.setValues(Set.of("before-value", "some-value"));
            rule.setDescription("Set should be valid");

            final var ruleResultBuilder = ValuesRuleResult.<String>builder().with(r -> {
                r.type = RuleType.SET.getName();
                r.operator = Operator.IN;
                r.key = RULE_KEY;
                r.description = "Set should be valid";
                r.expectedValues = Set.of("before-value", "some-value");
            });
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> "some-value"));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID,
                    "some-value", Set.of("before-value", "some-value"));

        }

        @Nested
        @DisplayName("in should")
        class In {

            private ValuesRuleResult.RuleResultBuilder<String> ruleResultBuilder;

            @BeforeEach
            void setup() {
                rule.setOperator(Operator.IN);
                rule.setValues(Set.of("before-value", "some-value", "after-value"));

                ruleResultBuilder = ValuesRuleResult.<String>builder().with(r -> {
                    r.type = RuleType.SET.getName();
                    r.operator = Operator.IN;
                    r.key = RULE_KEY;
                    r.expectedValues = Set.of("before-value", "some-value", "after-value");
                });
            }

            @Test
            @DisplayName("evaluate as valid if fact matches")
            void factIsValid() {
                when(resolver.resolveFact(RULE_KEY))
                        .thenReturn(CompletableFuture.supplyAsync(() -> "some-value"));

                RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID,
                        "some-value", Set.of("before-value", "some-value", "after-value"));
            }

            @Test
            @DisplayName("evaluate as invalid if fact does not match")
            void factIsNotValid() {
                when(resolver.resolveFact(RULE_KEY))
                        .thenReturn(CompletableFuture.supplyAsync(() -> INVALID_FACT));

                RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID,
                        INVALID_FACT, Set.of("before-value", "some-value", "after-value"));
            }

            @Test
            @DisplayName("evaluate action on completion")
            void actionOnCompletion() {
                when(resolver.resolveFact(RULE_KEY))
                        .thenReturn(CompletableFuture.supplyAsync(() -> "some-value"));

                var integerAtomicReference = new AtomicInteger(0);
                var action = new Action().setOnCompletion((result, throwable, ruleResult) -> integerAtomicReference.getAndIncrement());
                rule.setAction(action);

                RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID,
                        "some-value", Set.of("before-value", "some-value", "after-value"));

                assertThat(integerAtomicReference.get()).isEqualTo(1);
            }

            @Test
            @DisplayName("evaluate action on completion with exception")
            void actionOnCompletionWithException() {
                when(resolver.resolveFact(RULE_KEY))
                        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("bla")));

                var throwableAtomicReference = new AtomicReference<Throwable>();
                var action = new Action().setOnCompletion((result, throwable, ruleResult) -> throwableAtomicReference.set(throwable));
                rule.setAction(action);

                RuleTestUtils.evaluateAndTestWithMessage(rule, resolver, ruleResultBuilder, Result.FAILED, "java.lang.RuntimeException: bla");

                assertThat(throwableAtomicReference.get()).hasMessageContaining("bla");
            }
        }

        @Nested
        @DisplayName("interests should")
        class Intersects {

            private ValuesRuleResult.RuleResultBuilder<String> ruleResultBuilder;

            @BeforeEach
            void setup() {
                rule.setOperator(Operator.INTERSECTS);
                rule.setValues(Set.of("before-value", "some-value", "after-value"));

                ruleResultBuilder = ValuesRuleResult.<String>builder().with(r -> {
                    r.type = RuleType.SET.getName();
                    r.operator = Operator.INTERSECTS;
                    r.key = RULE_KEY;
                    r.expectedValues = Set.of("before-value", "some-value", "after-value");
                });
            }

            @Test
            @DisplayName("evaluate as valid if one of the facts matches the set")
            void factIsValid() {
                when(resolver.resolveFact(RULE_KEY))
                        .thenReturn(CompletableFuture.supplyAsync(() -> Set.of("wrong-value", "some-value")));

                RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID,
                        Set.of("wrong-value", "some-value"), Set.of("before-value", "some-value", "after-value"));
            }

            @Test
            @DisplayName("evaluate as invalid if none of the facts matches the set")
            void factIsInvalid() {
                when(resolver.resolveFact(RULE_KEY))
                        .thenReturn(CompletableFuture.supplyAsync(() -> Set.of("wrong-value", "another-wrong-value")));

                RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID,
                        Set.of("wrong-value", "another-wrong-value"), Set.of("before-value", "some-value", "after-value"));
            }

            @Test
            @DisplayName("evaluate action on completion")
            void actionOnCompletion() {
                when(resolver.resolveFact(RULE_KEY))
                        .thenReturn(CompletableFuture.supplyAsync(() -> Set.of("wrong-value", "some-value")));

                var integerAtomicReference = new AtomicInteger(0);
                var action = new Action().setOnCompletion((result, throwable, ruleResult) -> integerAtomicReference.getAndIncrement());
                rule.setAction(action);

                RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID,
                        Set.of("wrong-value", "some-value"), Set.of("before-value", "some-value", "after-value"));

                assertThat(integerAtomicReference.get()).isEqualTo(1);
            }

            @Test
            @DisplayName("evaluate action on completion with exception")
            void actionOnCompletionWithException() {
                when(resolver.resolveFact(RULE_KEY))
                        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("bla")));

                var throwableAtomicReference = new AtomicReference<Throwable>();
                var action = new Action().setOnCompletion((result, throwable, ruleResult) -> throwableAtomicReference.set(throwable));
                rule.setAction(action);

                RuleTestUtils.evaluateAndTestWithMessage(rule, resolver, ruleResultBuilder, Result.FAILED, "java.lang.RuntimeException: bla");

                assertThat(throwableAtomicReference.get()).hasMessageContaining("bla");
            }

        }
    }

    @Nested
    @DisplayName("with Integer values")
    class WithIntegerValues {

        private static final String INTEGER_RULE_KEY = "integer_set_fact";
        private SetRule<Integer> integerRule;

        @BeforeEach
        void setup() {
            integerRule = new SetRule<>();
            integerRule.setKey(INTEGER_RULE_KEY);
        }

        @Test
        @DisplayName("evaluate IN as valid if the fact matches")
        void inFactIsValid() {
            integerRule.setOperator(Operator.IN);
            integerRule.setValues(Set.of(1, 2, 3));

            final var ruleResultBuilder = ValuesRuleResult.<Integer>builder().with(r -> {
                r.type = RuleType.SET.getName();
                r.operator = Operator.IN;
                r.key = INTEGER_RULE_KEY;
                r.expectedValues = Set.of(1, 2, 3);
            });
            when(resolver.resolveFact(INTEGER_RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> 2));

            RuleTestUtils.evaluateAndTest(integerRule, resolver, ruleResultBuilder, Result.VALID, 2, Set.of(1, 2, 3));
        }

        @Test
        @DisplayName("evaluate IN as invalid if the fact does not match")
        void inFactIsNotValid() {
            integerRule.setOperator(Operator.IN);
            integerRule.setValues(Set.of(1, 2, 3));

            final var ruleResultBuilder = ValuesRuleResult.<Integer>builder().with(r -> {
                r.type = RuleType.SET.getName();
                r.operator = Operator.IN;
                r.key = INTEGER_RULE_KEY;
                r.expectedValues = Set.of(1, 2, 3);
            });
            when(resolver.resolveFact(INTEGER_RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> 42));

            RuleTestUtils.evaluateAndTest(integerRule, resolver, ruleResultBuilder, Result.INVALID, 42, Set.of(1, 2, 3));
        }

        @Test
        @DisplayName("evaluate INTERSECTS as valid if one of the facts matches the set")
        void intersectsFactIsValid() {
            integerRule.setOperator(Operator.INTERSECTS);
            integerRule.setValues(Set.of(1, 2, 3));

            final var ruleResultBuilder = ValuesRuleResult.<Integer>builder().with(r -> {
                r.type = RuleType.SET.getName();
                r.operator = Operator.INTERSECTS;
                r.key = INTEGER_RULE_KEY;
                r.expectedValues = Set.of(1, 2, 3);
            });
            when(resolver.resolveFact(INTEGER_RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> Set.of(3, 99)));

            RuleTestUtils.evaluateAndTest(integerRule, resolver, ruleResultBuilder, Result.VALID,
                    Set.of(3, 99), Set.of(1, 2, 3));
        }

        @Test
        @DisplayName("evaluate INTERSECTS as invalid if none of the facts matches the set")
        void intersectsFactIsInvalid() {
            integerRule.setOperator(Operator.INTERSECTS);
            integerRule.setValues(Set.of(1, 2, 3));

            final var ruleResultBuilder = ValuesRuleResult.<Integer>builder().with(r -> {
                r.type = RuleType.SET.getName();
                r.operator = Operator.INTERSECTS;
                r.key = INTEGER_RULE_KEY;
                r.expectedValues = Set.of(1, 2, 3);
            });
            when(resolver.resolveFact(INTEGER_RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> Set.of(98, 99)));

            RuleTestUtils.evaluateAndTest(integerRule, resolver, ruleResultBuilder, Result.INVALID,
                    Set.of(98, 99), Set.of(1, 2, 3));
        }
    }

    @Nested
    @DisplayName("with Double values")
    class WithDoubleValues {

        private static final String DOUBLE_RULE_KEY = "double_set_fact";
        private SetRule<Double> doubleRule;

        @BeforeEach
        void setup() {
            doubleRule = new SetRule<>();
            doubleRule.setKey(DOUBLE_RULE_KEY);
        }

        @Test
        @DisplayName("evaluate IN as valid if the fact matches")
        void inFactIsValid() {
            doubleRule.setOperator(Operator.IN);
            doubleRule.setValues(Set.of(1.1, 2.2, 3.3));

            final var ruleResultBuilder = ValuesRuleResult.<Double>builder().with(r -> {
                r.type = RuleType.SET.getName();
                r.operator = Operator.IN;
                r.key = DOUBLE_RULE_KEY;
                r.expectedValues = Set.of(1.1, 2.2, 3.3);
            });
            when(resolver.resolveFact(DOUBLE_RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> 2.2));

            RuleTestUtils.evaluateAndTest(doubleRule, resolver, ruleResultBuilder, Result.VALID, 2.2, Set.of(1.1, 2.2, 3.3));
        }

        @Test
        @DisplayName("evaluate IN as invalid if the fact does not match")
        void inFactIsNotValid() {
            doubleRule.setOperator(Operator.IN);
            doubleRule.setValues(Set.of(1.1, 2.2, 3.3));

            final var ruleResultBuilder = ValuesRuleResult.<Double>builder().with(r -> {
                r.type = RuleType.SET.getName();
                r.operator = Operator.IN;
                r.key = DOUBLE_RULE_KEY;
                r.expectedValues = Set.of(1.1, 2.2, 3.3);
            });
            when(resolver.resolveFact(DOUBLE_RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> 4.4));

            RuleTestUtils.evaluateAndTest(doubleRule, resolver, ruleResultBuilder, Result.INVALID, 4.4, Set.of(1.1, 2.2, 3.3));
        }

        @Test
        @DisplayName("evaluate INTERSECTS as valid if one of the facts matches the set")
        void intersectsFactIsValid() {
            doubleRule.setOperator(Operator.INTERSECTS);
            doubleRule.setValues(Set.of(1.1, 2.2, 3.3));

            final var ruleResultBuilder = ValuesRuleResult.<Double>builder().with(r -> {
                r.type = RuleType.SET.getName();
                r.operator = Operator.INTERSECTS;
                r.key = DOUBLE_RULE_KEY;
                r.expectedValues = Set.of(1.1, 2.2, 3.3);
            });
            when(resolver.resolveFact(DOUBLE_RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> Set.of(3.3, 9.9)));

            RuleTestUtils.evaluateAndTest(doubleRule, resolver, ruleResultBuilder, Result.VALID,
                    Set.of(3.3, 9.9), Set.of(1.1, 2.2, 3.3));
        }

        @Test
        @DisplayName("evaluate INTERSECTS as invalid if none of the facts matches the set")
        void intersectsFactIsInvalid() {
            doubleRule.setOperator(Operator.INTERSECTS);
            doubleRule.setValues(Set.of(1.1, 2.2, 3.3));

            final var ruleResultBuilder = ValuesRuleResult.<Double>builder().with(r -> {
                r.type = RuleType.SET.getName();
                r.operator = Operator.INTERSECTS;
                r.key = DOUBLE_RULE_KEY;
                r.expectedValues = Set.of(1.1, 2.2, 3.3);
            });
            when(resolver.resolveFact(DOUBLE_RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> Set.of(8.8, 9.9)));

            RuleTestUtils.evaluateAndTest(doubleRule, resolver, ruleResultBuilder, Result.INVALID,
                    Set.of(8.8, 9.9), Set.of(1.1, 2.2, 3.3));
        }
    }

    @Nested
    @DisplayName("with Date values")
    class WithDateValues {

        private static final String DATE_RULE_KEY = "date_set_fact";
        private SetRule<OffsetDateTime> dateRule;

        private final OffsetDateTime day1 = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        private final OffsetDateTime day2 = OffsetDateTime.of(2024, 2, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        private final OffsetDateTime day3 = OffsetDateTime.of(2024, 3, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        private final OffsetDateTime otherDay = OffsetDateTime.of(2024, 4, 1, 0, 0, 0, 0, ZoneOffset.UTC);

        @BeforeEach
        void setup() {
            dateRule = new SetRule<>();
            dateRule.setKey(DATE_RULE_KEY);
        }

        @Test
        @DisplayName("evaluate IN as valid if the fact matches")
        void inFactIsValid() {
            dateRule.setOperator(Operator.IN);
            dateRule.setValues(Set.of(day1, day2, day3));

            final var ruleResultBuilder = ValuesRuleResult.<OffsetDateTime>builder().with(r -> {
                r.type = RuleType.SET.getName();
                r.operator = Operator.IN;
                r.key = DATE_RULE_KEY;
                r.expectedValues = Set.of(day1, day2, day3);
            });
            when(resolver.resolveFact(DATE_RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> day2));

            RuleTestUtils.evaluateAndTest(dateRule, resolver, ruleResultBuilder, Result.VALID, day2, Set.of(day1, day2, day3));
        }

        @Test
        @DisplayName("evaluate IN as invalid if the fact does not match")
        void inFactIsNotValid() {
            dateRule.setOperator(Operator.IN);
            dateRule.setValues(Set.of(day1, day2, day3));

            final var ruleResultBuilder = ValuesRuleResult.<OffsetDateTime>builder().with(r -> {
                r.type = RuleType.SET.getName();
                r.operator = Operator.IN;
                r.key = DATE_RULE_KEY;
                r.expectedValues = Set.of(day1, day2, day3);
            });
            when(resolver.resolveFact(DATE_RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> otherDay));

            RuleTestUtils.evaluateAndTest(dateRule, resolver, ruleResultBuilder, Result.INVALID, otherDay, Set.of(day1, day2, day3));
        }

        @Test
        @DisplayName("evaluate INTERSECTS as valid if one of the facts matches the set")
        void intersectsFactIsValid() {
            dateRule.setOperator(Operator.INTERSECTS);
            dateRule.setValues(Set.of(day1, day2, day3));

            final var ruleResultBuilder = ValuesRuleResult.<OffsetDateTime>builder().with(r -> {
                r.type = RuleType.SET.getName();
                r.operator = Operator.INTERSECTS;
                r.key = DATE_RULE_KEY;
                r.expectedValues = Set.of(day1, day2, day3);
            });
            when(resolver.resolveFact(DATE_RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> Set.of(day3, otherDay)));

            RuleTestUtils.evaluateAndTest(dateRule, resolver, ruleResultBuilder, Result.VALID,
                    Set.of(day3, otherDay), Set.of(day1, day2, day3));
        }

        @Test
        @DisplayName("evaluate INTERSECTS as invalid if none of the facts matches the set")
        void intersectsFactIsInvalid() {
            dateRule.setOperator(Operator.INTERSECTS);
            dateRule.setValues(Set.of(day1, day2, day3));

            final var ruleResultBuilder = ValuesRuleResult.<OffsetDateTime>builder().with(r -> {
                r.type = RuleType.SET.getName();
                r.operator = Operator.INTERSECTS;
                r.key = DATE_RULE_KEY;
                r.expectedValues = Set.of(day1, day2, day3);
            });
            when(resolver.resolveFact(DATE_RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> Set.of(otherDay)));

            RuleTestUtils.evaluateAndTest(dateRule, resolver, ruleResultBuilder, Result.INVALID,
                    Set.of(otherDay), Set.of(day1, day2, day3));
        }
    }
}
