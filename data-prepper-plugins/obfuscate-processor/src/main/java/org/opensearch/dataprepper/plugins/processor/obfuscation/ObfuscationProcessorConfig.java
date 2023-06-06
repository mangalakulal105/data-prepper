/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.processor.obfuscation;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.opensearch.dataprepper.model.configuration.PluginModel;

import java.util.List;

public class ObfuscationProcessorConfig {

    @JsonProperty("source")
    @NotEmpty
    @NotNull
    private String source;

    @JsonProperty("patterns")
    private List<String> patterns;
    
    @JsonProperty("target")
    private String target;

    @JsonProperty("action")
    private PluginModel action;

    public ObfuscationProcessorConfig() {
    }

    public ObfuscationProcessorConfig(String source, List<String> patterns, String target, PluginModel action) {
        this.source = source;
        this.patterns = patterns;
        this.target = target;
        this.action = action;
    }

    public String getSource() {
        return source;
    }

    public List<String> getPatterns() {
        return patterns;
    }

    public String getTarget() {
        return target;
    }

    public PluginModel getAction() {
        return action;
    }
}
