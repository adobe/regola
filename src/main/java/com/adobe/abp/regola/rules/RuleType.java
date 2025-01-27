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

// Limitation: this enum cannot be extended in such a way that json deserialization with jackson works.
public enum RuleType {
    // Logical type rules
    AND("AND"),
    OR("OR"),
    NOT("NOT"),

    // Fact only rules
    EXISTS("EXISTS"),

    // Fixed rules
    CONSTANT("CONSTANT"),

    // Data type rules
    STRING("STRING"),
    NUMBER("NUMBER"),
    SET("SET"),
    DATE("DATE"),
    NULL("NULL"),
    RANGE("RANGE");

    private final String name;

    RuleType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
