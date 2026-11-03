/*
 *  Copyright 2026 Adobe. All rights reserved.
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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LeafRuleCancellationTest {

    private static final String KEY = "key";

    static Stream<Arguments> leafRules() {
        return Stream.of(
                Arguments.of("StringRule EQUALS", (Supplier<Rule>) () -> new StringRule(KEY, Operator.EQUALS, "value")),
                Arguments.of("StringRule CONTAINS", (Supplier<Rule>) () -> new StringRule(KEY, Operator.CONTAINS, "value")),
                Arguments.of("SetRule IN", (Supplier<Rule>) () -> new SetRule<String>()
                        .setValues(Set.of("value"))
                        .setOperator(Operator.IN)
                        .setKey(KEY)),
                Arguments.of("RangeRule BETWEEN", (Supplier<Rule>) () -> new RangeRule<Integer>()
                        .setMin(1)
                        .setMax(10)
                        .setOperator(Operator.BETWEEN)
                        .setKey(KEY)),
                Arguments.of("ExistsRule", (Supplier<Rule>) () -> new ExistsRule(KEY)),
                Arguments.of("NullRule", (Supplier<Rule>) () -> new NullRule(KEY)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("leafRules")
    @DisplayName("should cancel the pending fact, keep MAYBE and not run its action when cancelled")
    void cancelPendingLeafRule(String name, Supplier<Rule> ruleSupplier) {
        final var fact = new CompletableFuture<Object>();
        final var resolver = mock(FactsResolver.class);
        when(resolver.resolveFact(KEY)).thenReturn(fact);
        final var actionCalls = new AtomicInteger();
        final var rule = ruleSupplier.get();
        rule.setAction(new Action().setOnCompletion((result, throwable, ruleResult) -> actionCalls.incrementAndGet()));

        final var evaluationResult = rule.evaluate(resolver);
        final var status = evaluationResult.status();
        evaluationResult.cancel();

        assertThat(fact).isCancelled();
        assertThat(status).isCancelled();
        assertThat(evaluationResult.snapshot().getResult()).isEqualTo(Result.MAYBE);
        assertThat(actionCalls).hasValue(0);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("leafRules")
    @DisplayName("should not resolve the fact when cancelled before being started")
    void cancelBeforeStart(String name, Supplier<Rule> ruleSupplier) {
        final var resolver = mock(FactsResolver.class);
        final var rule = ruleSupplier.get();

        final var evaluationResult = rule.evaluate(resolver);
        evaluationResult.cancel();

        assertThat(evaluationResult.status()).isCancelled();
        assertThat(evaluationResult.snapshot().getResult()).isEqualTo(Result.MAYBE);
        verifyNoInteractions(resolver);
    }
}
