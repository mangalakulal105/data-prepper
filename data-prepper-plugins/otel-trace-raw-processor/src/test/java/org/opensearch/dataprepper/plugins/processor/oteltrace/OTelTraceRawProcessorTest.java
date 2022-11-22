/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.processor.oteltrace;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.dataprepper.metrics.MetricsTestUtil;
import org.opensearch.dataprepper.model.configuration.PluginSetting;
import org.opensearch.dataprepper.model.record.Record;
import org.opensearch.dataprepper.model.trace.DefaultTraceGroupFields;
import org.opensearch.dataprepper.model.trace.JacksonSpan;
import org.opensearch.dataprepper.model.trace.Span;
import org.opensearch.dataprepper.model.trace.TraceGroupFields;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OTelTraceRawProcessorTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final long TEST_TRACE_FLUSH_INTERVAL = 3L;
    private static final int TEST_CONCURRENCY_SCALE = 2;

    private static final String TEST_TRACE_GROUP_1_ROOT_SPAN_JSON_FILE = "trace-group-1-root-span.json";
    private static final String TEST_TRACE_GROUP_1_CHILD_SPAN_1_JSON_FILE = "trace-group-1-child-span-1.json";
    private static final String TEST_TRACE_GROUP_1_CHILD_SPAN_2_JSON_FILE = "trace-group-1-child-span-2.json";
    private static final String TEST_TRACE_GROUP_2_ROOT_SPAN_JSON_FILE = "trace-group-2-root-span.json";
    private static final String TEST_TRACE_GROUP_2_CHILD_SPAN_1_JSON_FILE = "trace-group-2-child-span-1.json";
    private static final String TEST_TRACE_GROUP_2_CHILD_SPAN_2_JSON_FILE = "trace-group-2-child-span-2.json";

    private Span TEST_TRACE_GROUP_1_ROOT_SPAN;
    private Span TEST_TRACE_GROUP_1_CHILD_SPAN_1;
    private Span TEST_TRACE_GROUP_1_CHILD_SPAN_2;
    private Span TEST_TRACE_GROUP_2_ROOT_SPAN;
    private Span TEST_TRACE_GROUP_2_CHILD_SPAN_1;
    private Span TEST_TRACE_GROUP_2_CHILD_SPAN_2;

    private List<Record<Span>> TEST_ONE_FULL_TRACE_GROUP_RECORDS;
    private List<Record<Span>> TEST_ONE_TRACE_GROUP_MISSING_ROOT_RECORDS;
    private List<Record<Span>> TEST_TWO_FULL_TRACE_GROUP_RECORDS;
    private List<Record<Span>> TEST_TWO_TRACE_GROUP_INTERLEAVED_PART_1_RECORDS;
    private List<Record<Span>> TEST_TWO_TRACE_GROUP_INTERLEAVED_PART_2_RECORDS;
    private List<Record<Span>> TEST_TWO_TRACE_GROUP_MISSING_ROOT_RECORDS;

    PluginSetting pluginSetting;
    public OTelTraceRawProcessor oTelTraceRawProcessor;
    public ExecutorService executorService;

    @BeforeEach
    public void setup() {
        TEST_TRACE_GROUP_1_ROOT_SPAN = buildSpanFromJsonFile(TEST_TRACE_GROUP_1_ROOT_SPAN_JSON_FILE);
        TEST_TRACE_GROUP_1_CHILD_SPAN_1 = buildSpanFromJsonFile(TEST_TRACE_GROUP_1_CHILD_SPAN_1_JSON_FILE);
        TEST_TRACE_GROUP_1_CHILD_SPAN_2 = buildSpanFromJsonFile(TEST_TRACE_GROUP_1_CHILD_SPAN_2_JSON_FILE);
        TEST_TRACE_GROUP_2_ROOT_SPAN = buildSpanFromJsonFile(TEST_TRACE_GROUP_2_ROOT_SPAN_JSON_FILE);
        TEST_TRACE_GROUP_2_CHILD_SPAN_1 = buildSpanFromJsonFile(TEST_TRACE_GROUP_2_CHILD_SPAN_1_JSON_FILE);
        TEST_TRACE_GROUP_2_CHILD_SPAN_2 = buildSpanFromJsonFile(TEST_TRACE_GROUP_2_CHILD_SPAN_2_JSON_FILE);
        TEST_ONE_FULL_TRACE_GROUP_RECORDS = Stream.of(
                        TEST_TRACE_GROUP_1_ROOT_SPAN, TEST_TRACE_GROUP_1_CHILD_SPAN_1, TEST_TRACE_GROUP_1_CHILD_SPAN_2)
                .map(Record::new).collect(Collectors.toList());
        TEST_ONE_TRACE_GROUP_MISSING_ROOT_RECORDS = Stream.of(
                        TEST_TRACE_GROUP_2_CHILD_SPAN_1, TEST_TRACE_GROUP_2_CHILD_SPAN_2)
                .map(Record::new).collect(Collectors.toList());
        TEST_TWO_FULL_TRACE_GROUP_RECORDS = Stream.of(
                        TEST_TRACE_GROUP_1_ROOT_SPAN, TEST_TRACE_GROUP_1_CHILD_SPAN_1, TEST_TRACE_GROUP_1_CHILD_SPAN_2,
                        TEST_TRACE_GROUP_2_ROOT_SPAN, TEST_TRACE_GROUP_2_CHILD_SPAN_1, TEST_TRACE_GROUP_2_CHILD_SPAN_2)
                .map(Record::new).collect(Collectors.toList());
        TEST_TWO_TRACE_GROUP_INTERLEAVED_PART_1_RECORDS = Stream.of(
                        TEST_TRACE_GROUP_1_ROOT_SPAN, TEST_TRACE_GROUP_2_CHILD_SPAN_1, TEST_TRACE_GROUP_2_CHILD_SPAN_2)
                .map(Record::new).collect(Collectors.toList());
        TEST_TWO_TRACE_GROUP_INTERLEAVED_PART_2_RECORDS = Stream.of(
                        TEST_TRACE_GROUP_2_ROOT_SPAN, TEST_TRACE_GROUP_1_CHILD_SPAN_1, TEST_TRACE_GROUP_1_CHILD_SPAN_2)
                .map(Record::new).collect(Collectors.toList());
        TEST_TWO_TRACE_GROUP_MISSING_ROOT_RECORDS = Stream.of(
                        TEST_TRACE_GROUP_1_CHILD_SPAN_1, TEST_TRACE_GROUP_1_CHILD_SPAN_2,
                        TEST_TRACE_GROUP_2_CHILD_SPAN_1, TEST_TRACE_GROUP_2_CHILD_SPAN_2)
                .map(Record::new).collect(Collectors.toList());

        MetricsTestUtil.initMetrics();
        pluginSetting = new PluginSetting(
                "OTelTrace",
                Map.of(OtelTraceRawProcessorConfig.TRACE_FLUSH_INTERVAL, TEST_TRACE_FLUSH_INTERVAL));
        pluginSetting.setPipelineName("pipelineOTelTrace");
        pluginSetting.setProcessWorkers(TEST_CONCURRENCY_SCALE);
        oTelTraceRawProcessor = new OTelTraceRawProcessor(pluginSetting);
        executorService = Executors.newFixedThreadPool(TEST_CONCURRENCY_SCALE);
    }

    @AfterEach
    public void tearDown() {
        oTelTraceRawProcessor.shutdown();
        executorService.shutdown();
    }

    @Test
    public void testEmptyCollection() {
        assertThat(oTelTraceRawProcessor.doExecute(Collections.EMPTY_LIST)).isEmpty();
    }

    @Test
    public void testExportRequestFlushByParentSpan() {
        final List<Record<Span>> processedRecords = (List<Record<Span>>) oTelTraceRawProcessor.doExecute(TEST_TWO_FULL_TRACE_GROUP_RECORDS);

        Assertions.assertThat(processedRecords.size()).isEqualTo(6);
        Assertions.assertThat(getMissingTraceGroupFieldsSpanCount(processedRecords)).isEqualTo(0);
    }

    @Test
    public void testExportRequestFlushByParentSpanMultiThread() throws InterruptedException, ExecutionException {
        final List<Record<Span>> processedRecords = new ArrayList<>();
        List<Future<Collection<Record<Span>>>> futures = new ArrayList<>();
        futures.addAll(submitRecords(TEST_TWO_TRACE_GROUP_INTERLEAVED_PART_1_RECORDS));
        futures.addAll(submitRecords(TEST_TWO_TRACE_GROUP_INTERLEAVED_PART_2_RECORDS));
        for (Future<Collection<Record<Span>>> future : futures) {
            processedRecords.addAll(future.get());
        }
        await().atMost(2 * TEST_TRACE_FLUSH_INTERVAL, TimeUnit.SECONDS).untilAsserted(() -> {
            List<Future<Collection<Record<Span>>>> futureList = submitRecords(Collections.emptyList());
            for (Future<Collection<Record<Span>>> future : futureList) {
                processedRecords.addAll(future.get());
            }
            Assertions.assertThat(processedRecords.size()).isEqualTo(6);
            Assertions.assertThat(getMissingTraceGroupFieldsSpanCount(processedRecords)).isEqualTo(0);
        });
    }

    @Test
    public void testExportRequestFlushByGC() {
        oTelTraceRawProcessor.doExecute(TEST_TWO_TRACE_GROUP_MISSING_ROOT_RECORDS);
        await().atMost(2 * TEST_TRACE_FLUSH_INTERVAL, TimeUnit.SECONDS).untilAsserted(() -> {
            final List<Record<Span>> processedRecords = (List<Record<Span>>) oTelTraceRawProcessor.doExecute(Collections.emptyList());
            Assertions.assertThat(processedRecords.size()).isEqualTo(4);
            Assertions.assertThat(getMissingTraceGroupFieldsSpanCount(processedRecords)).isEqualTo(4);
        });
    }

    @Test
    public void testExportRequestFlushByMixedMultiThread() throws InterruptedException, ExecutionException {
        List<Record<Span>> processedRecords = new ArrayList<>();
        List<Future<Collection<Record<Span>>>> futures = new ArrayList<>();
        futures.addAll(submitRecords(TEST_ONE_FULL_TRACE_GROUP_RECORDS));
        futures.addAll(submitRecords(TEST_ONE_TRACE_GROUP_MISSING_ROOT_RECORDS));
        for (Future<Collection<Record<Span>>> future : futures) {
            processedRecords.addAll(future.get());
        }
        await().atMost(2 * TEST_TRACE_FLUSH_INTERVAL, TimeUnit.SECONDS).untilAsserted(() -> {
            List<Future<Collection<Record<Span>>>> futureList = submitRecords(Collections.emptyList());
            for (Future<Collection<Record<Span>>> future : futureList) {
                processedRecords.addAll(future.get());
            }
            Assertions.assertThat(processedRecords.size()).isEqualTo(5);
            Assertions.assertThat(getMissingTraceGroupFieldsSpanCount(processedRecords)).isEqualTo(2);
        });
    }

    @Test
    public void testPrepareForShutdown() {
        // Assert no records in memory
        assertTrue(oTelTraceRawProcessor.isReadyForShutdown());

        // Add records to memory/queue
        oTelTraceRawProcessor.doExecute(TEST_TWO_TRACE_GROUP_MISSING_ROOT_RECORDS);

        // Assert records exist in memory
        assertFalse(oTelTraceRawProcessor.isReadyForShutdown());

        // Force records to be flushed
        oTelTraceRawProcessor.prepareForShutdown();
        oTelTraceRawProcessor.doExecute(Collections.emptyList());

        // Assert records have been flushed
        assertTrue(oTelTraceRawProcessor.isReadyForShutdown());
    }

    @Test
    public void testGetIdentificationKeys() {
        final Collection<String> expectedIdentificationKeys = oTelTraceRawProcessor.getIdentificationKeys();
        assertThat(expectedIdentificationKeys, equalTo(Collections.singleton("traceId")));
    }

    private static Span buildSpanFromJsonFile(final String jsonFileName) {
        JacksonSpan.Builder spanBuilder = JacksonSpan.builder();
        try (final InputStream inputStream = Objects.requireNonNull(
                OTelTraceRawProcessorTest.class.getClassLoader().getResourceAsStream(jsonFileName))){
            final Map<String, Object> spanMap = OBJECT_MAPPER.readValue(inputStream, new TypeReference<Map<String, Object>>() {});
            final String traceId = (String) spanMap.get("traceId");
            final String spanId = (String) spanMap.get("spanId");
            final String parentSpanId = (String) spanMap.get("parentSpanId");
            final String traceState = (String) spanMap.get("traceState");
            final String name = (String) spanMap.get("name");
            final String kind = (String) spanMap.get("kind");
            final Long durationInNanos = ((Number) spanMap.get("durationInNanos")).longValue();
            final String startTime = (String) spanMap.get("startTime");
            final String endTime = (String) spanMap.get("endTime");
            spanBuilder = spanBuilder
                    .withTraceId(traceId)
                    .withSpanId(spanId)
                    .withParentSpanId(parentSpanId)
                    .withTraceState(traceState)
                    .withName(name)
                    .withKind(kind)
                    .withDurationInNanos(durationInNanos)
                    .withStartTime(startTime)
                    .withEndTime(endTime)
                    .withTraceGroup(null);
            DefaultTraceGroupFields.Builder traceGroupFieldsBuilder = DefaultTraceGroupFields.builder();
            if (parentSpanId.isEmpty()) {
                final Integer statusCode = (Integer) ((Map<String, Object>) spanMap.get("traceGroupFields")).get("statusCode");
                traceGroupFieldsBuilder = traceGroupFieldsBuilder
                        .withStatusCode(statusCode)
                        .withEndTime(endTime)
                        .withDurationInNanos(durationInNanos);
                final String traceGroup = (String) spanMap.get("traceGroup");
                spanBuilder = spanBuilder.withTraceGroup(traceGroup);
            }
            spanBuilder.withTraceGroupFields(traceGroupFieldsBuilder.build());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return spanBuilder.build();
    }

    private List<Future<Collection<Record<Span>>>> submitRecords(Collection<Record<Span>> records) {
        final List<Future<Collection<Record<Span>>>> futures = new ArrayList<>();
        futures.add(executorService.submit(() -> oTelTraceRawProcessor.doExecute(records)));
        return futures;
    }

    private int getMissingTraceGroupFieldsSpanCount(List<Record<Span>> records) {
        int count = 0;
        for (Record<Span> record: records) {
            final Span span = record.getData();
            final String traceGroup = span.getTraceGroup();
            final TraceGroupFields traceGroupFields = span.getTraceGroupFields();
            if (Stream.of(
                    traceGroup,
                    traceGroupFields.getEndTime(),
                    traceGroupFields.getDurationInNanos(),
                    traceGroupFields.getStatusCode()).allMatch(Objects::isNull)) {
                count += 1;
            }
        }
        return count;
    }
}

