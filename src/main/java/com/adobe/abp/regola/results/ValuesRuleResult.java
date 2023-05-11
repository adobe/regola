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
package com.adobe.abp.regola.results;

import com.adobe.abp.regola.rules.Operator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.github.fburato.functionalutils.utils.Builder;

import java.util.Collection;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ValuesRuleResult<T> extends RuleResult {

    private String key;
    private Operator operator;
    private T expectedValue;
    private Collection<T> expectedValues;
    private T actualValue;
    private Collection<T> actualValues;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public Operator getOperator() {
        return operator;
    }

    public void setOperator(Operator operator) {
        this.operator = operator;
    }

    public T getExpectedValue() {
        return expectedValue;
    }

    public void setExpectedValue(T expectedValue) {
        this.expectedValue = expectedValue;
    }

    public Collection<T> getExpectedValues() {
        return expectedValues;
    }

    public void setExpectedValues(Collection<T> expectedValues) {
        this.expectedValues = expectedValues;
    }

    public T getActualValue() {
        return actualValue;
    }

    public void setActualValue(T actualValue) {
        this.actualValue = actualValue;
    }

    public Collection<T> getActualValues() {
        return actualValues;
    }

    public void setActualValues(Collection<T> actualValues) {
        this.actualValues = actualValues;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        ValuesRuleResult<?> that = (ValuesRuleResult<?>) o;
        return Objects.equals(key, that.key) &&
                operator == that.operator &&
                Objects.equals(expectedValue, that.expectedValue) &&
                Objects.equals(expectedValues, that.expectedValues) &&
                Objects.equals(actualValue, that.actualValue) &&
                Objects.equals(actualValues, that.actualValues);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), key, operator, expectedValue, expectedValues, actualValue, actualValues);
    }

    @Override
    public String toString() {
        return "ValuesRuleResult{" +
                "result=" + getResult() +
                ", type='" + getType() + '\'' +
                ", description='" + getDescription() + '\'' +
                ", message='" + getMessage() + '\'' +
                ", ignored=" + isIgnored() +
                ", key='" + key + '\'' +
                ", operator=" + operator +
                ", expectedValue=" + expectedValue +
                ", expectedValues=" + expectedValues +
                ", actualValue=" + actualValue +
                ", actualValues=" + actualValues +
                "}";
    }

    public static <T> RuleResultBuilder<T> builder() {
        return new ValuesRuleResult.RuleResultBuilder<>();
    }

    @SuppressWarnings("squid:ClassVariableVisibilityCheck")
    public static class RuleResultBuilder<T> extends Builder<ValuesRuleResult<T>, RuleResultBuilder<T>> {

        public Result result;
        public String type;
        public String description;
        public String message;
        public Throwable cause;
        public boolean ignored;
        public Operator operator;
        public String key;
        public T expectedValue;
        public Collection<T> expectedValues;
        public T actualValue;
        public Collection<T> actualValues;

        private RuleResultBuilder() {
            super(RuleResultBuilder::new);
        }

        @Override
        protected ValuesRuleResult<T> makeValue() {
            final var ruleResult = new ValuesRuleResult<T>();
            ruleResult.setResult(result);
            ruleResult.setType(type);
            ruleResult.setDescription(description);
            ruleResult.setMessage(message);
            ruleResult.setCause(cause);
            ruleResult.setIgnored(ignored);
            ruleResult.setOperator(operator);
            ruleResult.setKey(key);
            ruleResult.setExpectedValue(expectedValue);
            ruleResult.setExpectedValues(expectedValues);
            ruleResult.setActualValue(actualValue);
            ruleResult.setActualValues(actualValues);
            return ruleResult;
        }
    }
}
