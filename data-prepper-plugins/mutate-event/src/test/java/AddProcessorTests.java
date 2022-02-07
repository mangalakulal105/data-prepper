/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

import com.amazon.dataprepper.metrics.PluginMetrics;
import com.amazon.dataprepper.model.event.Event;
import com.amazon.dataprepper.model.event.JacksonEvent;
import com.amazon.dataprepper.model.record.Record;
import com.amazon.dataprepper.plugins.processor.mutateevent.AddProcessor;
import com.amazon.dataprepper.plugins.processor.mutateevent.AddProcessorConfig;
import org.junit.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@RunWith(MockitoJUnitRunner.class)
public class AddProcessorTests {
    @Mock
    private PluginMetrics pluginMetrics;

    @Mock
    private AddProcessorConfig mockConfig;

    @InjectMocks
    private AddProcessor processor;

    @Test
    public void testAddProcessorTests() {
        when(mockConfig.getKey()).thenReturn("newMessage");
        when(mockConfig.getValue()).thenReturn(3);
        final Record<Event> record = getMessage("thisisamessage");
        final List<Record<Event>> editedRecords = (List<Record<Event>>) processor.doExecute(Collections.singletonList(record));

        assertThat(editedRecords.get(0).getData().containsKey("newMessage"), is(true));
        assertThat(editedRecords.get(0).getData().get("newMessage", Object.class), equalTo(3));
    }

    @Test
    public void testAddNoOverwriteMutateEventProcessorTests() {
        when(mockConfig.getKey()).thenReturn("newMessage");
        when(mockConfig.getValue()).thenReturn(3);
        when(mockConfig.getSkipIfPresent()).thenReturn(true);
        final Record<Event> record = getMessage("thisisamessage");
        record.getData().put("newMessage", "test");
        final List<Record<Event>> editedRecords = (List<Record<Event>>) processor.doExecute(Collections.singletonList(record));

        assertThat(editedRecords.get(0).getData().containsKey("newMessage"), is(true));
        assertThat(editedRecords.get(0).getData().get("newMessage", Object.class), equalTo("test"));
    }

    private Record<Event> getMessage(String message) {
        final Map<String, Object> testData = new HashMap();
        testData.put("message", message);
        return buildRecordWithEvent(testData);
    }

    private static Record<Event> buildRecordWithEvent(final Map<String, Object> data) {
        return new Record<>(JacksonEvent.builder()
                .withData(data)
                .withEventType("event")
                .build());
    }
}
