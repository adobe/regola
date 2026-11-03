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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rule whose evaluation never completes on its own and records whether it was started or cancelled.
 */
@SuppressWarnings("this-escape")
public class PendingRule extends KeyBasedRule {

    private final AtomicInteger starts = new AtomicInteger();
    private final AtomicBoolean cancelled = new AtomicBoolean();

    public PendingRule(String key) {
        super("PENDING_RULE");
        setKey(key);
    }

    public int getStarts() {
        return starts.get();
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    @Override
    public EvaluationResult evaluate(FactsResolver factsResolver) {
        return new EvaluationResult() {
            private final CompletableFuture<Result> status = new CompletableFuture<>();

            @Override
            public RuleResult snapshot() {
                return ValuesRuleResult.builder().with(r -> {
                    r.type = getType();
                    r.key = getKey();
                    r.result = Result.MAYBE;
                    r.ignored = isIgnore();
                }).build();
            }

            @Override
            public CompletableFuture<Result> status() {
                starts.incrementAndGet();
                return status;
            }

            @Override
            public void cancel() {
                cancelled.set(true);
                status.cancel(false);
            }
        };
    }
}
