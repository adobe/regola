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
 * This rule evaluates to true if all the subrules evaluate to true.
 * If a subrule is ignored, it is not taken into account for the final result.
 * Short-circuiting: as soon as one of the subrules evaluates to not-VALID, the evaluation stops and the result is not-VALID.
 * An empty AND has no operands to falsify the conjunction, so it evaluates to VALID (the identity element).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@SuppressWarnings("this-escape")
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
        return new LockingEvaluationResult() {

            // Number of subrules still to report back. Accessed only inside decideUnderLock (under the lock).
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
                    complete(Result.VALID); // empty conjunction is the identity ⇒ VALID
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
                    if (!ignore && subresult != Result.VALID) {
                        return Optional.of(subresult); // short-circuit: a non-VALID subrule fails the AND
                    } else if (rulesToEvaluate == 0) {
                        return Optional.of(Result.VALID); // every subrule evaluated (or was ignored) ⇒ VALID
                    }
                    return Optional.empty();
                });
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
