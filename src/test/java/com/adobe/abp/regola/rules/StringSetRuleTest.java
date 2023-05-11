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

@DisplayName("Testing SetRule<String> with operator")
class StringSetRuleTest {

    private SetRule<String> rule;
    private final FactsResolver resolver = mock(FactsResolver.class);

    private static final String RULE_KEY = "string_set_fact";
    private static final String INVALID_FACT = "invalid-fact";

    @BeforeEach
    void setup() {
        rule = new SetRule<>();
        rule.setKey(RULE_KEY);
    }

    @Test
    @DisplayName("have the expected SET type")
    void ruleHasExpectedType() {
        assertThat(rule.getType()).isEqualTo("SET");
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

        @Nested
        @DisplayName("for a fact that is a set")
        class FactIsASet {

            @Test
            @DisplayName("evaluate as valid if all values in set fact match")
            void factIsValid() {
                when(resolver.resolveFact(RULE_KEY))
                        .thenReturn(CompletableFuture.supplyAsync(() -> Set.of("some-value", "before-value")));

                RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID,
                        Set.of("some-value", "before-value"), Set.of("before-value", "some-value", "after-value"));
            }

            @Test
            @DisplayName("evaluate as invalid if set fact is empty and expected set is not")
            void emptyFactIsInvalid() {
                when(resolver.resolveFact(RULE_KEY))
                        .thenReturn(CompletableFuture.supplyAsync(Set::of));

                RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID,
                        Set.of(), Set.of("before-value", "some-value", "after-value"));
            }

