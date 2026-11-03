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

    /**
     * Signal that the result of this evaluation is no longer needed, so that work which has not
     * happened yet can be skipped.
     *
     * <p>Cancellation is best effort. If the result is still pending, {@link #status()} completes
     * exceptionally with a {@link java.util.concurrent.CancellationException}, {@link #snapshot()}
     * keeps reporting {@link Result#MAYBE}, marked as {@link RuleResult#isSkipped() skipped} if the
     * evaluation was never started and {@link RuleResult#isCancelled() cancelled} otherwise, and the
     * rule's action runs {@link com.adobe.abp.regola.actions.Action#onCancellation(RuleResult)}
     * instead of its completion callback. Work that is already running, such as an in-flight data
     * fetch, is not interrupted. Cancelling a completed evaluation has no effect on its result.</p>
     *
     * <p>Boolean rules call this on their subrules once their own result is decided. The default
     * implementation does nothing.</p>
     */
    default void cancel() {
    }
}
