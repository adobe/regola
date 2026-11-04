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
import com.adobe.abp.regola.results.ValuesRuleResult;
import java.util.LinkedHashSet;
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

    /**
     * Construct a single-value rule.
     *
     * <p>The check runs on the thread that completes the fact. {@code executor} only produces the
     * {@code OPERATION_NOT_SUPPORTED} result for unsupported operators; it does not run fact resolution.
     * To isolate blocking data fetchers, supply an executor to
     * {@link com.adobe.abp.regola.facts.SimpleFactsResolver} instead.</p>
     *
     * @param type rule type
     * @param factTypes accepted fact types
     * @param executor executor for the unsupported-operator result, or {@code null} to use
     *                 {@link java.util.concurrent.ForkJoinPool#commonPool()}
     */
    public SingleValueRule(String type, Set<Class<?>> factTypes, Executor executor) {
        super(type);
        this.factTypes = new LinkedHashSet<>(factTypes);
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
        return new LeafEvaluationResult<T>(this, factsResolver) {
            @Override
            CompletableFuture<LeafOutcome<T>> evaluateRule() {
                if (getSingleFactOperators().contains(getOperator())) {
                    return castFact(resolveFact())
                            .thenApply(fact -> runCheck(
                                    () -> LeafOutcome.ofValue(check(fact), fact)));
                } else if (getSetFactOperators().contains(getOperator())) {
                    return castSetFact(resolveFact())
                            .thenApply(factsSet -> runCheck(
                                    () -> LeafOutcome.ofValues(check(factsSet), factsSet)));
                } else {
                    return runCheckAsync(
                            () -> LeafOutcome.of(Result.OPERATION_NOT_SUPPORTED), executor);
                }
            }

            @Override
            void describeExpectation(ValuesRuleResult.RuleResultBuilder<T> ruleResult) {
                ruleResult.operator = getOperator();
                ruleResult.expectedValue = getValue();
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
     * <p>
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
     * <p>
     * Return INVALID if:
     * - fact is null, or
     * - value is null, or
     * - predicate is not satisfied
     * <p>
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

    @SuppressWarnings("unchecked")
    private CompletableFuture<T> castFact(CompletableFuture<Object> resolved) {
        return resolved
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

    @SuppressWarnings("unchecked")
    private CompletableFuture<Set<T>> castSetFact(CompletableFuture<Object> resolved) {
        return resolved
                .thenApply(f -> {
                            if (f == null) {
                                return null;
                            }
                            return Optional.of(f)
                                    .filter(Set.class::isInstance)
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
