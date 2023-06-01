/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.dataprepper.plugins.source.sqssource.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Duration;
import java.util.List;

public class QueuesOptions {

    private List<String> urls;

    @JsonProperty("polling_frequency")
    private Duration pollingFrequency;

    @JsonProperty("batch_size")
    private Integer batchSize;

    @JsonProperty("number_of_threads")
    private Integer numberOfThreads = 1;

    public List<String> getUrls() {
        return urls;
    }

    public Duration getPollingFrequency() {
        return pollingFrequency;
    }

    public Integer getBatchSize() {
        return batchSize;
    }

    public Integer getNumberOfThreads() {
        return numberOfThreads;
    }
}
