/*
 *  Copyright 2025 Adobe. All rights reserved.
 *  This file is licensed to you under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License. You may obtain a copy
 *  of the License at http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software distributed under
 *  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
 *  OF ANY KIND, either express or implied. See the License for the specific language
 *  governing permissions and limitations under the License
 */

package com.adobe.abp.regola.results;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.commons.collections4.ListUtils;

public final class ResultReducer {

    private ResultReducer() {
        throw new IllegalAccessError("Do not instantiate");
    }

    /**
     * Reduces a hierarchical result tree into a flat list of {@link RuleResult} objects that match the given predicate.
     *
     * <p>This method traverses the result tree and collects all {@link RuleResult} instances that satisfy the provided
     * predicate into a single list.</p>
     *
     * @param result the root {@link RuleResult} to be reduced
     * @param predicate a {@link Predicate} used to filter the {@link RuleResult} instances to include in the final list
     * @return a list of {@link RuleResult} objects that match the given predicate
     * @throws NullPointerException if {@code result} or {@code predicate} is {@code null}
     */
    public static List<RuleResult> reduce(RuleResult result,
            Predicate<RuleResult> predicate) {
        return reduce(List.of(), result, predicate, x -> x);
    }

    /**
     * Reduces a hierarchical result tree into a flat list of objects of type {@code T}.
     *
     * <p>This method traverses the result tree and collects all {@link RuleResult} instances that satisfy the provided
     * predicate, transforming them into objects of type {@code T} using the given transformation function.</p>
     *
     * @param <T> the type of objects in the resulting list
     * @param result the root {@link RuleResult} to be reduced
     * @param resultPredicate a {@link Predicate} used to filter the {@link RuleResult} instances to include in the final list
     * @param transform a {@link Function} to transform each {@link RuleResult} into an object of type {@code T}
     * @return a list of objects of type {@code T} that match the given predicate
     * @throws NullPointerException if {@code result}, {@code resultPredicate}, or {@code transform} is {@code null}
     */
    public static <T> List<T> reduce(RuleResult result,
            Predicate<RuleResult> resultPredicate,
            Function<RuleResult, T> transform) {
        return reduce(List.of(), result, resultPredicate, transform);
    }

    private static <T> List<T> reduce(List<T> flattened,
            RuleResult result,
            Predicate<RuleResult> resultPredicate,
            Function<RuleResult, T> transform) {
        if (result instanceof ValuesRuleResult && resultPredicate.test(result)) {
            var rule = transform.apply(result);
            return ListUtils.union(flattened, List.of(rule));
        }

        if (result instanceof MultiaryBooleanRuleResult) {
            return ((MultiaryBooleanRuleResult) result).getRules().stream()
                    .flatMap(rule -> reduce(flattened, rule, resultPredicate, transform).stream())
                    .collect(Collectors.toList());
        }

        if (result instanceof UnaryBooleanRuleResult) {
            return reduce(flattened, ((UnaryBooleanRuleResult) result).getRule(), resultPredicate, transform);
        }

        return List.of();
    }
}
