/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.processor.keyvalue;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public class KeyValueProcessorConfig {
    static final String DEFAULT_SOURCE = "message";
    static final String DEFAULT_DESTINATION = "parsed_message";
    public static final String DEFAULT_FIELD_SPLIT_CHARACTERS = "&";
    static final String[] DEFAULT_INCLUDE_KEYS = new String[]{};
    public static final String DEFAULT_VALUE_SPLIT_CHARACTERS = "=";
    static final Object DEFAULT_NON_MATCH_VALUE = null;
    static final String DEFAULT_PREFIX = "";
    static final String DEFAULT_DELETE_KEY_REGEX = "";
    static final String DEFAULT_DELETE_VALUE_REGEX = "";

    @NotEmpty
    private String source = DEFAULT_SOURCE;

    @NotEmpty
    private String destination = DEFAULT_DESTINATION;

    @JsonProperty("field_delimiter_regex")
    private String fieldDelimiterRegex;

    @JsonProperty("field_split_characters")
    @NotEmpty
    private String fieldSplitCharacters = DEFAULT_FIELD_SPLIT_CHARACTERS;

    @JsonProperty("include_keys")
    @NotNull
    private String[] includeKeys = DEFAULT_INCLUDE_KEYS;

    @JsonProperty("key_value_delimiter_regex")
    private String keyValueDelimiterRegex;

    @JsonProperty("value_split_characters")
    private String valueSplitCharacters = DEFAULT_VALUE_SPLIT_CHARACTERS;

    @JsonProperty("non_match_value")
    private Object nonMatchValue = DEFAULT_NON_MATCH_VALUE;

    @NotNull
    private String prefix = DEFAULT_PREFIX;

    @JsonProperty("delete_key_regex")
    @NotNull
    private String deleteKeyRegex = DEFAULT_DELETE_KEY_REGEX;

    @JsonProperty("delete_value_regex")
    @NotNull
    private String deleteValueRegex = DEFAULT_DELETE_VALUE_REGEX;

    public String getSource() {
        return source;
    }

    public String getDestination() {
        return destination;
    }

    public String getFieldDelimiterRegex() {
        return fieldDelimiterRegex;
    }

    public String getFieldSplitCharacters() {
        return fieldSplitCharacters;
    }

    public String[] getIncludeKeys() {
        return includeKeys;
    }

    public String getKeyValueDelimiterRegex() {
        return keyValueDelimiterRegex;
    }

    public String getValueSplitCharacters() {
        return valueSplitCharacters;
    }

    public Object getNonMatchValue() {
        return nonMatchValue;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getDeleteKeyRegex() {
        return deleteKeyRegex;
    }

    public String getDeleteValueRegex() {
        return deleteValueRegex;
    }
}
