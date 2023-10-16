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
 * A rule that checks if a fact exists between two numbers with various inclusivity options.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RangeRule extends KeyBasedRule {

    private final Number min;
    private final Number max;
    private final boolean inclusiveMin;
    private final boolean inclusiveMax;
    private final Executor executor;

    public RangeRule(String key, Number min, Number max) {
        this(key, min, max, true, true); // Default to inclusive for both min and max
    }

    public RangeRule(String key, Number min, Number max, boolean inclusiveMin, boolean inclusiveMax) {
        this(key, min, max, inclusiveMin, inclusiveMax, null);
    }

    public RangeRule(String key, Number min, Number max, boolean inclusiveMin, boolean inclusiveMax, Executor executor) {
        super(RuleType.RANGE.getName());
        this.setKey(key);
        this.min = min;
        this.max = max;
        this.inclusiveMin = inclusiveMin;
        this.inclusiveMax = inclusiveMax;
        this.executor = executor;
    }

    @Override
    public EvaluationResult evaluate(FactsResolver factsResolver) {
        return new EvaluationResult() {
            private Result result = Result.MAYBE;
            private Number evaluatedFact;
            private String message;
            private Throwable cause;

            @Override
            public RuleResult snapshot() {
                return ValuesRuleResult.builder().with(r -> {
                    r.type = getType();
                    r.key = getKey();
                    r.description = getDescription();
                    r.result = result;
                    r.expectedValue = getExpectedValue();
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
                            if (fact instanceof Number) {
                                Number value = (Number) fact;
                                if (isValidRange(value)) {
                                    result = Result.VALID;
                                } else {
                                    result = Result.INVALID;
                                    message = "Value is outside the range " + getExpectedValue();
                                }
                                evaluatedFact = value;
                            } else {
                                result = Result.INVALID;
                                message = "Fact is not a Number";
                            }
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

            private String getExpectedValue() {
                StringBuilder expectedValue = new StringBuilder();
                if (inclusiveMin) {
                    expectedValue.append('[');
                } else {
                    expectedValue.append('(');
                }
                expectedValue.append(min).append(", ").append(max);
                if (inclusiveMax) {
                    expectedValue.append(']');
                } else {
                    expectedValue.append(')');
                }
                return expectedValue.toString();
            }

            private boolean isValidRange(Number value) {
                if (inclusiveMin) {
                    if (inclusiveMax) {
                        return value.doubleValue() >= min.doubleValue() && value.doubleValue() <= max.doubleValue(); // [min, max]
                    } else {
                        return value.doubleValue() >= min.doubleValue() && value.doubleValue() < max.doubleValue();  // [min, max)
                    }
                } else {
                    if (inclusiveMax) {
                        return value.doubleValue() > min.doubleValue() && value.doubleValue() <= max.doubleValue();  // (min, max]
                    } else {
                        return value.doubleValue() > min.doubleValue() && value.doubleValue() < max.doubleValue();   // (min, max)
                    }
                }
            }
        };
    }
}
