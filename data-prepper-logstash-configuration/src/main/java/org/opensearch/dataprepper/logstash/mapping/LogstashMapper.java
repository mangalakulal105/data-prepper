/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.logstash.mapping;

import com.amazon.dataprepper.model.configuration.PipelineModel;
import com.amazon.dataprepper.model.configuration.PluginModel;
import org.opensearch.dataprepper.logstash.exception.LogstashMappingException;
import org.opensearch.dataprepper.logstash.model.LogstashConfiguration;
import org.opensearch.dataprepper.logstash.model.LogstashPlugin;
import org.opensearch.dataprepper.logstash.model.LogstashPluginType;

import java.util.LinkedList;
import java.util.List;

/**
 * Converts Logstash configuration model to Data Prepper pipeline model
 *
 * @since 1.2
 */
public class LogstashMapper {
    public PipelineModel mapPipeline(LogstashConfiguration logstashConfiguration) {

        List<PluginModel> sourcePluginModels = mapPluginSection(logstashConfiguration, LogstashPluginType.INPUT);
        PluginModel sourcePlugin = null;
        if (sourcePluginModels.size() != 1)
            throw new LogstashMappingException("Only logstash configurations with exactly 1 input plugin are supported");
        else sourcePlugin = sourcePluginModels.get(0);

        List<PluginModel> prepperPluginModels = mapPluginSection(logstashConfiguration, LogstashPluginType.FILTER);

        List<PluginModel> sinkPluginModels = mapPluginSection(logstashConfiguration, LogstashPluginType.OUTPUT);

        if (sinkPluginModels.isEmpty()) {
            throw new LogstashMappingException("At least one logstash output plugin is required");
        }

        return new PipelineModel(sourcePlugin, null, prepperPluginModels, null, sinkPluginModels, null, null);
    }

    private List<PluginModel> mapPluginSection(LogstashConfiguration logstashConfiguration, LogstashPluginType logstashPluginType) {
        LogstashPluginMapper pluginMapper = new LogstashPluginMapper();
        List<PluginModel> pluginModels = new LinkedList<>();

        List<LogstashPlugin> logstashPluginList = logstashConfiguration.getPluginSection(logstashPluginType);
        if (logstashPluginList != null)
            logstashPluginList.forEach(logstashPlugin -> pluginModels.addAll(pluginMapper.mapPlugin(logstashPlugin)));

        return pluginModels;
    }
}
