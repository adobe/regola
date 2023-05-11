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
import com.adobe.abp.regola.results.UnaryBooleanRuleResult;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * A rule that returns the inverse result of the subrule.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotRule extends UnaryBooleanRule {

    public NotRule() {
        super(RuleType.NOT.getName());
    }

    public NotRule(Rule rule) {
        this();
        setRule(rule);
    }

    @Override
    public EvaluationResult evaluate(FactsResolver factsResolver) {
        return new EvaluationResult() {

            private Result result = Result.MAYBE;
            private final CompletableFuture<Result> status = new CompletableFuture<>();
            private EvaluationResult notResult;

            @Override
            public RuleResult snapshot() {
                return UnaryBooleanRuleResult.builder().with(r -> {
                    r.type = getType();
                    r.description = getDescription();
                    r.result = result;
                    r.rule = Optional.ofNullable(notResult)
                            .map(EvaluationResult::snapshot)
                            .orElse(null);
                    r.ignored = isIgnore();
                }).build();
            }

            @Override
            public CompletableFuture<Result> status() {
                notResult = Optional.ofNullable(getRule())
                        .map(this::evaluateRule)
                        .orElse(null);
                return status
                        .whenComplete((result, throwable) -> Optional.ofNullable(getAction())
                                .ifPresent(action -> action.onCompletion(result, throwable, snapshot())));
            }

            private EvaluationResult evaluateRule(Rule rule) {
                final var evaluationResult = rule.evaluate(factsResolver);
                evaluationResult.status()
                        .whenComplete((result, throwable) -> evaluateSubresult(result, throwable, rule.isIgnore()));
                return evaluationResult;
            }

            private synchronized void evaluateSubresult(Result subresult, Throwable throwable, boolean ignore) {
                if (!status.isDone()) {
                    if (!ignore && throwable != null) {
                        status.completeExceptionally(throwable);
                        result = Result.FAILED;
                    } else if (ignore) {
                        // Whatever the subresult, mark this as VALID
                        result = Result.VALID;
                        status.complete(result);
                    } else if (subresult == Result.FAILED ||
                            subresult == Result.OPERATION_NOT_SUPPORTED ||
                            subresult == Result.MAYBE) {
                        result = subresult;
                        status.complete(result);
                    } else if (subresult == Result.VALID) {
                        result = Result.INVALID;
                        status.complete(result);
                    } else if (subresult == Result.INVALID) {
                        result = Result.VALID;
                        status.complete(result);
                    }
                }
            }
        };
    }

}
