/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.source.opensearch.configuration;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;

import java.time.Duration;
import java.time.Instant;

public class SchedulingParameterConfiguration {

    @JsonProperty("rate")
    private Duration rate = Duration.ofHours(8);

    @Min(1)
    @JsonProperty("job_count")
    private int jobCount = 1;

    @JsonProperty("start_time")
    private String startTime = Instant.now().toString();

    @JsonIgnore
    private Instant startTimeInstant;

    public Duration getRate() {
        return rate;
    }

    public int getJobCount() {
        return jobCount;
    }

    public Instant getStartTime() {
        return startTimeInstant;
    }

    @AssertTrue(message = "start_time must be a valid Java Instant format such as \"2007-12-03T10:15:30.00Z\"")
    boolean isStartTimeValid() {
        try {
            startTimeInstant = Instant.parse(startTime);
            return true;
        } catch (final Exception e) {
            return false;
        }
    }
}