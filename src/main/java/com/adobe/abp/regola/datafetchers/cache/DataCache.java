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

import com.adobe.abp.regola.datafetchers.DataFetcher;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Interface for the cache used by the abstract {@link DataFetcher}
 *
 * @param <V> type of cached data
 */
public interface DataCache<V> {

    /**
     * Returns the future associated with key in this cache, obtaining that value from mappingFunction if necessary.
     * This method provides a simple substitute for the conventional "if cached, return; otherwise create, cache and return" pattern.
     * <p>
     * If the specified key is not already associated with a value, attempts to compute its value asynchronously and enters it into this cache unless null.
     *
     * @param key with which the specified value is to be associated
     * @param mappingFunction the function to asynchronously compute a value
     * @return the current future value associated with the specified key
     */
    CompletableFuture<V> get(String key, Function<String, CompletableFuture<V>> mappingFunction);
}
