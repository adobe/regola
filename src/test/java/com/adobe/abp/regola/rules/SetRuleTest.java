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
}
