/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.processor.mutateevent;

import org.opensearch.dataprepper.expression.ExpressionEvaluator;
import org.opensearch.dataprepper.metrics.PluginMetrics;
import org.opensearch.dataprepper.model.annotations.DataPrepperPlugin;
import org.opensearch.dataprepper.model.annotations.DataPrepperPluginConstructor;
import org.opensearch.dataprepper.model.event.Event;
import org.opensearch.dataprepper.model.processor.AbstractProcessor;
import org.opensearch.dataprepper.model.processor.Processor;
import org.opensearch.dataprepper.model.record.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.opensearch.dataprepper.logging.DataPrepperMarkers.EVENT;

@DataPrepperPlugin(name = "add_entries", pluginType = Processor.class, pluginConfigurationType = AddEntryProcessorConfig.class)
public class AddEntryProcessor extends AbstractProcessor<Record<Event>, Record<Event>> {
    private static final Logger LOG = LoggerFactory.getLogger(AddEntryProcessor.class);
    private final List<AddEntryProcessorConfig.Entry> entries;

    private final ExpressionEvaluator<Boolean> expressionEvaluator;

    @DataPrepperPluginConstructor
    public AddEntryProcessor(final PluginMetrics pluginMetrics, final AddEntryProcessorConfig config, final ExpressionEvaluator<Boolean> expressionEvaluator) {
        super(pluginMetrics);
        this.entries = config.getEntries();
        this.expressionEvaluator = expressionEvaluator;
    }

    @Override
    public Collection<Record<Event>> doExecute(final Collection<Record<Event>> records) {
        for(final Record<Event> record : records) {
            final Event recordEvent = record.getData();

            for(AddEntryProcessorConfig.Entry entry : entries) {

                if (Objects.nonNull(entry.getAddWhen()) && !expressionEvaluator.evaluate(entry.getAddWhen(), recordEvent)) {
                    continue;
                }

                try {
                    final String key = entry.getKey();
                    final String metadataKey = entry.getMetadataKey();
                    Object value;
                    if (!Objects.isNull(entry.getFormat())) {
                        value = recordEvent.formatString(entry.getFormat());
                    } else {
                        value = entry.getValue();
                    }
                    if (!Objects.isNull(key)) {
                        if (!recordEvent.containsKey(key) || entry.getOverwriteIfKeyExists()) {
                            recordEvent.put(key, value);
                        }
                    } else {
                        Map<String, Object> attributes = recordEvent.getMetadata().getAttributes();
                        if (!attributes.containsKey(metadataKey) || entry.getOverwriteIfKeyExists()) {
                            recordEvent.getMetadata().setAttribute(metadataKey, value);
    
                        }
                    }
                } catch (Exception e) {
                    LOG.error(EVENT, "Error adding entry to record [{}] with key [{}], metadataKey [{}], format [{}], value [{}]",
                            recordEvent, entry.getKey(), entry.getMetadataKey(), entry.getFormat(), entry.getValue(), e);
                }
            }
        }

        return records;
    }

    @Override
    public void prepareForShutdown() {
    }

    @Override
    public boolean isReadyForShutdown() {
        return true;
    }

    @Override
    public void shutdown() {
    }
}
