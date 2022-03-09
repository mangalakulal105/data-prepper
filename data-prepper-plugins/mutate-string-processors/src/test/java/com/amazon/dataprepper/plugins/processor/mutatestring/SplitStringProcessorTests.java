/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.dataprepper.plugins.processor.mutatestring;

import com.amazon.dataprepper.metrics.PluginMetrics;
import com.amazon.dataprepper.model.event.Event;
import com.amazon.dataprepper.model.event.JacksonEvent;
import com.amazon.dataprepper.model.record.Record;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SplitStringProcessorTests {

    @Mock
    private PluginMetrics pluginMetrics;

    @Mock
    private StringProcessorConfig<SplitStringProcessorConfig.Entry> config;

    private SplitStringProcessor createObjectUnderTest() {
        return new SplitStringProcessor(pluginMetrics, config);
    }

    @Test
    public void testSingleSplitProcessor() {

        when(config.getIterativeConfig()).thenReturn(Collections.singletonList(createEntry("message", ",")));

        final SplitStringProcessor splitStringProcessor = createObjectUnderTest();
        final Record<Event> record = createEvent("hello,world");
        final List<Record<Event>> splitRecords = (List<Record<Event>>) splitStringProcessor.doExecute(Collections.singletonList(record));

        assertThat(splitRecords.get(0).getData().get("message", Object.class), notNullValue());
        assertThat(splitRecords.get(0).getData().get("message", Object.class), equalTo(Arrays.asList("hello", "world")));
    }

    private SplitStringProcessorConfig.Entry createEntry(final String source, final String delimiter) {
        return new SplitStringProcessorConfig.Entry(source, delimiter);
    }

    private Record<Event> createEvent(String message) {
        final Map<String, Object> eventData = new HashMap<>();
        eventData.put("message", message);
        return new Record<>(JacksonEvent.builder()
                .withEventType("event")
                .withData(eventData)
                .build());
    }

}