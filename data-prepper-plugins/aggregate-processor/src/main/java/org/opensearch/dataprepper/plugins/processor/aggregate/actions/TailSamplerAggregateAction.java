/*
 * Copyright OpenSearch Contributors
 * SPDX-Limense-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.processor.aggregate.actions;

import org.opensearch.dataprepper.model.annotations.DataPrepperPlugin;
import org.opensearch.dataprepper.model.annotations.DataPrepperPluginConstructor;
import org.opensearch.dataprepper.model.event.Event;
import org.opensearch.dataprepper.plugins.processor.aggregate.AggregateAction;
import org.opensearch.dataprepper.plugins.processor.aggregate.AggregateActionInput;
import org.opensearch.dataprepper.plugins.processor.aggregate.AggregateActionResponse;
import org.opensearch.dataprepper.plugins.processor.aggregate.GroupState;
import org.opensearch.dataprepper.expression.ExpressionEvaluator;

import java.util.List;
import java.util.ArrayList;
import java.util.Random;
import java.time.Duration;
import java.time.Instant;

/**
 * An AggregateAction that combines multiple Events into a single Event. This action 
 * 
 * @since 2.1
 */
@DataPrepperPlugin(name = "tail_sampler", pluginType = AggregateAction.class, pluginConfigurationType = TailSamplerAggregateActionConfig.class)
public class TailSamplerAggregateAction implements AggregateAction {
    static final String LAST_RECEIVED_TIME_KEY = "last_received_time";
    static final String EVENTS_KEY = "events";
    static final String ERROR_STATUS_KEY = "error_status";
    private final double percent;
    private final Duration waitPeriod;
    private final ExpressionEvaluator<Boolean> expressionEvaluator;
    private final String errorCondition;

    @DataPrepperPluginConstructor
    public TailSamplerAggregateAction(final TailSamplerAggregateActionConfig tailSamplerAggregateActionConfig, final ExpressionEvaluator<Boolean> expressionEvaluator) {
        percent = tailSamplerAggregateActionConfig.getPercent();
        waitPeriod = tailSamplerAggregateActionConfig.getWaitPeriod();
        errorCondition = tailSamplerAggregateActionConfig.getErrorCondition();
        this.expressionEvaluator = expressionEvaluator;
    }

    @Override
    public boolean shouldCarryState() {
        return true;
    }

    @Override
    public AggregateActionResponse handleEvent(final Event event, final AggregateActionInput aggregateActionInput) {
        final GroupState groupState = aggregateActionInput.getGroupState();
        List<Event> events = (List)groupState.getOrDefault(EVENTS_KEY, new ArrayList<>());
        events.add(event);
        groupState.put(EVENTS_KEY, events);
        if (errorCondition != null && !errorCondition.isEmpty() && expressionEvaluator.evaluate(errorCondition, event)) {
            groupState.put(ERROR_STATUS_KEY, true);
        }
        groupState.put(LAST_RECEIVED_TIME_KEY, Instant.now());
        return AggregateActionResponse.nullEventResponse();
    }

    @Override
    public List<Event> concludeGroup(final AggregateActionInput aggregateActionInput) {
        GroupState groupState = aggregateActionInput.getGroupState();
        Duration timeDiff = Duration.between((Instant)groupState.get(LAST_RECEIVED_TIME_KEY), Instant.now());
        List<Event> result = (List)groupState.get(EVENTS_KEY);
        if (result != null) {
            if (timeDiff.getSeconds() > waitPeriod.getSeconds()) {
                Random randomNum = new Random();
                int randomInt = randomNum.nextInt(100);
                if ((boolean)groupState.getOrDefault(ERROR_STATUS_KEY, false) ||
                    (randomInt < percent)) {
                    groupState.remove(EVENTS_KEY);
                    return result;
                } else {
                    groupState.remove(EVENTS_KEY);
                }
            }
        }
        return List.of();
    }

}
