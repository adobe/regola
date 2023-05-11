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
import com.adobe.abp.regola.results.RuleResult;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("Testing ConstantRule should")
class ConstantRuleTest {

    private final FactsResolver resolver = mock(FactsResolver.class);

    @Test
    @DisplayName("have the expected CONSTANT type")
    void ruleHasExpectedType() {
        ConstantRule rule = new ConstantRule(Result.VALID);

        assertThat(rule.getType()).isEqualTo("CONSTANT");
    }

    @ParameterizedTest
    @EnumSource(Result.class)
    @DisplayName("evaluate to the result specified")
    void evaluateToGivenResult(Result result) {
        ConstantRule rule = new ConstantRule(result);
        rule.setDescription("This rule will always evaluate to the same result");

        final var ruleResultBuilder = RuleResult.baseBuilder().with(r -> {
            r.type = RuleType.CONSTANT.getName();
            r.description = "This rule will always evaluate to the same result";
        });

        RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, result);
    }

    @Test
    @DisplayName("evaluate to VALID by default")
    void evaluateToGivenResult() {
        ConstantRule rule = new ConstantRule();

        final var ruleResultBuilder = RuleResult.baseBuilder().with(r -> {
            r.type = RuleType.CONSTANT.getName();
        });

        RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID);
    }

    @Test
    @DisplayName("evaluate action on completion")
    void actionOnCompletion() {
        ConstantRule rule = new ConstantRule(Result.VALID);

        var integerAtomicReference = new AtomicInteger(0);
        var action = new Action().setOnCompletion((result, throwable, ruleResult) -> integerAtomicReference.getAndIncrement());
        rule.setAction(action);

        final var ruleResultBuilder = RuleResult.baseBuilder().with(r -> {
            r.type = RuleType.CONSTANT.getName();
        });
        RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID);

        assertThat(integerAtomicReference.get()).isEqualTo(1);
    }
}
