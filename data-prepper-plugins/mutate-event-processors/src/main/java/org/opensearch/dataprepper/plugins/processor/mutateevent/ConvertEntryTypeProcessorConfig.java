/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.processor.mutateevent;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Optional;

public class ConvertEntryTypeProcessorConfig  {
    @JsonProperty("key")
    private String key;

    @JsonProperty("keys")
    private List<String> keys;

    @JsonProperty("type")
    private TargetType type = TargetType.INTEGER;

    @JsonProperty("convert_when")
    private String convertWhen;

    @JsonProperty("null_values")
    private List<String> nullValues;

    public String getKey() {
        return key;
    }

    public List<String> getKeys() { return keys; }

    public TargetType getType() {
        return type;
    }

    public String getConvertWhen() { return convertWhen; }

    public Optional<List<String>> getNullValues(){
        return Optional.ofNullable(nullValues);
    }
}
