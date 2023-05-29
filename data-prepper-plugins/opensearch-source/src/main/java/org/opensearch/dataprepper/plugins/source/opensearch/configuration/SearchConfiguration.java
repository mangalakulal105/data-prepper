/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.source.opensearch.configuration;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.AssertTrue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;

public class SearchConfiguration {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger LOG = LoggerFactory.getLogger(SearchConfiguration.class);

    @JsonProperty("batch_size")
    private Integer batchSize;

    @JsonProperty("query")
    private String queryString;

    @JsonIgnore
    private Map<String, Object> queryMap;


    public Integer getBatchSize() {
        return batchSize;
    }

    public Map<String, Object> getQuery() {
        return queryMap;
    }

    @AssertTrue(message = "query is not a valid json string")
    boolean isQueryValid() {
        try {
            queryMap = objectMapper.readValue(queryString, new TypeReference<>() {});
            return true;
        } catch (final Exception e) {
            LOG.error("Invalid query json string: ", e);
            return false;
        }
    }
}
