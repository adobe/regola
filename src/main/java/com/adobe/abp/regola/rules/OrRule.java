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
import java.util.stream.Collectors;

/**
 * This rule evaluates to true if at least one of its subrules evaluates to true.
 * If a subrule is ignored, it is not considered in the evaluation.
 * Short-circuiting: as soon as one of the subrules evaluates to VALID, the evaluation stops and the result is VALID.
 * An empty OR has no operand able to satisfy the disjunction, so it evaluates to INVALID (the identity element).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@SuppressWarnings("this-escape")
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
        return new LockingEvaluationResult() {

            // Priority of the results based on their position in this list.
            // First entries have higher priority.
            // So, for example INVALID || FAILED == FAILED
            private final List<Result> RESULT_PRIORITY = List.of(Result.VALID, Result.FAILED, Result.OPERATION_NOT_SUPPORTED, Result.INVALID, Result.MAYBE);

            // The following mutable fields are accessed only inside decideUnderLock (under the lock).
            private Result intermediateResult = RESULT_PRIORITY.get(RESULT_PRIORITY.size() - 1); // Result with least priority
            private int ignoredRulesCounter;
            private int rulesToEvaluate = getRules().size();

            // Child results, published once in startEvaluation() before any callback is wired up.
            private volatile List<EvaluationResult> results = List.of();

            @Override
            protected void startEvaluation() {
                final List<Rule> rules = getRules();
                final List<EvaluationResult> evaluated = rules.stream()
                        .map(rule -> rule.evaluate(factsResolver))
                        .collect(Collectors.toList());
                results = evaluated; // safe publication before the callbacks below can fire

                if (rules.isEmpty()) {
                    complete(Result.INVALID); // empty disjunction is the identity ⇒ INVALID
                    return;
                }

                // Non-blocking: status() kicks off each subrule and whenComplete only registers a callback,
                // so the subrules evaluate concurrently. Each callback reports back via evaluateSubresult as
                // it settles; this loop does not wait. (Actual concurrency depends on the fact fetchers being async.)
                for (int i = 0; i < rules.size(); i++) {
                    final boolean ignore = rules.get(i).isIgnore();
                    evaluated.get(i).status()
                            .whenComplete((subresult, throwable) -> evaluateSubresult(subresult, throwable, ignore));
                }
            }

            private void evaluateSubresult(Result subresult, Throwable throwable, boolean ignore) {
                if (!ignore && throwable != null) {
                    completeExceptionally(throwable);
                    return;
                }
                decideUnderLock(() -> {
                    rulesToEvaluate--;
                    if (ignore) {
                        ignoredRulesCounter++;
                    } else {
                        intermediateResult = determineResult(intermediateResult, subresult);
                    }
                    if (intermediateResult == Result.VALID) {
                        return Optional.of(Result.VALID); // short-circuit: a VALID subrule satisfies the OR
                    } else if (rulesToEvaluate == 0) {
                        // If all subrules were ignored default to VALID, otherwise return the intermediate result
                        return Optional.of(ignoredRulesCounter == getRules().size() ? Result.VALID : intermediateResult);
                    }
                    return Optional.empty();
                });
            }

            // Return the result with the higher priority
            private Result determineResult(Result intermediateResult, Result result) {
                return RESULT_PRIORITY.indexOf(result) < RESULT_PRIORITY.indexOf(intermediateResult) ?
                        result : intermediateResult;
            }

            @Override
            protected void afterCompletion(Result completedResult, Throwable throwable) {
                Optional.ofNullable(getAction())
                        .ifPresent(action -> action.onCompletion(completedResult, throwable, snapshot()));
            }

            @Override
            public RuleResult snapshot() {
                final List<EvaluationResult> capturedResults = results;
                final Result capturedResult = getResult();
                return MultiaryBooleanRuleResult.builder().with(r -> {
                    r.type = getType();
                    r.description = getDescription();
                    r.result = capturedResult;
                    r.rules = capturedResults.stream()
                            .map(EvaluationResult::snapshot)
                            .collect(Collectors.toSet());
                    r.ignored = isIgnore();
                }).build();
            }
        };
    }
}
