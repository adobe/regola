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

public enum Result {

    /**
     * The rule is valid with respect to its associated fact.
     */
    VALID,

    /**
     * The rule is invalid with respect to its associated fact.
     */
    INVALID,

    /**
     * It is not possible to determine whether a fact is valid or not.
     * This is most likely due to the rule not having been run against the related fact.
     */
    MAYBE,

    /**
     * The rule could not be evaluated because its definition is not supported.
     */
    OPERATION_NOT_SUPPORTED,

    /**
     * Rule failed to be evaluated
     */
    FAILED
}
