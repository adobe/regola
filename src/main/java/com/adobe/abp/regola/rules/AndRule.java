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
import com.adobe.abp.regola.results.MultiaryBooleanRuleResult;
import com.adobe.abp.regola.results.Result;
import com.adobe.abp.regola.results.RuleResult;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * This rule evaluates to true if all the subrules evaluate to true.
 * If a subrule is ignored, it is not taken into account for the final result.
 * Short-circuiting: as soon as one of the subrules evaluates to not-VALID, the evaluation stops and the result is not-VALID.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AndRule extends MultiaryBooleanRule {

    public AndRule() {
        super(RuleType.AND.getName());
    }

    public AndRule(List<Rule> rules) {
        this();
        setRules(rules);
    }

    @Override
    public EvaluationResult evaluate(FactsResolver factsResolver) {
        return new EvaluationResult() {

            private Result result = Result.MAYBE;
            private final CompletableFuture<Result> status = new CompletableFuture<>();
            private final AtomicInteger rulesToEvaluate = new AtomicInteger(getRules().size());
            private List<EvaluationResult> results = List.of();

            @Override
            public RuleResult snapshot() {
                return MultiaryBooleanRuleResult.builder().with(r -> {
                    r.type = getType();
                    r.description = getDescription();
                    r.result = result;
                    r.rules = results.stream()
                            .map(EvaluationResult::snapshot)
                            .collect(Collectors.toSet());
                    r.ignored = isIgnore();
                }).build();
            }

            @Override
            public CompletableFuture<Result> status() {
                results = getRules().stream()
                        .map(this::evaluateRule)
                        .collect(Collectors.toList());
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
                final var remaining = rulesToEvaluate.decrementAndGet();
                if (!status.isDone()) {
                    if (!ignore && throwable != null) {
                        result = Result.FAILED;
                        status.completeExceptionally(throwable);
                    } else {
                        if (!ignore && subresult != Result.VALID) {
                            result = subresult;
                            status.complete(result);
                        } else if (remaining == 0) {
                            result = Result.VALID;
                            status.complete(result);
                        }
                    }
                }
            }
        };
    }
}
