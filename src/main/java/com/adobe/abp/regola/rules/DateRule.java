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
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * A rule that checks if a fact is a date and compares it to a value.
 */
public class DateRule extends SingleValueRule<OffsetDateTime> {

    public DateRule() {
        this(null);
    }

    public DateRule(String key, Operator operator, OffsetDateTime value) {
        this(null, key, operator, value);
    }

    public DateRule(Executor executor) {
        super(RuleType.DATE.getName(), Set.of(OffsetDateTime.class), executor);
    }

    public DateRule(Executor executor, String key, Operator operator, OffsetDateTime value) {
        this(executor);
        setKey(key);
        setOperator(operator);
        setValue(value);
    }

    @Override
    Result check(OffsetDateTime fact) {
        switch (getOperator()) {
            case EQUALS:
                return checkFact(fact, OffsetDateTime::isEqual);
            case GREATER_THAN:
                return checkFact(fact, OffsetDateTime::isAfter);
            case GREATER_THAN_EQUAL:
                return checkFact(fact, (f, value) -> f.isEqual(getValue()) || f.isAfter(getValue()));
            case LESS_THAN:
                return checkFact(fact, OffsetDateTime::isBefore);
            case LESS_THAN_EQUAL:
                return checkFact(fact, (f, value) -> f.isEqual(getValue()) || f.isBefore(getValue()));
            default:
                return Result.OPERATION_NOT_SUPPORTED;
        }
    }
}
