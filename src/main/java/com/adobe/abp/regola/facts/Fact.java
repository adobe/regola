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
import com.adobe.abp.regola.datafetchers.DataFetcher;
import java.util.function.Function;

/**
 * A fact is a piece of information that should be evaluated against a rule that matches the Fact's key.
 * The fact is associated to an Object that gets evaluated on demand by the {@link RulesEvaluator}.
 * The evaluation of the Object works as follow:
 * 1. The evaluator retrieves data via a {@link DataFetcher}, which we can select using the Fact's {@link DataSource}.
 * 2. The data returned contains one or multiple data points, one of which is associated to this Fact.
 * 3. The factChecker specified by this Fact describes how to extract that data point from the data returned by the DataFetcher
 *
 * @param <D> type of the data for this fact
 * @param <F> type of the data point(s)
 */
public class Fact<D, F> {

    private final String key;
    private final DataSource dataSource;
    private final Function<D, F> factFetcher;

    /**
     * Constructor for a Fact
     *
     * @param key identifying the fact
     * @param dataSource identifier for the data retrieval function to be used
     * @param factFetcher function used to fetch a data point from the data returned from the dataFetcher.
     *                    This is the data used against the Rule identified for the key.
     */
    public Fact(String key, DataSource dataSource, Function<D, F> factFetcher) {
        this.key = key;
        this.dataSource = dataSource;
        this.factFetcher = factFetcher;
    }

    /**
     * Constructor for a fact with a {@link StandardDataSources#NONE} data source
     *
     * @param key identifying the fact
     * @param factFetcher function used to fetch a data point from the data returned from the dataFetcher.
     *                    This is the data used against the Rule identified for the key.
     */
    public Fact(String key, Function<D, F> factFetcher) {
        this(key, StandardDataSources.NONE, factFetcher);
    }

    /**
     * Get the key for this fact
     * @return key
     */
    public String getKey() {
        return key;
    }

    /**
     * Get the data source type for this fact
     * @return data source
     */
    public DataSource getDataSource() {
        return dataSource;
    }

    /**
     * Get the fact fetcher function used to get a data point from the data returned by a data source
     * @return fact fetcher function
     */
    public Function<D, F> getFactFetcher() {
        return factFetcher;
    }
}
