/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.processor.aggregate.actions;

import com.amazon.dataprepper.model.event.Event;
import com.amazon.dataprepper.model.event.JacksonEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.dataprepper.plugins.processor.aggregate.AggregateAction;
import org.opensearch.dataprepper.plugins.processor.aggregate.AggregateActionInput;
import org.opensearch.dataprepper.plugins.processor.aggregate.AggregateActionResponse;
import org.opensearch.dataprepper.plugins.processor.aggregate.AggregateActionTestUtils;
import org.opensearch.dataprepper.plugins.processor.aggregate.GroupState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class PutAllAggregateActionTest {
    private AggregateAction combineAggregateAction;
    private List<Event> events;
    private List<Map<String, Object>> eventMaps;

    @BeforeEach
    void setup() {
        events = new ArrayList<>();
        eventMaps = new ArrayList<>();

        final Map<String, Object> firstEventMap = new HashMap<>();
        firstEventMap.put("string", "firstEventString");
        firstEventMap.put("array", Arrays.asList(1, 2, 3));
        eventMaps.add(firstEventMap);
        events.add(buildEventFromMap(firstEventMap));

        final Map<String, Object> secondEventMap = new HashMap<>();
        secondEventMap.put("string", "secondEventString");
        secondEventMap.put("number", 2);
        eventMaps.add(secondEventMap);
        events.add(buildEventFromMap(secondEventMap));
    }

    private Event buildEventFromMap(final Map<String, Object> eventMap) {
        return JacksonEvent.builder()
                .withEventType("event")
                .withData(eventMap)
                .build();
    }

    private PutAllAggregateAction createObjectUnderTest() {
        return new PutAllAggregateAction();
    }

    @Test
    void handleEvent_with_empty_group_state_should_return_correct_AggregateResponse_and_add_event_to_groupState() {
        combineAggregateAction = createObjectUnderTest();

        final AggregateActionInput aggregateActionInput = new AggregateActionTestUtils.TestAggregateActionInput();

        final AggregateActionResponse aggregateActionResponse = combineAggregateAction.handleEvent(events.get(0), aggregateActionInput);

        assertThat(aggregateActionResponse.getEvent(), equalTo(null));
        assertThat(aggregateActionInput.getGroupState(), equalTo(events.get(0).toMap()));
    }

    @Test
    void handleEvent_with_non_empty_groupState_should_combine_Event_with_groupState_correctly() {
        combineAggregateAction = createObjectUnderTest();

        final AggregateActionInput aggregateActionInput = new AggregateActionTestUtils.TestAggregateActionInput();
        final GroupState groupState = aggregateActionInput.getGroupState();
        groupState.putAll(events.get(0).toMap());
        final GroupState expectedGroupState = new AggregateActionTestUtils.TestGroupState();
        expectedGroupState.putAll(groupState);
        expectedGroupState.putAll(events.get(1).toMap());
        final AggregateActionResponse aggregateActionResponse = combineAggregateAction.handleEvent(events.get(1), aggregateActionInput);
        assertThat(aggregateActionResponse.getEvent(), equalTo(null));
        assertThat(groupState, equalTo(expectedGroupState));
    }

    @Test
    void concludeGroup_should_return_groupState_As_An_Event_correctly() {
        combineAggregateAction = createObjectUnderTest();
        final AggregateActionInput aggregateActionInput = new AggregateActionTestUtils.TestAggregateActionInput();
        final GroupState groupState = aggregateActionInput.getGroupState();
        for (final Map<String, Object> eventMap : eventMaps) {
            groupState.putAll(eventMap);
        }

        final Optional<Event> result = combineAggregateAction.concludeGroup(aggregateActionInput);
        assertThat(result.isPresent(), equalTo(true));
        assertThat(result.get().getMetadata().getEventType(), equalTo(PutAllAggregateAction.EVENT_TYPE));
        assertThat(result.get().toMap(), equalTo(groupState));
    }
}
