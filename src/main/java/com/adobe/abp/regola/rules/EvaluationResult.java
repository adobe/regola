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

import com.adobe.abp.regola.results.Result;
import com.adobe.abp.regola.results.RuleResult;
import java.util.concurrent.CompletableFuture;

public interface EvaluationResult {

    /**
     * Returns the current result for the rule
     *
     * @return result for the rule
     */
    RuleResult snapshot();

    /**
     * Return a completable future for the result of a rule
     *
     * @return completable future of a result
     */
    CompletableFuture<Result> status();
}
