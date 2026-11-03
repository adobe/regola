/*
 *  Copyright 2026 Adobe. All rights reserved.
 *  This file is licensed to you under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License. You may obtain a copy
 *  of the License at http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software distributed under
 *  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
 *  OF ANY KIND, either express or implied. See the License for the specific language
 *  governing permissions and limitations under the License
 */

package com.adobe.abp.regola.rules;

import com.adobe.abp.regola.facts.FactsResolver;
import com.adobe.abp.regola.results.Result;
import com.adobe.abp.regola.utils.futures.FutureUtils;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Cancellation state of a single fact-based rule evaluation.
 *
 * <p>Cancelling cancels the future returned by the {@link FactsResolver}, so that a resolver can
 * skip a fetch that has not started yet, and the rule's status future. Rules use
 * {@link #isCancelled()} to skip their action and keep reporting {@link Result#MAYBE}.</p>
 */
final class LeafCancellation {

    private volatile boolean cancelled;
    private volatile CompletableFuture<?> fact;
    private volatile CompletableFuture<Result> status;

    CompletableFuture<Object> resolveFact(FactsResolver factsResolver, String key) {
        final CompletableFuture<Object> resolved = factsResolver.resolveFact(key);
        fact = resolved;
        if (cancelled) {
            resolved.cancel(false);
        }
        return resolved;
    }

    CompletableFuture<Result> start(Supplier<CompletableFuture<Result>> evaluation) {
        if (cancelled) {
            final CompletableFuture<Result> notStarted = new CompletableFuture<>();
            notStarted.cancel(false);
            return notStarted;
        }
        final CompletableFuture<Result> started = evaluation.get();
        status = started;
        if (cancelled) {
            started.cancel(false);
        }
        return started;
    }

    CompletableFuture<Result> evaluateAsync(Supplier<Result> evaluation, Executor executor) {
        return FutureUtils.supplyAsync(() -> cancelled ? Result.MAYBE : evaluation.get(), executor);
    }

    boolean isCancelled() {
        return cancelled;
    }

    void cancel() {
        cancelled = true;
        // Cancel the status first: cancelling the fact completes the status chain synchronously.
        Optional.ofNullable(status).ifPresent(s -> s.cancel(false));
        Optional.ofNullable(fact).ifPresent(f -> f.cancel(false));
    }
}
