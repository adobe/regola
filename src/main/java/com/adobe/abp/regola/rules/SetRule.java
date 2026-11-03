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
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.apache.commons.collections4.CollectionUtils;

/**
 * This rule checks that a fact is contained in a set of values.
 * <p>
 * The rule supports the following operators:
 * <ul>
 * <li>{@link Operator#IN}</li>
 * <li>{@link Operator#INTERSECTS}</li>
 * </ul>
 * <p>
 * The rule will return {@link Result#OPERATION_NOT_SUPPORTED} for any other
 * combination of types or sets.
 *
 * @param <T> type of the fact
 */
public class SetRule<T> extends OperatorBasedRule {

    private static final String TYPE_MISMATCH = "Fact must match type of the rule's values";

    private Set<T> values;
    private final Executor executor;

    public SetRule() {
        this(null);
    }

    /**
     * Construct a set rule.
     *
     * <p>{@code executor} runs the check after the fact is resolved, and the
     * {@code OPERATION_NOT_SUPPORTED} result for unsupported operators. It does not run fact resolution:
     * to isolate blocking data fetchers, supply an executor to
     * {@link com.adobe.abp.regola.facts.SimpleFactsResolver} instead.</p>
     *
     * @param executor executor for the check, or {@code null} to use
     *                 {@link java.util.concurrent.ForkJoinPool#commonPool()}
     */
    public SetRule(Executor executor) {
        super(RuleType.SET.getName());
        this.executor = executor;
    }

    public Set<T> getValues() {
        return values;
    }

    public SetRule<T> setValues(Set<T> values) {
        this.values = values;
        return this;
    }

    @Override
    public EvaluationResult evaluate(FactsResolver factsResolver) {
        return new LeafEvaluationResult<T>(this, factsResolver) {
            @Override
            CompletableFuture<LeafOutcome<T>> evaluateRule() {
                if (getOperator() == Operator.IN) {
                    return exposeTypeMismatch(castFact(resolveFact())
                            .thenCompose(fact -> runCheckAsync(
                                    () -> handleSuccess(f -> checkIn(f), fact), executor)));
                }

                if (getOperator() == Operator.INTERSECTS) {
                    return exposeTypeMismatch(castFact(resolveFact())
                            .thenCompose(fact -> runCheckAsync(
                                    () -> handleSuccess(f -> checkIntersects(f), fact),
                                    executor)));
                }

                return runCheckAsync(
                        () -> LeafOutcome.of(Result.OPERATION_NOT_SUPPORTED), executor);
            }

            @Override
            void describeExpectation(ValuesRuleResult.RuleResultBuilder<T> ruleResult) {
                ruleResult.operator = getOperator();
                ruleResult.expectedValues = getValues();
            }

            @SuppressWarnings("unchecked")
            private LeafOutcome<T> handleSuccess(Function<T, Result> checkFunction, T fact) {
                final Result checkedResult = checkFunction.apply(fact);
                if (fact instanceof Collection) {
                    return LeafOutcome.ofValues(checkedResult, (Collection<T>) fact);
                }
                return LeafOutcome.ofValue(checkedResult, fact);
            }
        };
    }

    private Result checkIn(T fact) {
        return check(fact, (v, f) -> CollectionUtils.containsAll(getValues(), f));
    }

    private Result checkIntersects(T fact) {
        return check(fact, (v, f) -> CollectionUtils.containsAny(getValues(), f));
    }

    private Result check(T fact, BiFunction<Set<T>, Collection<?>, Boolean> checkCollection) {
        if (fact == null) {
            return Result.INVALID;
        }

        if (!getValues().isEmpty() && fact instanceof Collection && ((Collection<?>) fact).isEmpty()) {
            return Result.INVALID;
        }

        return Optional.of(fact)
                .filter(f -> {
                    if (f instanceof Collection) {
                        return checkCollection.apply(getValues(), (Collection<?>) f);
                    } else {
                        return getValues().contains(f);
                    }
                })
                .map(f -> Result.VALID)
                .orElse(Result.INVALID);
    }

    @SuppressWarnings("unchecked")
    private CompletableFuture<T> castFact(CompletableFuture<Object> resolved) {
        return resolved.thenApply(fact -> {
            if (fact == null) {
                return null;
            }
            requireMatchingFact(fact);
            return (T) fact;
        });
    }

    private void requireMatchingFact(Object fact) {
        Class<?> witness = valueWitness();
        if (witness == null) {
            return;
        }
        if (!(fact instanceof Collection)) {
            requireInstance(witness, fact);
            return;
        }
        for (Object element : (Collection<?>) fact) {
            requireInstance(witness, element);
        }
    }

    private void requireInstance(Class<?> witness, Object value) {
        if (value != null && !witness.isInstance(value)) {
            throw new IllegalArgumentException(TYPE_MISMATCH);
        }
    }

    private Class<?> valueWitness() {
        if (values == null) {
            return null;
        }
        for (T value : values) {
            if (value != null) {
                return value.getClass();
            }
        }
        return null;
    }

    private CompletableFuture<LeafOutcome<T>> exposeTypeMismatch(
            CompletableFuture<LeafOutcome<T>> checked) {
        var exposed = new CompletableFuture<LeafOutcome<T>>();
        checked.whenComplete((outcome, failure) -> {
            if (failure == null) {
                exposed.complete(outcome);
                return;
            }
            exposed.completeExceptionally(typeMismatchOrSame(failure));
        });
        return exposed;
    }

    private Throwable typeMismatchOrSame(Throwable failure) {
        Throwable cause = failure instanceof CompletionException ? failure.getCause() : failure;
        if (cause instanceof IllegalArgumentException
                && TYPE_MISMATCH.equals(cause.getMessage())) {
            return cause;
        }
        return failure;
    }

    @Override
    public String toString() {
        return "SingleValueRule{" +
                "type='" + getType() + '\'' +
                ", description='" + getDescription() + '\'' +
                ", key='" + getKey() + '\'' +
                ", operator=" + getOperator() +
                ", values=" + values +
                ", ignore=" + isIgnore() +
                "}";
    }
}
