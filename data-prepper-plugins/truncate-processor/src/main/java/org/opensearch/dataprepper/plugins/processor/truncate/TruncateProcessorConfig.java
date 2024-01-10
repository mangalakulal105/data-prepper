/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.processor.truncate;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue; 

import java.util.List;

public class TruncateProcessorConfig {
    @NotEmpty
    @NotNull
    @JsonProperty("source_keys")
    private List<String> sourceKeys;

    @JsonProperty("length")
    private Integer length;

    @JsonProperty("start_at")
    private Integer startAt;

    @JsonProperty("truncate_when")
    private String truncateWhen;

    public List<String> getSourceKeys() {
        return sourceKeys;
    }

    public Integer getStartAt() {
        return startAt;
    }

    public Integer getLength() {
        return length;
    }

    @AssertTrue(message = "At least one of source or source_keys must be specified. At least one of start_at or length or both must be specified and the values must be positive integers")
    public boolean isValidConfig() {
        if (length == null && startAt == null) {
            return false;
        }
        if (length != null && length < 0) {
            return false;
        }
        if (startAt != null && startAt < 0) {
            return false;
        }
        return true;
    }  

    public String getTruncateWhen() { return truncateWhen; }
}

