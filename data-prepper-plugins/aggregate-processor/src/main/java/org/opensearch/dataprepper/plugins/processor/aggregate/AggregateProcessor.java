/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.processor.aggregate;

import com.amazon.dataprepper.metrics.PluginMetrics;
import com.amazon.dataprepper.model.annotations.DataPrepperPlugin;
import com.amazon.dataprepper.model.annotations.DataPrepperPluginConstructor;
import com.amazon.dataprepper.model.configuration.PluginModel;
import com.amazon.dataprepper.model.configuration.PluginSetting;
import com.amazon.dataprepper.model.event.Event;
import com.amazon.dataprepper.model.peerforwarder.RequiresPeerForwarding;
import com.amazon.dataprepper.model.plugin.PluginFactory;
import com.amazon.dataprepper.model.processor.AbstractProcessor;
import com.amazon.dataprepper.model.processor.Processor;
import com.amazon.dataprepper.model.record.Record;
import io.micrometer.core.instrument.Counter;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@DataPrepperPlugin(name = "aggregate", pluginType = Processor.class, pluginConfigurationType = AggregateProcessorConfig.class)
public class AggregateProcessor extends AbstractProcessor<Record<Event>, Record<Event>> implements RequiresPeerForwarding {
    static final String ACTION_HANDLE_EVENTS_OUT = "actionHandleEventsOut";
    static final String ACTION_HANDLE_EVENTS_DROPPED = "actionHandleEventsDropped";
    static final String ACTION_CONCLUDE_GROUP_EVENTS_OUT = "actionConcludeGroupEventsOut";
    static final String ACTION_CONCLUDE_GROUP_EVENTS_DROPPED = "actionConcludeGroupEventsDropped";
    static final String CURRENT_AGGREGATE_GROUPS = "currentAggregateGroups";

    private final Counter actionHandleEventsOutCounter;
    private final Counter actionHandleEventsDroppedCounter;
    private final Counter actionConcludeGroupEventsDroppedCounter;
    private final Counter actionConcludeGroupEventsOutCounter;

    private final AggregateProcessorConfig aggregateProcessorConfig;
    private final AggregateGroupManager aggregateGroupManager;
    private final AggregateActionSynchronizer aggregateActionSynchronizer;
    private final AggregateIdentificationKeysHasher aggregateIdentificationKeysHasher;

    @DataPrepperPluginConstructor
    public AggregateProcessor(final AggregateProcessorConfig aggregateProcessorConfig, final PluginMetrics pluginMetrics, final PluginFactory pluginFactory) {
        this(aggregateProcessorConfig, pluginMetrics, pluginFactory, new AggregateGroupManager(aggregateProcessorConfig.getGroupDuration()),
                new AggregateIdentificationKeysHasher(aggregateProcessorConfig.getIdentificationKeys()), new AggregateActionSynchronizer.AggregateActionSynchronizerProvider());
    }
    public AggregateProcessor(final AggregateProcessorConfig aggregateProcessorConfig, final PluginMetrics pluginMetrics, final PluginFactory pluginFactory, final AggregateGroupManager aggregateGroupManager,
                              final AggregateIdentificationKeysHasher aggregateIdentificationKeysHasher, final AggregateActionSynchronizer.AggregateActionSynchronizerProvider aggregateActionSynchronizerProvider) {
        super(pluginMetrics);
        this.aggregateProcessorConfig = aggregateProcessorConfig;
        this.aggregateGroupManager = aggregateGroupManager;
        this.aggregateIdentificationKeysHasher = aggregateIdentificationKeysHasher;
        final AggregateAction aggregateAction = loadAggregateAction(pluginFactory);
        this.aggregateActionSynchronizer = aggregateActionSynchronizerProvider.provide(aggregateAction, aggregateGroupManager, pluginMetrics);

        this.actionConcludeGroupEventsOutCounter = pluginMetrics.counter(ACTION_CONCLUDE_GROUP_EVENTS_OUT);
        this.actionConcludeGroupEventsDroppedCounter = pluginMetrics.counter(ACTION_CONCLUDE_GROUP_EVENTS_DROPPED);
        this.actionHandleEventsOutCounter = pluginMetrics.counter(ACTION_HANDLE_EVENTS_OUT);
        this.actionHandleEventsDroppedCounter = pluginMetrics.counter(ACTION_HANDLE_EVENTS_DROPPED);

        pluginMetrics.gauge(CURRENT_AGGREGATE_GROUPS, aggregateGroupManager, AggregateGroupManager::getAllGroupsSize);
    }

    private AggregateAction loadAggregateAction(final PluginFactory pluginFactory) {
        final PluginModel actionConfiguration = aggregateProcessorConfig.getAggregateAction();
        final PluginSetting actionPluginSetting = new PluginSetting(actionConfiguration.getPluginName(), actionConfiguration.getPluginSettings());
        return pluginFactory.loadPlugin(AggregateAction.class, actionPluginSetting);
    }

    @Override
    public Collection<Record<Event>> doExecute(Collection<Record<Event>> records) {
        final List<Record<Event>> recordsOut = new LinkedList<>();

        final List<Map.Entry<AggregateIdentificationKeysHasher.IdentificationHash, AggregateGroup>> groupsToConclude = aggregateGroupManager.getGroupsToConclude();
        for (final Map.Entry<AggregateIdentificationKeysHasher.IdentificationHash, AggregateGroup> groupEntry : groupsToConclude) {
            final Optional<Event> concludeGroupEvent = aggregateActionSynchronizer.concludeGroup(groupEntry.getKey(), groupEntry.getValue());

            if (concludeGroupEvent.isPresent()) {
                recordsOut.add(new Record(concludeGroupEvent.get()));
                actionConcludeGroupEventsOutCounter.increment();
            } else {
                actionConcludeGroupEventsDroppedCounter.increment();
            }
        }

        int handleEventsOut = 0;
        int handleEventsDropped = 0;
        for (final Record<Event> record : records) {
            final Event event = record.getData();
            final AggregateIdentificationKeysHasher.IdentificationHash identificationKeysHash = aggregateIdentificationKeysHasher.createIdentificationKeyHashFromEvent(event);
            final AggregateGroup aggregateGroupForEvent = aggregateGroupManager.getAggregateGroup(identificationKeysHash);

            final AggregateActionResponse handleEventResponse = aggregateActionSynchronizer.handleEventForGroup(event, identificationKeysHash, aggregateGroupForEvent);

            final Event aggregateActionResponseEvent = handleEventResponse.getEvent();

            if (aggregateActionResponseEvent != null) {
                recordsOut.add(new Record<>(aggregateActionResponseEvent, record.getMetadata()));
                handleEventsOut++;
            } else {
                handleEventsDropped++;
            }
        }

        actionHandleEventsOutCounter.increment(handleEventsOut);
        actionHandleEventsDroppedCounter.increment(handleEventsDropped);
        return recordsOut;
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

    @Override
    public Collection<String> getIdentificationKeys() {
        return aggregateProcessorConfig.getIdentificationKeys();
    }
}
