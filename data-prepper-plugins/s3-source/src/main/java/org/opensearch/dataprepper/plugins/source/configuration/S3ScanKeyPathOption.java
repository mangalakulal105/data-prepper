/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.dataprepper.plugins.source.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Class consists the scan include and exclude keys properties.
 */
public class S3ScanKeyPathOption {
    @JsonProperty("include")
    @NotNull
    private List<String> s3scanIncludeOptions;
    @JsonProperty("exclude")
    @NotNull
    private List<String> s3ScanExcludeOptions;

    public List<String> getS3scanIncludeOptions() {
        return s3scanIncludeOptions;
    }

    public List<String> getS3ScanExcludeOptions() {
        return s3ScanExcludeOptions;
    }
}