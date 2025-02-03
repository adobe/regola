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

package com.adobe.abp.regola.datafetchers;

import com.adobe.abp.regola.datafetchers.metrics.LogMetricsAgent;
import com.adobe.abp.regola.datafetchers.metrics.MetricsAgent;

public class DataFetcherConfiguration {

    private int metricsTimesToSample = 10; // Must be greater than 1
    private long slaFetchTime = 0; // Must be 0 or greater. If 0, then no SLA should be enforced
    private MetricsAgent metricsAgent = new LogMetricsAgent();

    public int getMetricsTimesToSample() {
        return metricsTimesToSample;
    }

    public DataFetcherConfiguration setMetricsTimesToSample(int metricsTimesToSample) {
        this.metricsTimesToSample = metricsTimesToSample;
        return this;
    }

    public long getSlaFetchTime() {
        return slaFetchTime;
    }

    public DataFetcherConfiguration setSlaFetchTime(long slaFetchTime) {
        this.slaFetchTime = slaFetchTime;
        return this;
    }

    public MetricsAgent getMetricsAgent() {
        return metricsAgent;
    }

    public void setMetricsAgent(MetricsAgent metricsAgent) {
        this.metricsAgent = metricsAgent;
    }

    @Override
    public String toString() {
        return "DataFetcherConfiguration{" +
                "metricsTimesToSample=" + metricsTimesToSample +
                ", slaFetchTime=" + slaFetchTime +
                '}';
    }
}