            @Test
            @DisplayName("evaluate as valid if set fact is empty and expected set is also empty")
            void emptyFactIsValid() {
                rule.setValues(Set.of());

                when(resolver.resolveFact(RULE_KEY))
                        .thenReturn(CompletableFuture.supplyAsync(Set::of));

                RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID,
                        Set.of(), Set.of());
            }

            @Test
            @DisplayName("evaluate as invalid if set fact exists but expected set is also empty")
            void emptyRuleButSomeFactIsInvalid() {
                rule.setValues(Set.of());

                when(resolver.resolveFact(RULE_KEY))
                        .thenReturn(CompletableFuture.supplyAsync(() -> Set.of("some-value", "before-value")));

                RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID,
                        Set.of("some-value", "before-value"), Set.of());
            }

            @Test
            @DisplayName("evaluate as invalid if one of the values in the set fact does not match")
            void factIsNotValid() {
                when(resolver.resolveFact(RULE_KEY))
                        .thenReturn(CompletableFuture.supplyAsync(() -> Set.of("some-value", "bad-value")));

                RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID,
                        Set.of("some-value", "bad-value"), Set.of("before-value", "some-value", "after-value"));
            }
        }

        @Nested
        @DisplayName("for a fact that is a list")
        class FactIsAList {

            @Test
            @DisplayName("evaluate as valid if all values in list fact match")
            void factIsValid() {
                when(resolver.resolveFact(RULE_KEY))
                        .thenReturn(CompletableFuture.supplyAsync(() -> List.of("some-value", "before-value", "before-value", "before-value")));

                RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID,
                        List.of("some-value", "before-value", "before-value", "before-value"), Set.of("before-value", "some-value", "after-value"));
            }

            @Test
            @DisplayName("evaluate as invalid if list fact is empty and expected set is not")
            void emptyFactIsInvalid() {
                when(resolver.resolveFact(RULE_KEY))
                        .thenReturn(CompletableFuture.supplyAsync(List::of));

                RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID,
                        List.of(), Set.of("before-value", "some-value", "after-value"));
            }

            @Test
            @DisplayName("evaluate as valid if list fact is empty and expected set is also empty")
            void emptyFactIsValid() {
                rule.setValues(Set.of());

                when(resolver.resolveFact(RULE_KEY))
                        .thenReturn(CompletableFuture.supplyAsync(List::of));

                RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID,
                        List.of(), Set.of());
            }

            @Test
            @DisplayName("evaluate as invalid if set fact exists but expected set is also empty")
            void emptyRuleButSomeFactIsInvalid() {
                rule.setValues(Set.of());

                when(resolver.resolveFact(RULE_KEY))
                        .thenReturn(CompletableFuture.supplyAsync(() -> List.of("some-value", "before-value")));

                RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID,
                        List.of("some-value", "before-value"), Set.of());
            }

            @Test
            @DisplayName("evaluate as invalid if one of the values in the list fact does not match")
            void factIsNotValid() {
                when(resolver.resolveFact(RULE_KEY))
                        .thenReturn(CompletableFuture.supplyAsync(() -> List.of("some-value", "bad-value")));

                RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID,
                        List.of("some-value", "bad-value"), Set.of("before-value", "some-value", "after-value"));
            }
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

        @Nested
        @DisplayName("for a fact that is a set")
        class FactIsASet {

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
            @DisplayName("evaluate as invalid if facts is an empty set")
            void factIsInvalidOnEmptyFactSet() {
                when(resolver.resolveFact(RULE_KEY))
                        .thenReturn(CompletableFuture.supplyAsync(Set::of));

                RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID,
                        Set.of(), Set.of("before-value", "some-value", "after-value"));
            }

            @Test
            @DisplayName("evaluate as invalid if rule's values and facts are both empty set")
            void factIsInvalidOnEmptyRuleAndFactSet() {
                rule.setValues(Set.of());
                when(resolver.resolveFact(RULE_KEY))
                        .thenReturn(CompletableFuture.supplyAsync(Set::of));

                RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID, Set.of(), Set.of());
            }
        }

        @Nested
        @DisplayName("for a fact that is a list")
        class FactIsAList {

            @Test
            @DisplayName("evaluate as valid if one of the facts matches the list")
            void factIsValid() {
                when(resolver.resolveFact(RULE_KEY))
                        .thenReturn(CompletableFuture.supplyAsync(() -> List.of("wrong-value", "some-value", "some-value", "some-value")));

                RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID,
                        List.of("wrong-value", "some-value", "some-value", "some-value"), Set.of("before-value", "some-value", "after-value"));
            }

            @Test
            @DisplayName("evaluate as invalid if none of the facts matches the list")
            void factIsInvalid() {
                when(resolver.resolveFact(RULE_KEY))
                        .thenReturn(CompletableFuture.supplyAsync(() -> List.of("wrong-value", "another-wrong-value")));

                RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID,
                        List.of("wrong-value", "another-wrong-value"), Set.of("before-value", "some-value", "after-value"));
            }

            @Test
            @DisplayName("evaluate as invalid if facts is an empty list")
            void factIsInvalidOnEmptyFactSet() {
                when(resolver.resolveFact(RULE_KEY))
                        .thenReturn(CompletableFuture.supplyAsync(List::of));

                RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID,
                        List.of(), Set.of("before-value", "some-value", "after-value"));
            }

            @Test
            @DisplayName("evaluate as invalid if rule's values and facts are both empty list")
            void factIsInvalidOnEmptyRuleAndFactSet() {
                rule.setValues(Set.of());
                when(resolver.resolveFact(RULE_KEY))
                        .thenReturn(CompletableFuture.supplyAsync(List::of));

                RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID, List.of(), Set.of());
            }
        }
    }

    @Nested
    @DisplayName("not-supported should")
    class NotSupported {
        private ValuesRuleResult.RuleResultBuilder<String> ruleResultBuilder;

        void setup(Operator operator) {
            rule.setOperator(operator);
            rule.setValues(Set.of("before-value", "some-value", "after-value"));

            ruleResultBuilder = ValuesRuleResult.<String>builder().with(r -> {
                r.type = RuleType.SET.getName();
                r.operator = operator;
                r.key = RULE_KEY;
                r.expectedValues = Set.of("before-value", "some-value", "after-value");
            });
        }

        @ParameterizedTest
        @EnumSource(value = Operator.class,
                names = {"IN", "INTERSECTS"},
                mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("evaluate as not supported")
        void factIsNotValidOnEmptyFact(Operator operator) {
            setup(operator);

            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> "some-value"));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.OPERATION_NOT_SUPPORTED);
        }
    }

    @Nested
    @DisplayName("any-supported-rule should")
    class GenericTests {
        private ValuesRuleResult.RuleResultBuilder<String> ruleResultBuilder;

        void setup(Operator operator) {
            rule.setOperator(operator);
            rule.setValues(Set.of("some-value"));

            ruleResultBuilder = ValuesRuleResult.<String>builder().with(r -> {
                r.type = RuleType.SET.getName();
                r.operator = operator;
                r.key = RULE_KEY;
                r.expectedValues = Set.of("some-value");
            });
        }

        @ParameterizedTest
        @EnumSource(value = Operator.class, names = {"IN"})
        @DisplayName("evaluate as invalid if fact is null")
        void factIsNotValidOnNullFact(Operator operator) {
            setup(operator);

            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> null));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID);
        }

        @ParameterizedTest
        @EnumSource(value = Operator.class, names = {"IN"})
        @DisplayName("evaluate as invalid if rule has an empty value and fact is null")
        void emptyValueButFactIsNotValidOnNullFact(Operator operator) {
            setup(operator);

            rule.setValues(Set.of());
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> null));

            RuleTestUtils.evaluateAndTestWithValues(rule, resolver, ruleResultBuilder, Result.INVALID, Set.of());
        }

        @ParameterizedTest
        @EnumSource(value = Operator.class, names = {"IN"})
        @DisplayName("evaluate as invalid if rule has an empty-string value and fact is null")
        void emptyStringValueButFactIsNotValidOnNullFact(Operator operator) {
            setup(operator);

            rule.setValues(Set.of(""));
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> null));

            RuleTestUtils.evaluateAndTestWithValues(rule, resolver, ruleResultBuilder, Result.INVALID, Set.of(""));
        }

        @ParameterizedTest
        @EnumSource(value = Operator.class, names = {"IN"})
        @DisplayName("evaluate as invalid if fact cannot be resolved")
        void factIsFailed(Operator operator) {
            setup(operator);

            Throwable throwable = new IllegalArgumentException("Failed to fetch fact");
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.failedFuture(throwable));

            final var snapshot = RuleTestUtils.evaluateAndTestWithMessage(rule, resolver, ruleResultBuilder, Result.FAILED,
                    "java.lang.IllegalArgumentException: Failed to fetch fact", Set.of("some-value"));
            assertThat(snapshot.getCause())
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Failed to fetch fact");
        }
    }
}
