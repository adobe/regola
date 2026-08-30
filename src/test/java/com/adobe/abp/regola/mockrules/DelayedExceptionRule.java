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

package com.adobe.abp.regola.mockrules;

import com.adobe.abp.regola.facts.FactsResolver;
import com.adobe.abp.regola.results.Result;
import com.adobe.abp.regola.results.RuleResult;
import com.adobe.abp.regola.results.ValuesRuleResult;
import com.adobe.abp.regola.rules.EvaluationResult;
import com.adobe.abp.regola.rules.KeyBasedRule;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;

@SuppressWarnings("this-escape")
public class DelayedExceptionRule extends KeyBasedRule {

    private final long delayMillis;

    public DelayedExceptionRule(String key, long delayMillis) {
        super("DELAYED_EXCEPTION_RULE");
        setKey(key);
        this.delayMillis = delayMillis;
    }

    @Override
    public EvaluationResult evaluate(FactsResolver factsResolver) {
        return new EvaluationResult() {
            private final ReentrantLock lock = new ReentrantLock();
            private Result result = Result.MAYBE;

            @Override
            public RuleResult snapshot() {
                lock.lock();
                try {
                    return ValuesRuleResult.builder().with(r -> {
                        r.type = getType();
                        r.key = getKey();
                        r.result = result;
                        r.ignored = isIgnore();
                    }).build();
                } finally {
                    lock.unlock();
                }
            }

            @Override
            public CompletableFuture<Result> status() {
                return CompletableFuture.supplyAsync(() -> {
                    try {
                        Thread.sleep(delayMillis);
                        lock.lock();
                        try {
                            result = Result.FAILED;
                        } finally {
                            lock.unlock();
                        }
                        throw new RuntimeException("Intentionally failing this rule with an exception");
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        };

    }
}
