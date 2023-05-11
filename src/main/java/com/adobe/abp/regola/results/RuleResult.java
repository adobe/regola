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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.github.fburato.functionalutils.utils.Builder;

import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RuleResult {

    private Result result;
    private String type;
    private String description;
    private String message;
    private Throwable cause;
    private boolean ignored;

    public Result getResult() {
        return result;
    }

    public void setResult(Result result) {
        this.result = result;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    // The Throwable object does not get deserialized properly by jackson due to self-reference objects
    // within the throwable.
    // Marking this field as ignored for now.
    // In next versions, we will remove the jackson dependency from regola and the need for this annotation.
    // Clients wishing to deserialise this class, will have to use jackson MixIn
    // to control deserialization of this library:
    // https://github.com/FasterXML/jackson-docs/wiki/JacksonMixInAnnotations
    @JsonIgnore
    public Throwable getCause() {
        return cause;
    }

    public void setCause(Throwable cause) {
        this.cause = cause;
    }

    public boolean isIgnored() {
        return ignored;
    }

    public void setIgnored(boolean ignored) {
        this.ignored = ignored;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        RuleResult that = (RuleResult) o;
        return result == that.result &&
                Objects.equals(type, that.type) &&
                Objects.equals(description, that.description) &&
                Objects.equals(message, that.message) &&
                Objects.equals(cause, that.cause) &&
                Objects.equals(ignored, that.ignored);
    }

    @Override
    public int hashCode() {
        return Objects.hash(result, type, message);
    }

    @Override
    public String toString() {
        return "RuleResult{" +
                "result=" + result +
                ", type='" + type + '\'' +
                ", description='" + description + '\'' +
                ", message='" + message + '\'' +
                ", ignored=" + ignored +
                '}';
    }

    public static RuleResultBuilder baseBuilder() {
        return new RuleResult.RuleResultBuilder();
    }

    @SuppressWarnings("squid:ClassVariableVisibilityCheck")
    public static class RuleResultBuilder extends Builder<RuleResult, RuleResultBuilder> {

        public Result result;
        public String type;
        public String description;
        public String message;
        public Throwable cause;
        public boolean ignored;

        private RuleResultBuilder() {
            super(RuleResultBuilder::new);
        }

        @Override
        protected RuleResult makeValue() {
            final var ruleResult = new RuleResult();
            ruleResult.setResult(result);
            ruleResult.setType(type);
            ruleResult.setDescription(description);
            ruleResult.setMessage(message);
            ruleResult.setCause(cause);
            ruleResult.setIgnored(ignored);
            return ruleResult;
        }
    }
}
