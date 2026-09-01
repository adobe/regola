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

package com.adobe.abp.regola.datafetchers.cache;

import java.util.concurrent.Executor;

public class DataCacheConfiguration {

    private int cacheSize = 1_000; // Must be 0 or greater
    private int cacheExpireAfterMinutes = 1; // Must be 0 or greater
    private Executor executor;

    public int getCacheSize() {
        return cacheSize;
    }

    public DataCacheConfiguration setCacheSize(int cacheSize) {
        this.cacheSize = cacheSize;
        return this;
    }

    public int getCacheExpireAfterMinutes() {
        return cacheExpireAfterMinutes;
    }

    public DataCacheConfiguration setCacheExpireAfterMinutes(int cacheExpireAfterMinutes) {
        this.cacheExpireAfterMinutes = cacheExpireAfterMinutes;
        return this;
    }

    /**
     * Returns the executor that {@link CaffeineCache} uses to invoke a mapping function on a cache miss.
     *
     * @return the mapping-function executor, or {@code null} to use Caffeine's default executor
     */
    public Executor getExecutor() {
        return executor;
    }

    /**
     * Sets the executor that {@link CaffeineCache} uses to invoke a mapping function on a cache miss.
     * The returned future is not executed on this executor unless the mapping function arranges that itself.
     *
     * @param executor the mapping-function executor, or {@code null} to use Caffeine's default executor
     * @return this configuration
     */
    public DataCacheConfiguration setExecutor(Executor executor) {
        this.executor = executor;
        return this;
    }

    @Override
    public String toString() {
        return "DataCacheConfiguration{" +
                "cacheSize=" + cacheSize +
                ", cacheExpireAfterMinutes=" + cacheExpireAfterMinutes +
                '}';
    }
}
