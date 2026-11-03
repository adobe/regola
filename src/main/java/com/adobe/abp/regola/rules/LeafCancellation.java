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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Cancellation state of a single fact-based rule evaluation.
 *
 * <p>An evaluation is pending until it either commits to an outcome or is stopped, whichever
 * happens first. A stopped evaluation is skipped if it was never started, cancelled otherwise.
 * Rules {@link #commit() commit} before publishing a result or a failure, so a
 * cancellation that loses the race has no effect and a cancellation that wins it leaves the rule
 * reporting {@link Result#MAYBE}.</p>
 *
 * <p>Cancelling cancels the rule's status future and, if the {@link FactsResolver}
 * {@link FactsResolver#supportsCancellation() supports it}, the future it returned, so that the
 * resolver can skip a fetch that has not started yet. Rules also commit before
 * running their action, so the action of a cancelled rule never runs.</p>
 */
final class LeafCancellation {

    private enum State { PENDING, COMMITTED, SKIPPED, CANCELLED }

    private final AtomicReference<State> state = new AtomicReference<>(State.PENDING);
    private volatile boolean started;
    private volatile CompletableFuture<?> fact;
    private volatile CompletableFuture<Result> status;

    CompletableFuture<Object> resolveFact(FactsResolver factsResolver, String key) {
        final CompletableFuture<Object> resolved = factsResolver.resolveFact(key);
        if (!factsResolver.supportsCancellation()) {
            return resolved;
        }
        fact = resolved;
        if (isStopped()) {
            resolved.cancel(false);
        }
        return resolved;
    }

    CompletableFuture<Result> start(Supplier<CompletableFuture<Result>> evaluation) {
        started = true;
        if (isStopped()) {
            final CompletableFuture<Result> notStarted = new CompletableFuture<>();
            notStarted.cancel(false);
            return notStarted;
        }
        final CompletableFuture<Result> evaluating = evaluation.get();
        status = evaluating;
        if (isStopped()) {
            evaluating.cancel(false);
        }
        return evaluating;
    }

    CompletableFuture<Result> evaluateAsync(Supplier<Result> evaluation, Executor executor) {
        return FutureUtils.supplyAsync(() -> evaluate(evaluation), executor);
    }

    /**
     * Run the rule check unless the evaluation was cancelled first.
     */
    Result evaluate(Supplier<Result> evaluation) {
        return commit() ? evaluation.get() : Result.MAYBE;
    }

    /**
     * Commit to an outcome, after which cancellation has no effect.
     *
     * @return {@code false} if the evaluation was cancelled first
     */
    boolean commit() {
        return state.compareAndSet(State.PENDING, State.COMMITTED) || state.get() == State.COMMITTED;
    }

    boolean isSkipped() {
        return state.get() == State.SKIPPED;
    }

    boolean isCancelled() {
        return state.get() == State.CANCELLED;
    }

    private boolean isStopped() {
        return isSkipped() || isCancelled();
    }

    /**
     * Stop the evaluation unless it has already committed to an outcome or been stopped.
     *
     * @return {@code true} if this call stopped the evaluation
     */
    boolean cancel() {
        if (!state.compareAndSet(State.PENDING, started ? State.CANCELLED : State.SKIPPED)) {
            return false; // already committed to an outcome, or already stopped
        }
        // Cancel the status first: cancelling the fact completes the status chain synchronously.
        Optional.ofNullable(status).ifPresent(s -> s.cancel(false));
        Optional.ofNullable(fact).ifPresent(f -> f.cancel(false));
        return true;
    }
}
