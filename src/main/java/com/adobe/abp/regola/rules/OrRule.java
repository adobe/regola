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
 * This rule evaluates to true if at least one of its subrules evaluates to true.
 * If a subrule is ignored, it is not considered in the evaluation.
 * Short-circuiting: as soon as one of the subrules evaluates to VALID, the evaluation stops and the result is VALID.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrRule extends MultiaryBooleanRule {

    public OrRule() {
        super(RuleType.OR.getName());
    }

    public OrRule(List<Rule> rules) {
        this();
        setRules(rules);
    }

    @Override
    public EvaluationResult evaluate(FactsResolver factsResolver) {
        return new EvaluationResult() {

            // Priority of the results based on their position in this list.
            // First entries have higher priority.
            // So, for example INVALID || FAILED == FAILED
            private final List<Result> RESULT_PRIORITY = List.of(Result.VALID, Result.FAILED, Result.OPERATION_NOT_SUPPORTED, Result.INVALID, Result.MAYBE);

            private Result result = Result.MAYBE;
            private Result intermediateResult = RESULT_PRIORITY.get(RESULT_PRIORITY.size() - 1); // Result with least priority
            private final AtomicInteger ignoredRulesCounter = new AtomicInteger();
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
                if (ignore) {
                    ignoredRulesCounter.incrementAndGet();
                }
                if (!status.isDone()) {
                    if (!ignore && throwable != null) {
                        result = Result.FAILED;
                        status.completeExceptionally(throwable);
                    } else {
                        intermediateResult = ignore ? intermediateResult : determineResult(intermediateResult, subresult);
                        if (intermediateResult == Result.VALID) {
                            result = intermediateResult;
                            status.complete(result);
                        } else if (remaining == 0) {
                            // If all subrules have been ignored, then default to VALID, otherwise
                            // return the intermediate result
                            result = ignoredRulesCounter.get() == getRules().size() ? Result.VALID : intermediateResult;
                            status.complete(result);
                        }
                    }
                }
            }

            // Return the result with the higher priority
            private Result determineResult(Result intermediateResult, Result result) {
                return RESULT_PRIORITY.indexOf(result) < RESULT_PRIORITY.indexOf(intermediateResult) ?
                        result : intermediateResult;
            }
        };
    }
}
