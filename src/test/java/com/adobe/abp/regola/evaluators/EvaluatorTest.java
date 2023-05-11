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

package com.adobe.abp.regola.evaluators;

import com.adobe.abp.regola.RulesEvaluator;
import com.adobe.abp.regola.facts.FactsResolver;
import com.adobe.abp.regola.results.Result;
import com.adobe.abp.regola.results.RuleResult;
import com.adobe.abp.regola.rules.EvaluationResult;
import com.adobe.abp.regola.rules.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EvaluatorTest {

    private final FactsResolver factsResolver = mock(FactsResolver.class);
    private final Rule rule = mock(Rule.class);

    private final RuleResult ruleResult = mock(RuleResult.class);

    @BeforeEach
    void setup() {
        when(rule.evaluate(factsResolver))
                .thenReturn(new MockEvaluationResult());
    }

    private class MockEvaluationResult implements EvaluationResult {

        @Override
        public synchronized RuleResult snapshot() {
            return ruleResult;
        }

        @Override
        public CompletableFuture<Result> status() {
            return CompletableFuture.supplyAsync(() -> Result.VALID);
        }
    }

    @Test
    @DisplayName("should prefetch facts and then evaluate rule")
    void evaluateRule() {
        RulesEvaluator evaluator = new Evaluator();

        evaluator.evaluate(rule, factsResolver);

        verify(rule).evaluate(factsResolver);
    }
}