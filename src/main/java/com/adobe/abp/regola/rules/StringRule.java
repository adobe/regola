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
import java.util.Set;
import java.util.concurrent.Executor;
import org.apache.commons.lang3.StringUtils;

/**
 * A rule that checks a String fact against a String value.
 */
public class StringRule extends SingleValueRule<String> {

    public StringRule() {
        this(null);
    }

    public StringRule(String key, Operator operator, String value) {
        this(null, key, operator, value);
    }

    public StringRule(Executor executor) {
        super(RuleType.STRING.getName(), Set.of(String.class), executor);
    }

    public StringRule(Executor executor, String key, Operator operator, String value) {
        this(executor);
        setKey(key);
        setOperator(operator);
        setValue(value);
    }

    @Override
    Result check(String fact) {
        switch (getOperator()) {
            case EQUALS:
                return checkFact(fact, String::equals);
            case GREATER_THAN:
                return checkFact(fact, (f, value) -> StringUtils.compare(f, value) > 0);
            case GREATER_THAN_EQUAL:
                return checkFact(fact, (f, value) -> StringUtils.compare(f, value) >= 0);
            case LESS_THAN:
                return checkFact(fact, (f, value) -> StringUtils.compare(f, value) < 0);
            case LESS_THAN_EQUAL:
                return checkFact(fact, (f, value) -> StringUtils.compare(f, value) <= 0);
            case STARTS_WITH:
                return checkFact(fact, StringUtils::startsWith);
            case ENDS_WITH:
                return checkFact(fact, StringUtils::endsWith);
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
                Operator.STARTS_WITH,
                Operator.ENDS_WITH
        );
    }
}
