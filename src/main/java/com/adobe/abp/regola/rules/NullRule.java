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

import com.adobe.abp.regola.facts.FactsResolver;
import com.adobe.abp.regola.results.Result;
import com.adobe.abp.regola.results.RuleResult;
import com.adobe.abp.regola.results.ValuesRuleResult;
import com.adobe.abp.regola.utils.futures.FutureUtils;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * A rule that checks if a fact is null.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NullRule extends KeyBasedRule {

    private final Executor executor;

    public NullRule() {
        this((Executor) null);
    }

    public NullRule(String key) {
        this(null, key);
    }

    public NullRule(Executor executor) {
        super(RuleType.NULL.getName());
        this.executor = executor;
    }

    public NullRule(Executor executor, String key) {
        this(executor);
        setKey(key);
    }

    @Override
    public EvaluationResult evaluate(FactsResolver factsResolver) {
        return new EvaluationResult() {
            private Result result = Result.MAYBE;
            private Object evaluatedFact;
            private String message;
            private Throwable cause;

            @Override
            public RuleResult snapshot() {
                return ValuesRuleResult.builder().with(r -> {
                    r.type = getType();
                    r.key = getKey();
                    r.description = getDescription();
                    r.result = result;
                    r.expectedValue = null;
                    r.actualValue = evaluatedFact;
                    r.message = message;
                    r.cause = cause;
                    r.ignored = isIgnore();
                }).build();
            }

            @Override
            public CompletableFuture<Result> status() {
                return factsResolver.resolveFact(getKey())
                        .thenCompose(fact -> FutureUtils.supplyAsync(() -> {
                            result = Optional.ofNullable(fact)
                                    .map(f -> Result.INVALID)
                                    .orElse(Result.VALID);
                            evaluatedFact = fact;
                            return result;
                        }, executor))
                        .whenComplete((result, throwable) -> Optional.ofNullable(getAction())
                                .ifPresent(action -> action.onCompletion(result, throwable, snapshot())))
                        .exceptionally(throwable -> {
                            result = Result.FAILED;
                            message = throwable.getMessage();
                            cause = throwable;
                            return result;
                        });
            }
        };
    }
}
