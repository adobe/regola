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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Testing NumberRule<V> with custom executor")
class GenericNumberRuleTest {

    private NumberRule<Integer> rule;
    private final FactsResolver resolver = mock(FactsResolver.class);

    private static final String RULE_KEY = "number_fact";

    private ValuesRuleResult.RuleResultBuilder<Integer> ruleResultBuilder;
    private final Executor executor = Executors.newCachedThreadPool();

    void setup(Operator operator) {
        rule = new NumberRule<>(executor);
        rule.setKey(RULE_KEY);
        rule.setOperator(operator);
        rule.setValue(1);

        ruleResultBuilder = ValuesRuleResult.<Integer>builder().with(r -> {
            r.type = RuleType.NUMBER.getName();
            r.operator = operator;
            r.key = RULE_KEY;
            r.expectedValue = 1;
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
                .thenReturn(CompletableFuture.supplyAsync(() -> 1));

        RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.OPERATION_NOT_SUPPORTED);
    }

    @Test
    @DisplayName("evaluate and return description in result")
    void descriptionInResult() {
        setup(Operator.EQUALS);
        rule.setDescription("Number should match");
        when(resolver.resolveFact(RULE_KEY))
                .thenReturn(CompletableFuture.supplyAsync(() -> 1));
        ruleResultBuilder = ruleResultBuilder.with(r -> r.description = "Number should match");

        RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID, 1, 1);
    }

    @Test
    @DisplayName("evaluate action on completion")
    void actionOnCompletion() {
        setup(Operator.EQUALS);
        when(resolver.resolveFact(RULE_KEY))
                .thenReturn(CompletableFuture.supplyAsync(() -> 1));

        var integerAtomicReference = new AtomicInteger(0);
        var action = new Action().setOnCompletion((result, throwable, ruleResult) -> integerAtomicReference.getAndIncrement());
        rule.setAction(action);

        RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID, 1, 1);

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
