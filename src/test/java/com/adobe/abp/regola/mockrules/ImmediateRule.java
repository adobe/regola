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

package com.adobe.abp.regola.mockrules;

import com.adobe.abp.regola.facts.FactsResolver;
import com.adobe.abp.regola.results.Result;
import com.adobe.abp.regola.results.RuleResult;
import com.adobe.abp.regola.results.ValuesRuleResult;
import com.adobe.abp.regola.rules.EvaluationResult;
import com.adobe.abp.regola.rules.KeyBasedRule;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rule that completes synchronously, on the thread calling {@code status()}, and counts how many
 * times its evaluation was started.
 */
@SuppressWarnings("this-escape")
public class ImmediateRule extends KeyBasedRule {

    private final Result finalResult;
    private final AtomicInteger starts = new AtomicInteger();

    public ImmediateRule(String key, Result finalResult) {
        super("IMMEDIATE_RULE");
        setKey(key);
        this.finalResult = finalResult;
    }

    public int getStarts() {
        return starts.get();
    }

    @Override
    public EvaluationResult evaluate(FactsResolver factsResolver) {
        return new EvaluationResult() {
            private volatile Result result = Result.MAYBE;

            @Override
            public RuleResult snapshot() {
                return ValuesRuleResult.builder().with(r -> {
                    r.type = getType();
                    r.key = getKey();
                    r.result = result;
                    r.ignored = isIgnore();
                }).build();
            }

            @Override
            public CompletableFuture<Result> status() {
                starts.incrementAndGet();
                result = finalResult;
                return CompletableFuture.completedFuture(result);
            }
        };
    }
}
