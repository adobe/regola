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
import com.adobe.abp.regola.results.RuleResult;
import com.adobe.abp.regola.results.ValuesRuleResult;
import com.adobe.abp.regola.utils.futures.FutureUtils;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Base class for the evaluation of a single fact-based rule.
 *
 * <p>The evaluation starts on the first {@link #status()} call; later calls return the same
 * future. It then either commits to an outcome or is stopped by {@link #cancel()}, whichever
 * happens first. A stopped evaluation is skipped if it was never started, cancelled otherwise: it
 * keeps reporting {@link Result#MAYBE}, its status future is cancelled and its action runs
 * {@link com.adobe.abp.regola.actions.Action#onCancellation(RuleResult)} instead of the
 * completion callback. A cancellation that loses the race has no effect.</p>
 *
 * <p>The status future is created up front and completed only by a committed evaluation, so a
 * late outcome can never overtake a cancellation. A committed evaluation publishes its outcome,
 * then runs {@link com.adobe.abp.regola.actions.Action#onCompletion(Result, Throwable, RuleResult)}
 * and then completes the status future. A failure, whether of the fact or of the check, is
 * published as {@link Result#FAILED} with the failure as message and cause, and completes the
 * status future normally. If the completion callback throws, the published outcome is kept: the
 * status future completes exceptionally with the callback failure, unless the evaluation had
 * already failed, in which case the original cause records the callback failure as suppressed.</p>
 *
 * <p>Cancelling also cancels the future returned by the {@link FactsResolver}, if it
 * {@link FactsResolver#supportsCancellation() supports it}, so that the resolver can skip a fetch
 * that has not started yet.</p>
 *
 * @param <T> type of the fact
 */
abstract class LeafEvaluationResult<T> implements EvaluationResult {

    private enum State { NEW, STARTED, COMMITTED, SKIPPED, CANCELLED }

    private final KeyBasedRule rule;
    private final FactsResolver factsResolver;
    private final AtomicReference<State> state = new AtomicReference<>(State.NEW);
    private final CompletableFuture<Result> status = new CompletableFuture<>();
    private volatile LeafOutcome<T> outcome = LeafOutcome.pending();
    private volatile CompletableFuture<?> fact;

    LeafEvaluationResult(KeyBasedRule rule, FactsResolver factsResolver) {
        this.rule = rule;
        this.factsResolver = factsResolver;
    }

    /**
     * Evaluate the rule. Called at most once, by the first {@link #status()} call.
     *
     * <p>Implementations run their check through {@link #runCheck(Supplier)} or
     * {@link #runCheckAsync(Supplier, Executor)}, so that a cancelled evaluation skips it.</p>
     *
     * @return the outcome of the evaluation
     */
    abstract CompletableFuture<LeafOutcome<T>> evaluateRule();

    /**
     * Add what the rule expects, such as its operator and expected values, to its snapshot.
     *
     * @param ruleResult snapshot being built
     */
    void describeExpectation(ValuesRuleResult.RuleResultBuilder<T> ruleResult) {
    }

    @Override
    public final RuleResult snapshot() {
        final State current = state.get();
        final LeafOutcome<T> published = outcome;
        return ValuesRuleResult.<T>builder()
                .with(r -> {
                    r.type = rule.getType();
                    r.key = rule.getKey();
                    r.description = rule.getDescription();
                    r.result = published.getResult();
                    r.actualValue = published.getActualValue();
                    r.actualValues = published.getActualValues();
                    r.message = published.getMessage();
                    r.cause = published.getCause();
                    r.ignored = rule.isIgnore();
                    r.skipped = current == State.SKIPPED;
                    r.cancelled = current == State.CANCELLED;
                    describeExpectation(r);
                })
                .build();
    }

    @Override
    public final CompletableFuture<Result> status() {
        if (state.compareAndSet(State.NEW, State.STARTED)) {
            startEvaluation().whenComplete(this::complete);
        }
        return status;
    }

    /**
     * Stop the evaluation unless it has already committed to an outcome or been stopped.
     */
    @Override
    public final void cancel() {
        if (!stop()) {
            return;
        }
        try {
            Optional.ofNullable(rule.getAction())
                    .ifPresent(action -> action.onCancellation(snapshot()));
        } finally {
            status.cancel(false);
            Optional.ofNullable(fact)
                    .ifPresent(f -> f.cancel(false));
        }
    }

    /**
     * Request the rule's fact, so that a cancellation can also cancel it.
     *
     * @return the fact
     */
    final CompletableFuture<Object> resolveFact() {
        final CompletableFuture<Object> resolved = factsResolver.resolveFact(rule.getKey());
        if (!factsResolver.supportsCancellation()) {
            return resolved;
        }
        fact = resolved;
        if (state.get() == State.CANCELLED) {
            resolved.cancel(false);
        }
        return resolved;
    }

    /**
     * Run the rule check on {@code executor}, unless the evaluation is cancelled while it waits.
     *
     * @param check the rule check
     * @param executor executor for the check, or {@code null} for the common pool
     * @return the outcome of the check, or a {@link CancellationException} if it was skipped
     */
    final CompletableFuture<LeafOutcome<T>> runCheckAsync(
            Supplier<LeafOutcome<T>> check,
            Executor executor) {
        return FutureUtils.supplyAsync(() -> runCheck(check), executor);
    }

    /**
     * Run the rule check unless the evaluation was cancelled first. Once the check runs,
     * cancellation has no effect.
     *
     * @param check the rule check
     * @return the outcome of the check
     * @throws CancellationException if the evaluation was cancelled first
     */
    final LeafOutcome<T> runCheck(Supplier<LeafOutcome<T>> check) {
        if (!commit()) {
            throw new CancellationException();
        }
        return check.get();
    }

    private CompletableFuture<LeafOutcome<T>> startEvaluation() {
        try {
            return evaluateRule();
        } catch (Throwable t) {
            return CompletableFuture.failedFuture(t);
        }
    }

    private void complete(LeafOutcome<T> evaluated, Throwable failure) {
        if (!commit()) {
            return;
        }
        final LeafOutcome<T> decided = failure == null ? evaluated : LeafOutcome.failed(failure);
        outcome = decided;
        try {
            Optional.ofNullable(rule.getAction())
                    .ifPresent(action -> action.onCompletion(
                            decided.getResult(), failure, snapshot()));
        } catch (Throwable callbackFailure) {
            if (failure == null) {
                status.completeExceptionally(callbackFailure);
                return;
            }
            if (callbackFailure != failure) {
                failure.addSuppressed(callbackFailure);
            }
        }
        status.complete(decided.getResult());
    }

    private boolean commit() {
        return state.compareAndSet(State.STARTED, State.COMMITTED)
                || state.get() == State.COMMITTED;
    }

    private boolean stop() {
        return state.compareAndSet(State.NEW, State.SKIPPED)
                || state.compareAndSet(State.STARTED, State.CANCELLED);
    }
}
