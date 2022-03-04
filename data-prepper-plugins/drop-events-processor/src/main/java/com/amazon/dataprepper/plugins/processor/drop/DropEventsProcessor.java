/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.dataprepper.plugins.processor.drop;

import com.amazon.dataprepper.model.annotations.DataPrepperPlugin;
import com.amazon.dataprepper.model.annotations.DataPrepperPluginConstructor;
import com.amazon.dataprepper.model.configuration.PluginSetting;
import com.amazon.dataprepper.model.event.Event;
import com.amazon.dataprepper.model.processor.AbstractProcessor;
import com.amazon.dataprepper.model.processor.Processor;
import com.amazon.dataprepper.model.record.Record;
import org.opensearch.dataprepper.expression.ExpressionEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

@DataPrepperPlugin(name = "drop_events", pluginType = Processor.class)
public class DropEventsProcessor extends AbstractProcessor<Record<Event>, Record<Event>> {
    private static final Logger LOG = LoggerFactory.getLogger(DropEventsProcessor.class);
    private static final String WHEN_PLUGIN_SETTING_KEY = "when";
    private static final String HANDLE_FAILED_EVENTS_KEY = "handle_failed_events";

    private final DropEventsWhenCondition whenCondition;

    @DataPrepperPluginConstructor
    public DropEventsProcessor(final PluginSetting pluginSetting, final ExpressionEvaluator<Boolean> expressionEvaluator) {
        super(pluginSetting);

        final Object whenSetting = pluginSetting.getAttributeFromSettings(WHEN_PLUGIN_SETTING_KEY);
        final Object handleFailedEventsSetting = pluginSetting.getAttributeFromSettings(HANDLE_FAILED_EVENTS_KEY);

        whenCondition = new DropEventsWhenCondition(whenSetting, handleFailedEventsSetting, expressionEvaluator);
    }

    @Override
    public Collection<Record<Event>> doExecute(Collection<Record<Event>> records) {
        if (whenCondition.shouldEvaluateConditional()) {
            return records.stream()
                    .filter(record -> whenCondition.isStatementFalseWith(record.getData()))
                    .collect(Collectors.toList());
        }
        else {
            return Collections.emptyList();
        }
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
