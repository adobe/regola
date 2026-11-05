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
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.util.JsonParserSequence;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.util.TokenBuffer;

import java.io.IOException;
import java.util.HashMap;

public class RuleDeserializer extends StdDeserializer<Rule> {
    private static final long serialVersionUID = -5996525917304371916L;

    private final HashMap<String, Class<? extends Rule>> registry = new HashMap<>();

    RuleDeserializer() {
        super(Rule.class);
    }

    RuleDeserializer registerRule(String type, Class<? extends Rule> clazz) {
        registry.put(type, clazz);
        return this;
    }

    @Override
    public Rule deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
        JsonToken token = parser.currentToken();
        if (token == null) {
            token = parser.nextToken();
        }
        if (token != JsonToken.START_OBJECT) {
            ctxt.reportWrongTokenException(Rule.class, JsonToken.START_OBJECT,
                    "Cannot deserialize Rule: expected an object");
        }

        // Find the "type" discriminator without rebuilding the whole (potentially deeply nested)
        // subtree. The previous implementation called mapper.readTree(parser) and then
        // mapper.treeToValue(root, clazz): for each nested rule (e.g. a chain of NOT wrappers),
        // the field holding the next rule is deserialized by invoking this same method again, and
        // readTree() re-materialises/copies that entire remaining subtree from scratch every time.
        // That makes total work quadratic in the nesting depth (an OutOfMemoryError was
        // reproducible with ~768 nested NOT rules).
        //
        // Instead, buffer only the handful of fields seen before "type" (normally none, since every
        // rule puts "type" first) and resume reading the rest of this object directly from
        // `parser`, so each nesting level only does O(1) extra work and total work stays linear.
        final TokenBuffer prefix = new TokenBuffer(parser, ctxt);
        prefix.writeStartObject();
        String ruleType = null;
        JsonToken fieldToken;
        while ((fieldToken = parser.nextToken()) != JsonToken.END_OBJECT) {
            if (fieldToken != JsonToken.FIELD_NAME) {
                ctxt.reportWrongTokenException(Rule.class, JsonToken.FIELD_NAME,
                        "Cannot deserialize Rule: expected a field name");
            }
            final String fieldName = parser.currentName();
            parser.nextToken();
            if ("type".equals(fieldName)) {
                ruleType = parser.getValueAsString();
                prefix.writeStringField(fieldName, ruleType);
                break;
            }
            prefix.writeFieldName(fieldName);
            prefix.copyCurrentStructure(parser);
        }

        if (ruleType == null) {
            return ctxt.reportInputMismatch(Rule.class,
                    "Cannot deserialize Rule: missing required 'type' field identifying the rule kind");
        }

        final Class<? extends Rule> clazz = registry.get(ruleType);
        if (clazz == null) {
            drainRemainingObjectFields(parser);
            return null;
        }

        final ObjectMapper mapper = (ObjectMapper) parser.getCodec();
        final JsonParser combinedParser = JsonParserSequence.createFlattened(
                false, prefix.asParser(mapper), parser);
        combinedParser.nextToken();
        return mapper.readValue(combinedParser, clazz);
    }

    /**
     * Consumes the rest of the current object's fields (after an unregistered "type" was found),
     * so the outer parser is left positioned past this object's closing brace, matching the
     * previous behaviour where the whole subtree was always consumed upfront via readTree().
     */
    private static void drainRemainingObjectFields(JsonParser parser) throws IOException {
        JsonToken token;
        while ((token = parser.nextToken()) != null && token != JsonToken.END_OBJECT) {
            if (token == JsonToken.FIELD_NAME) {
                final JsonToken value = parser.nextToken();
                if (value != null && value.isStructStart()) {
                    parser.skipChildren();
                }
            }
        }
    }
}
