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

package com.adobe.abp.regola.json;

import com.adobe.abp.regola.rules.AndRule;
import com.adobe.abp.regola.rules.DateRule;
import com.adobe.abp.regola.rules.ExistsRule;
import com.adobe.abp.regola.rules.NotRule;
import com.adobe.abp.regola.rules.NullRule;
import com.adobe.abp.regola.rules.NumberRule;
import com.adobe.abp.regola.rules.OrRule;
import com.adobe.abp.regola.rules.ConstantRule;
import com.adobe.abp.regola.rules.Rule;
import com.adobe.abp.regola.rules.SetRule;
import com.adobe.abp.regola.rules.StringRule;
import com.fasterxml.jackson.databind.module.SimpleModule;

public class RuleModule extends SimpleModule {

    private final RuleDeserializer deserializers = new RuleDeserializer()
            .registerRule("AND", AndRule.class)
            .registerRule("OR", OrRule.class)
            .registerRule("NOT", NotRule.class)
            .registerRule("EXISTS", ExistsRule.class)
            .registerRule("STRING", StringRule.class)
            .registerRule("SET", SetRule.class)
            .registerRule("NUMBER", NumberRule.class)
            .registerRule("DATE", DateRule.class)
            .registerRule("NULL", NullRule.class)
            .registerRule("CONSTANT", ConstantRule.class);

    public RuleModule() {
        super("RuleModule");
        addDeserializer(Rule.class, deserializers);
    }

    public RuleModule addRule(String type, Class<? extends Rule> clazz) {
        deserializers.registerRule(type, clazz);
        addDeserializer(Rule.class, deserializers);

        return this;
    }
}
