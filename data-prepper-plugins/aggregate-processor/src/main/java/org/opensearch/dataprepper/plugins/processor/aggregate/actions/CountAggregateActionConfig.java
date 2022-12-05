/*
 * Copyright OpenSearch Contributors
 * SPDX-Limense-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.processor.aggregate.actions;

import java.util.Set;
import java.util.HashSet;
import com.fasterxml.jackson.annotation.JsonProperty;

public class CountAggregateActionConfig {
    public static final String DEFAULT_COUNT_KEY = "aggr._count";
    public static final String DEFAULT_START_TIME_KEY = "aggr._start_time";
    public static final Set<String> validOutputFormats = new HashSet<>(Set.of(OutputFormat.OTEL_METRICS.toString(), OutputFormat.RAW.toString()));

    @JsonProperty("count_key")
    String countKey = DEFAULT_COUNT_KEY;

    @JsonProperty("start_time_key")
    String startTimeKey = DEFAULT_START_TIME_KEY;

    @JsonProperty("output_format")
    String outputFormat = OutputFormat.OTEL_METRICS.toString();

    public String getCountKey() {
        return countKey;
    }

    public String getStartTimeKey() {
        return startTimeKey;
    }

    public String getOutputFormat() {
        if (!validOutputFormats.contains(outputFormat)) {
            throw new IllegalArgumentException("Unknown output format " + outputFormat);
        }
        return outputFormat;
    }
} 
