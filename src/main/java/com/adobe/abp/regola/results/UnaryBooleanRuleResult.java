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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.github.fburato.functionalutils.utils.Builder;

import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class UnaryBooleanRuleResult extends RuleResult {

    private RuleResult rule;

    public RuleResult getRule() {
        return rule;
    }

    public void setRule(RuleResult rule) {
        this.rule = rule;
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
        UnaryBooleanRuleResult that = (UnaryBooleanRuleResult) o;
        return Objects.equals(rule, that.rule);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), rule);
    }

    @Override
    public String toString() {
        return "UnaryBooleanRuleResult{" +
                "result=" + getResult() +
                ", type='" + getType() + '\'' +
                ", description='" + getDescription() + '\'' +
                ", message='" + getMessage() + '\'' +
                ", ignored=" + isIgnored() +
                ", rule=" + rule +
                "}";
    }

    public static RuleResultBuilder builder() {
        return new UnaryBooleanRuleResult.RuleResultBuilder();
    }

    @SuppressWarnings("squid:ClassVariableVisibilityCheck")
    public static class RuleResultBuilder extends Builder<UnaryBooleanRuleResult, RuleResultBuilder> {

        public Result result;
        public String type;
        public String description;
        public String message;
        public boolean ignored;
        public RuleResult rule;

        private RuleResultBuilder() {
            super(RuleResultBuilder::new);
        }

        @Override
        protected UnaryBooleanRuleResult makeValue() {
            final var ruleResult = new UnaryBooleanRuleResult();
            ruleResult.setResult(result);
            ruleResult.setType(type);
            ruleResult.setDescription(description);
            ruleResult.setMessage(message);
            ruleResult.setIgnored(ignored);
            ruleResult.setRule(rule);
            return ruleResult;
        }
    }
}
