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

import com.adobe.abp.regola.results.MultiaryBooleanRuleResult;
import com.adobe.abp.regola.results.RuleResult;
import com.adobe.abp.regola.results.UnaryBooleanRuleResult;
import com.adobe.abp.regola.results.ValuesRuleResult;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;

public class RuleResultDeserializer extends StdDeserializer<RuleResult> {

    private static final long serialVersionUID = -4156695581016185307L;

    RuleResultDeserializer() {
        super(RuleResult.class);
    }

    @Override
    public RuleResult deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
        ObjectMapper mapper = (ObjectMapper) parser.getCodec();
        ObjectNode root = mapper.readTree(parser);

        if (root.has("rules")) {
            return mapper.treeToValue(root, MultiaryBooleanRuleResult.class);
        }
        if (root.has("rule")) {
            return mapper.treeToValue(root, UnaryBooleanRuleResult.class);
        }
        if (root.has("key")) {
            return mapper.treeToValue(root, ValuesRuleResult.class);
        }

        // If none works, attempt to deserialise to the base result class.
        return mapper.treeToValue(root, RuleResult.class);
    }
}
