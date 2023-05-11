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

import com.adobe.abp.regola.actions.Action;
import com.adobe.abp.regola.facts.FactsResolver;

/**
 * Abstract representation of a Rule.
 * All concrete rules must inherit from this class.
 */
public abstract class Rule {

    /**
     * This is the type related to the rule itself, not the fact.
     * So, for example a single-value rule with type NUMBER is expected
     * to have a value of type number.
     *
     * We use a String type, rather than enum, to allow for new rules (and types) to be added by applications using this library.
     */
    private final String type;

    /**
     * Optional description for this rule.
     * The description should not be used for evaluating a rule.
     */
    private String description;

    /**
     * Flag to mark the rule as ignored.
     *
     * By default this flag is set to false.
     */
    private boolean ignore;

    /**
     * Determine any relevant action to be taken for the rule
     */
    private Action action;

    /**
     * Constructor for a Rule
     *
     * @param type associated to the rule
     */
    public Rule(String type) {
        this.type = type;
    }

    /**
     * Method evaluating the associated fact(s) for this rule.
     *
     * @param factsResolver used to get facts to evaluate against this rule
     * @return RuleResult
     */
    public abstract EvaluationResult evaluate(FactsResolver factsResolver);

    /**
     * Type uniquely identifying the concrete rule.
     * The type must be provided for json deserialization to work.
     *
     * @return type
     */
    public String getType() {
        return type;
    }

    /**
     * Get the description for this rule.
     *
     * @return description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Set the description for this rule.
     *
     * @param description of the rule
     * @return this rule
     */
    public Rule setDescription(String description) {
        this.description = description;
        return this;
    }

    /**
     * Set the ignore flag.
     *
     * @param ignore flat to set
     * @return this rule
     */
    public Rule setIgnore(boolean ignore) {
        this.ignore = ignore;
        return this;
    }

    /**
     * Get the ignore flag.
     *
     * @return the ignore flag
     */
    public boolean isIgnore() {
        return ignore;
    }

    /**
     * Set the action to be taken for this rule.
     *
     * @param action to be taken
     * @return this rule
     */
    public Rule setAction(Action action) {
        this.action = action;
        return this;
    }

    /**
     * Get the action to be taken for this rule.
     *
     * @return the action to be taken
     */
    public Action getAction() {
        return action;
    }

    @Override
    public String toString() {
        return "Rule{" +
                "type='" + type + '\'' +
                ", description='" + description + '\'' +
                ", ignore=" + ignore +
                ", action=" + action +
                '}';
    }
}
