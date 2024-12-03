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

import com.adobe.abp.regola.rules.Rule;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class RuleDeserializer extends StdDeserializer<Rule> {
    private static final long serialVersionUID = -5996525917304371916L;

    private final Map<String, Class<? extends Rule>> registry = new HashMap<>();

    RuleDeserializer() {
        super(Rule.class);
    }

    RuleDeserializer registerRule(String type, Class<? extends Rule> clazz) {
        registry.put(type, clazz);
        return this;
    }

    @Override
    public Rule deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
        ObjectMapper mapper = (ObjectMapper) parser.getCodec();
        ObjectNode root = mapper.readTree(parser);
        final var ruleType = root.get("type").asText();
        Class<? extends Rule> clazz = registry.get(ruleType);
        if (clazz == null) {
            return null;
        }

        return mapper.treeToValue(root, clazz);
    }
}
