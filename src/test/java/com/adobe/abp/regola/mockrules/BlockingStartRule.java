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
import java.util.concurrent.CountDownLatch;

/**
 * A rule whose {@code status()} signals that it is starting and then blocks until released, so that
 * a test can act while the evaluation that starts it is still starting its subrules. Once released,
 * it stays pending until cancelled.
 */
@SuppressWarnings("this-escape")
public class BlockingStartRule extends KeyBasedRule {

    private final CountDownLatch starting;
    private final CountDownLatch release;

    public BlockingStartRule(String key, CountDownLatch starting, CountDownLatch release) {
        super("BLOCKING_START_RULE");
        setKey(key);
        this.starting = starting;
        this.release = release;
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
                starting.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    status.completeExceptionally(e);
                }
                return status;
            }

            @Override
            public void cancel() {
                status.cancel(false);
            }
        };
    }
}
