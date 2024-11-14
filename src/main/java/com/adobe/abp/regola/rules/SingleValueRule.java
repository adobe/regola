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
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

public abstract class SingleValueRule<T> extends OperatorBasedRule {

    private T value;
    private final Executor executor;
    private final Set<Class<?>> factTypes;

    public SingleValueRule(String type, Set<Class<?>> factTypes, Executor executor) {
        super(type);
        this.factTypes = factTypes;
        this.executor = executor;
    }

    public T getValue() {
        return value;
    }

    public SingleValueRule<T> setValue(T value) {
        this.value = value;
        return this;
    }

    @Override
    public EvaluationResult evaluate(FactsResolver factsResolver) {
        return new EvaluationResult() {
            private Result result = Result.MAYBE;
            private T evaluatedFact;
            private Collection<T> evaluatedFacts;
            private String message;
            private Throwable cause;

            @Override
            public RuleResult snapshot() {
                return ValuesRuleResult.<T>builder().with(r -> {
                    r.type = getType();
                    r.key = getKey();
                    r.operator = getOperator();
                    r.description = getDescription();
                    r.result = result;
                    r.expectedValue = getValue();
                    r.actualValue = evaluatedFact;
                    r.actualValues = evaluatedFacts;
                    r.message = message;
                    r.cause = cause;
                    r.ignored = isIgnore();
                }).build();
            }

            @Override
            public CompletableFuture<Result> status() {
                if (getSingleFactOperators().contains(getOperator())) {
                    return resolveFact(factsResolver, getKey())
                            .thenApply(fact -> {
                                result = check(fact);
                                evaluatedFact = fact;
                                return result;
                            })
                            .whenComplete((result, throwable) -> Optional.ofNullable(getAction())
                                    .ifPresent(action -> action.onCompletion(result, throwable, snapshot())))
                            .exceptionally(this::handleFailure);
                } else if (getSetFactOperators().contains(getOperator())) {
                    return resolveSetFact(factsResolver, getKey())
                            .thenApply(factsSet -> {
                                result = check(factsSet);
                                evaluatedFacts = factsSet;
                                return result;
                            })
                            .whenComplete((result, throwable) -> Optional.ofNullable(getAction())
                                    .ifPresent(action -> action.onCompletion(result, throwable, snapshot())))
                            .exceptionally(this::handleFailure);
                } else {
                    return FutureUtils.supplyAsync(() -> {
                                result = Result.OPERATION_NOT_SUPPORTED;
                                return result;
                            }, executor)
                            .whenComplete((result, throwable) -> Optional.ofNullable(getAction())
                                    .ifPresent(action -> action.onCompletion(result, throwable, snapshot())));
                }
            }

            private Result handleFailure(Throwable throwable) {
                result = Result.FAILED;
                message = throwable.getMessage();
                cause = throwable;
                return result;
            }
        };
    }

    protected Set<Operator> getSingleFactOperators() {
        return Set.of(
                Operator.EQUALS,
                Operator.GREATER_THAN,
                Operator.GREATER_THAN_EQUAL,
                Operator.LESS_THAN,
                Operator.LESS_THAN_EQUAL
        );
    }

    protected Set<Operator> getSetFactOperators() {
        return Set.of(Operator.CONTAINS);
    }

    /**
     * Check if the provided fact satisfies the rule condition.
     *
     * @param fact to check
     * @return result
     */
    abstract Result check(T fact);

    /**
     * Check if the provided set fact satisfied the rule condition.
     *
     * This default implementation support the CONTAINS operator only.
     *
     * @param factsSet to check
     * @return result
     */
    Result check(Set<T> factsSet) {
        if (getOperator() == Operator.CONTAINS) {
            return checkFact(factsSet, Set::contains);
        } else {
            return Result.OPERATION_NOT_SUPPORTED;
        }
    }

    /**
     * Check if the provided SINGLE fact satisfies the given predicate.
     *
     * Return INVALID if:
     * - fact is null, or
     * - value is null, or
     * - predicate is not satisfied
     *
     * Otherwise return a VALID result.
     *
     * @param fact to be checked
     * @param predicate used to check the fact against the value of the rule
     * @param <F> type of the fact
     * @return result
     */
    <F> Result checkFact(F fact, BiPredicate<F, T> predicate) {
        return Optional.ofNullable(fact)
                .filter(f -> Objects.nonNull(getValue()))
                .filter(f -> predicate.test(f, getValue()))
                .map(f -> Result.VALID)
                .orElse(Result.INVALID);
    }

    private CompletableFuture<T> resolveFact(FactsResolver factsResolver, String key) {
        return factsResolver.resolveFact(key)
                .thenApply(f -> {
                            if (f == null) {
                                return null;
                            }
                            return Optional.of(f)
                                    .filter(fact -> factTypes.stream()
                                            .allMatch(at -> at.isInstance(fact)))
                                    .map(fact -> (T) fact)
                                    .orElseThrow(() -> new IllegalArgumentException(
                                            String.format("Fact must be of one of the following types=[%s], but it was of type=%s", factTypes.stream()
                                                    .map(Class::getSimpleName)
                                                    .collect(Collectors.joining(", ")), f.getClass().getSimpleName())));
                        }
                );
    }

    private CompletableFuture<Set<T>> resolveSetFact(FactsResolver factsResolver, String key) {
        return factsResolver.resolveFact(key)
                .thenApply(f -> {
                            if (f == null) {
                                return null;
                            }
                            return Optional.of(f)
                                    .filter(fact -> fact instanceof Set)
                                    .map(fact -> (Set<T>) fact)
                                    .orElseThrow(() -> new IllegalArgumentException("Fact must be a Set"));
                        }
                );
    }

    @Override
    public String toString() {
        return "SingleValueRule{" +
                "type='" + getType() + '\'' +
                ", description='" + getDescription() + '\'' +
                ", key='" + getKey() + '\'' +
                ", operator=" + getOperator() +
                ", value='" + value + '\'' +
                ", ignore=" + isIgnore() +
                "}";
    }
}
