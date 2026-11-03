/*
 *  Copyright 2026 Adobe. All rights reserved.
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
import java.util.Collection;

/**
 * The outcome of a fact-based rule evaluation: its result together with the fact it was decided
 * on and, if it failed, why.
 *
 * <p>Immutable, so that it can be published with a single write and a snapshot never pairs the
 * result of one outcome with the details of another.</p>
 *
 * @param <T> type of the fact
 */
final class LeafOutcome<T> {

    private static final LeafOutcome<?> PENDING =
            new LeafOutcome<>(Result.MAYBE, null, null, null, null);

    private final Result result;
    private final T actualValue;
    private final Collection<T> actualValues;
    private final String message;
    private final Throwable cause;

    private LeafOutcome(
            Result result,
            T actualValue,
            Collection<T> actualValues,
            String message,
            Throwable cause) {
        this.result = result;
        this.actualValue = actualValue;
        this.actualValues = actualValues;
        this.message = message;
        this.cause = cause;
    }

    @SuppressWarnings("unchecked")
    static <T> LeafOutcome<T> pending() {
        return (LeafOutcome<T>) PENDING;
    }

    static <T> LeafOutcome<T> of(Result result) {
        return new LeafOutcome<>(result, null, null, null, null);
    }

    static <T> LeafOutcome<T> ofValue(Result result, T actualValue) {
        return new LeafOutcome<>(result, actualValue, null, null, null);
    }

    static <T> LeafOutcome<T> ofValues(Result result, Collection<T> actualValues) {
        return new LeafOutcome<>(result, null, actualValues, null, null);
    }

    static <T> LeafOutcome<T> failed(String message, T actualValue) {
        return new LeafOutcome<>(Result.FAILED, actualValue, null, message, null);
    }

    static <T> LeafOutcome<T> failed(Throwable cause) {
        return new LeafOutcome<>(Result.FAILED, null, null, cause.getMessage(), cause);
    }

    Result getResult() {
        return result;
    }

    T getActualValue() {
        return actualValue;
    }

    Collection<T> getActualValues() {
        return actualValues;
    }

    String getMessage() {
        return message;
    }

    Throwable getCause() {
        return cause;
    }
}
