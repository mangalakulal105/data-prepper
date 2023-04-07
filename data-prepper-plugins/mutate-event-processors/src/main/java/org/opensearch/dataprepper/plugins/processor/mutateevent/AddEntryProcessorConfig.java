/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.processor.mutateevent;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class AddEntryProcessorConfig {
    public static class Entry {
        @NotEmpty
        @NotNull
        private String key;

        @NotEmpty
        @NotNull
        private Object value;

        @NotEmpty
        @NotNull
        private String format;

        @JsonProperty("overwrite_if_key_exists")
        private boolean overwriteIfKeyExists = false;

        public String getKey() {
            return key;
        }

        public Object getValue() {
            return value;
        }

        public String getFormat() {
            return format;
        }

        public boolean getOverwriteIfKeyExists() {
            return overwriteIfKeyExists;
        }

        public Entry(final String key, final Object value, final String format, final boolean overwriteIfKeyExists)
        {
            this.key = key;
            this.value = value;
            this.format = format;
            this.overwriteIfKeyExists = overwriteIfKeyExists;
        }

        public Entry() {

        }
    }

    @NotEmpty
    @NotNull
    private List<Entry> entries;

    public List<Entry> getEntries() {
        return entries;
    }
}
