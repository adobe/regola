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
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
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

@DisplayName("Testing NullRule should")
class NullRuleTest {

    private NullRule rule;
    private final FactsResolver resolver = mock(FactsResolver.class);

    private static final String RULE_KEY = "some_fact";

    private ValuesRuleResult.RuleResultBuilder<Object> ruleResultBuilder;

    @BeforeEach
    void setup() {
        rule = new NullRule();
        rule.setKey(RULE_KEY);

        ruleResultBuilder = ValuesRuleResult.builder().with(r -> {
            r.type = RuleType.NULL.getName();
            r.key = RULE_KEY;
        });
    }

    @Test
    @DisplayName("have the expected NULL type")
    void ruleHasExpectedType() {
        assertThat(rule.getType()).isEqualTo("NULL");
    }

    @Test
    @DisplayName("evaluate as valid if fact is null")
    void factIsValid() {
        when(resolver.resolveFact(RULE_KEY))
                .thenReturn(CompletableFuture.supplyAsync(() -> null));

        RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID);
    }

    @Test
    @DisplayName("evaluate as invalid if fact is not null")
    void factIsNotValid() {
        when(resolver.resolveFact(RULE_KEY))
                .thenReturn(CompletableFuture.supplyAsync(() -> "not null"));

        RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID, "not null", (String) null);
    }

    @Test
    @DisplayName("evaluate as invalid if fact is non null set")
    void factIsNotValidSet() {
        when(resolver.resolveFact(RULE_KEY))
                .thenReturn(CompletableFuture.supplyAsync(() -> Set.of("not null")));

        RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID, (Collection<String>) Set.of("not null"), (String) null);
    }

    @Test
    @DisplayName("evaluate as invalid if fact cannot be resolved")
    void factIsFailed() {
        Throwable throwable = new IllegalArgumentException("Failed to fetch fact");
        when(resolver.resolveFact(RULE_KEY))
                .thenReturn(CompletableFuture.failedFuture(throwable));

        final var snapshot = RuleTestUtils.evaluateAndTestWithMessage(rule, resolver, ruleResultBuilder, Result.FAILED,
                "java.lang.IllegalArgumentException: Failed to fetch fact");
        assertThat(snapshot.getCause())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to fetch fact");
    }

    @Test
    @DisplayName("evaluate and return description in result")
    void descriptionInResult() {
        rule.setDescription("Fact should be null");
        when(resolver.resolveFact(RULE_KEY))
                .thenReturn(CompletableFuture.supplyAsync(() -> null));
        ruleResultBuilder = ruleResultBuilder.with(r -> r.description = "Fact should be null");

        RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID);
    }

    @Test
    @DisplayName("evaluate action on completion")
    void actionOnCompletion() {
        when(resolver.resolveFact(RULE_KEY))
                .thenReturn(CompletableFuture.supplyAsync(() -> null));

        var integerAtomicReference = new AtomicInteger(0);
        var action = new Action().setOnCompletion((result, throwable, ruleResult) -> integerAtomicReference.getAndIncrement());
        rule.setAction(action);

        RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID);

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

    @Nested
    @DisplayName("with custom executor should")
    class WithCustomExecutor {
        private final Executor executor = Executors.newCachedThreadPool();

        @BeforeEach
        void setup() {
            rule = new NullRule(executor);
            rule.setKey(RULE_KEY);

            ruleResultBuilder = ValuesRuleResult.builder().with(r -> {
                r.type = RuleType.NULL.getName();
                r.key = RULE_KEY;
            });
        }

        @Test
        @DisplayName("evaluate as valid if fact is null")
        void factIsValid() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> null));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID);
        }

        @Test
        @DisplayName("evaluate as invalid if fact is not null")
        void factIsNotValid() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> "not null"));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID, "not null", (String) null);
        }
    }
}
