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

import com.adobe.abp.regola.actions.Action;
import com.adobe.abp.regola.results.Result;
import com.adobe.abp.regola.results.RuleResult;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Base class for {@link EvaluationResult} implementations that need thread-safe state mutation
 * from concurrent {@link CompletableFuture} callbacks.
 *
 * <p>The class guarantees three non-obvious properties:
 * <ul>
 *   <li>{@link #startEvaluation()} runs at most once, on the first {@link #status()} call</li>
 *   <li>the published {@link Result} and the terminal status future never diverge</li>
 *   <li>{@link #afterCompletion(Result, Throwable)} runs outside the lock but before the future resolves</li>
 * </ul>
 *
 * <p>If {@code afterCompletion} throws, the future still transitions. A callback failure replaces a
 * normal completion, while an already-failed evaluation keeps its original cause and may record the
 * callback failure as suppressed.
 */
public abstract class LockingEvaluationResult implements EvaluationResult {

    private final ReentrantLock lock = new ReentrantLock();

    // volatile so an external thread polling snapshot() without the lock still observes a published
    // value. All in-lock writes already establish happens-before; volatile covers stray external pollers.
    private volatile Result result = Result.MAYBE;

    private final CompletableFuture<Result> status = new CompletableFuture<>();

    // Guards one-time startup. Written under the lock.
    private boolean started;

    // Marks that a terminal decision has been made. Flipped false→true exactly once, under the lock, by
    // the first completion path to win. It — not status.isDone() — is the completion guard, which is what
    // lets us complete the future *outside* the lock without a torn terminal state: a second racing
    // callback sees terminated==true and backs off before it can decide a different result.
    private volatile boolean terminated;

    // ── Protected accessors for subclass decision logic ──────────────────────────────────────────

    /**
     * Return the current result.
     *
     * <p>Safe to call from any thread because the field is {@code volatile}.
     *
     * @return the current {@link Result}, initially {@link Result#MAYBE}
     */
    protected final Result getResult() {
        return result;
    }

    /**
     * Return whether the status future has already been completed.
     *
     * @return {@code true} if the evaluation has reached a terminal state
     */
    protected final boolean isDone() {
        return terminated;
    }

    // ── Lock operations ───────────────────────────────────────────────────────────────────────────

    /**
     * Run subclass decision logic under the lock.
     *
     * <p>The supplier may mutate subclass-owned state and must return {@code Optional.of(terminal)}
     * to complete the evaluation or {@code Optional.empty()} to remain pending.
     *
     * <p>The terminal decision and the published {@code result} change together under the lock.
     *
     * @param decision supplier that inspects/mutates subclass state and returns the terminal
     *                 {@link Result} wrapped in an {@link Optional}, or {@link Optional#empty()} to
     *                 remain pending
     */
    protected final void decideUnderLock(Supplier<Optional<Result>> decision) {
        Result terminal = null;
        boolean dispatch = false;
        lock.lock();
        try {
            if (!terminated) {
                final Optional<Result> r = decision.get();
                if (r.isPresent()) {
                    terminal = r.get();
                    result = terminal;  // published under lock + volatile
                    terminated = true;  // no other callback can decide a different terminal now
                    dispatch = true;
                }
            }
        } finally {
            lock.unlock();
        }
        if (dispatch) {
            publishCompletion(terminal, null);
        }
    }

    /**
     * Complete normally from a non-callback path (e.g., an empty rule that resolves immediately
     * inside {@link #startEvaluation}).
     *
     * <p>Self-locking and guarded against races with child callbacks. Do NOT call from inside
     * {@link #decideUnderLock} — use the supplier's return value there instead.
     *
     * @param r the terminal {@link Result} to publish
     */
    protected final void complete(Result r) {
        boolean dispatch = false;
        lock.lock();
        try {
            if (!terminated) {
                result = r;
                terminated = true;
                dispatch = true;
            }
        } finally {
            lock.unlock();
        }
        if (dispatch) {
            publishCompletion(r, null);
        }
    }

    /**
     * Complete exceptionally.
     *
     * <p>Publishes {@link Result#FAILED} before the future resolves.
     *
     * @param t the throwable that caused the failure
     */
    protected final void completeExceptionally(Throwable t) {
        boolean dispatch = false;
        lock.lock();
        try {
            if (!terminated) {
                result = Result.FAILED;
                terminated = true;
                dispatch = true;
            }
        } finally {
            lock.unlock();
        }
        if (dispatch) {
            publishCompletion(Result.FAILED, t);
        }
    }

    /**
     * Dispatch {@link #afterCompletion} and then transition the status future, in that order.
     *
     * <p>If {@link #afterCompletion(Result, Throwable)} fails, this method still resolves the future.
     */
    private void publishCompletion(Result completedResult, Throwable throwable) {
        try {
            afterCompletion(completedResult, throwable);
        } catch (Throwable callbackFailure) {
            if (throwable != null) {
                if (callbackFailure != throwable) {
                    throwable.addSuppressed(callbackFailure);
                }
                status.completeExceptionally(throwable);
            } else {
                status.completeExceptionally(callbackFailure);
            }
            return;
        }
        if (throwable != null) {
            status.completeExceptionally(throwable);
            return;
        }
        status.complete(completedResult);
    }

    /**
     * Read subclass-owned state under the same lock used for mutations.
     *
     * <p>Subclasses should call this from {@link #snapshot()} to copy the fields they own into
     * locals, then build the {@link RuleResult} outside the lock.
     *
     * @param <T>      the type of value to read
     * @param supplier reads and returns the subclass-owned state
     *
     * @return the value returned by {@code supplier}
     */
    protected final <T> T readLocked(Supplier<T> supplier) {
        lock.lock();
        try {
            return supplier.get();
        } finally {
            lock.unlock();
        }
    }

    // ── Template methods ──────────────────────────────────────────────────────────────────────────

    /**
     * Called exactly once on the first {@link #status()} call. Subclasses kick off child evaluations
     * and attach callbacks here.
     *
     * <p>Subclasses must publish any mutable state that is later read from callbacks with the same
     * visibility guarantees used elsewhere in the class.
     */
    protected abstract void startEvaluation();

    /**
     * Handle post-completion work after the lock has been released.
     *
     * <p>Called exactly once after terminal state is recorded and before the status future resolves.
     * The default implementation is a no-op; boolean rules use this hook to dispatch
     * {@link Action#onCompletion(Result, Throwable, RuleResult)}.
     *
     * @param completedResult the terminal {@link Result} the evaluation settled on
     * @param throwable       the cause of an exceptional completion, or {@code null} on normal completion
     */
    protected void afterCompletion(Result completedResult, Throwable throwable) {
    }

    // ── Final implementation of the EvaluationResult contract ────────────────────────────────────

    @Override
    public final CompletableFuture<Result> status() {
        boolean shouldStart = false;
        lock.lock();
        try {
            if (!started) {
                started = true;
                shouldStart = true;
            }
        } finally {
            lock.unlock();
        }
        if (shouldStart) {
            try {
                startEvaluation();
            } catch (Throwable t) {
                completeExceptionally(t);
            }
        }
        return status;
    }
}
