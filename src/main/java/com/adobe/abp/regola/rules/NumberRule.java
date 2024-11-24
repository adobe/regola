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

import com.adobe.abp.regola.results.Result;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * A rule that compares a fact to a number value.
 *
 * @param <V> the {@link Number} type of the fact
 */
public class NumberRule<V extends Number & Comparable<V>> extends SingleValueRule<V> {

    private final Comparator<V> comparator = new NumberComparator<>();

    public NumberRule() {
        this(null);
    }

    public NumberRule(String key, Operator operator, V value) {
        this(null, key, operator, value);
    }

    public NumberRule(Executor executor) {
        super(RuleType.NUMBER.getName(), Set.of(Number.class, Comparable.class), executor);
    }

    public NumberRule(Executor executor, String key, Operator operator, V value) {
        this(executor);
        setKey(key);
        setOperator(operator);
        setValue(value);
    }

    @Override
    Result check(V fact) {
        if (fact == null || getValue() == null) {
            return Result.INVALID;
        }

        switch (getOperator()) {
            case EQUALS:
                return checkFact(fact, (f, value) -> comparator.compare(f, getValue()) == 0);
            case GREATER_THAN:
                return checkFact(fact, (f, value) -> comparator.compare(f, getValue()) > 0);
            case GREATER_THAN_EQUAL:
                return checkFact(fact, (f, value) -> comparator.compare(f, getValue()) >= 0);
            case LESS_THAN:
                return checkFact(fact, (f, value) -> comparator.compare(f, getValue()) < 0);
            case LESS_THAN_EQUAL:
                return checkFact(fact, (f, value) -> comparator.compare(f, getValue()) <= 0);
            case DIVISIBLE_BY:
                if (!(fact instanceof Integer || fact instanceof Long) ||
                        !(getValue() instanceof Integer || getValue() instanceof Long)) {
                    return Result.OPERATION_NOT_SUPPORTED;
                }
                return checkFact(fact, (f, value) -> f.longValue() % getValue().longValue() == 0);

            default:
                return Result.OPERATION_NOT_SUPPORTED;
        }
    }

    @Override
    protected Set<Operator> getSingleFactOperators() {
        return Set.of(
                Operator.EQUALS,
                Operator.GREATER_THAN,
                Operator.GREATER_THAN_EQUAL,
                Operator.LESS_THAN,
                Operator.LESS_THAN_EQUAL,
                Operator.DIVISIBLE_BY
        );
    }

    static class NumberComparator<T extends Number & Comparable<T>> implements Comparator<T> {
        public int compare(T a, T b) throws ClassCastException {
            if (Objects.equals(a, b)) {
                return 0;
            } else if (a == null) {
                return -1;
            } else if (b == null) {
                return 1;
            } else if (a.getClass().equals(b.getClass())){
                return a.compareTo(b);
            } else {
                // Do the best we can if types do not match
                double comparison = a.doubleValue() - b.doubleValue();
                if (comparison < 0) {
                    return -1;
                } else if (comparison > 0) {
                    return 1;
                }
                return 0;
            }
        }
    }
}
