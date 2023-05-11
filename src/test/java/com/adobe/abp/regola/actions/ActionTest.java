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

package com.adobe.abp.regola.actions;

import com.adobe.abp.regola.results.Result;
import com.adobe.abp.regola.results.RuleResult;
import com.adobe.abp.regola.utils.lambda.TriConsumer;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ActionTest {

    @Test
    void canSetDescription() {
        var action = new Action()
                .setDescription("abc");

        assertThat(action.getDescription()).isEqualTo("abc");
    }

    @Test
    void canSetOnCompletion() {
        final var resultToSet = new AtomicReference<>(Result.MAYBE);

        var action = new Action()
                .setOnCompletion((result, throwable, ruleResult) -> resultToSet.set(result));

        action.onCompletion(Result.VALID, null, null);

        assertThat(resultToSet.get()).isEqualTo(Result.VALID);
    }

    @Test
    void canChainOnCompletions() {
        final var resultToSet = new AtomicReference<>(Result.MAYBE);

        TriConsumer<Result, Throwable, RuleResult> consumer = (result, throwable, ruleResult) -> resultToSet.set(result);
        consumer = consumer.andThen((result, throwable, ruleResult) -> resultToSet.set(Result.INVALID));
        var action = new Action().setOnCompletion(consumer);

        action.onCompletion(Result.VALID, null, null);

        assertThat(resultToSet.get()).isEqualTo(Result.INVALID);
    }

    @Test
    void generateToString() {
        var action = new Action()
                .setDescription("abc")
                .setOnCompletion((result, throwable, ruleResult) -> { /* do nothing */ });

        assertThat(action.toString()).isEqualTo("Action{description='abc', onCompletion=true}");
    }

}
