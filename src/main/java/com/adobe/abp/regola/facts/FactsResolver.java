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

package com.adobe.abp.regola.facts;

import com.adobe.abp.regola.RulesEvaluator;
import java.util.concurrent.CompletableFuture;

/**
 * This is a resolver utility that gets passed to the {@link RulesEvaluator}.
 * The evaluator uses this resolver to get data points about the provided Facts.
 */
public interface FactsResolver {

    /**
     * Add a fact to the resolver
     *
     * @param fact to be added
     * @param <D> type associated to the Fact's data
     * @param <F> type of the data point(s) of the fact
     */
    <D, F> void addFact(Fact<D, F> fact);

    /**
     * Resolve a fact to a concrete data point
     *
     * @param key identifying the fact
     * @return data point
     */
    CompletableFuture<Object> resolveFact(String key);
}
