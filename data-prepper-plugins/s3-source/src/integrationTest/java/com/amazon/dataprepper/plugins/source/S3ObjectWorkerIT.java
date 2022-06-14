/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.dataprepper.plugins.source;

import com.amazon.dataprepper.metrics.PluginMetrics;
import com.amazon.dataprepper.model.buffer.Buffer;
import com.amazon.dataprepper.model.event.Event;
import com.amazon.dataprepper.model.record.Record;
import com.amazon.dataprepper.plugins.source.codec.Codec;
import com.amazon.dataprepper.plugins.source.compression.CompressionEngine;
import com.amazon.dataprepper.plugins.source.compression.NoneCompressionEngine;
import io.micrometer.core.instrument.Counter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.amazon.dataprepper.plugins.source.S3ObjectWorker.S3_OBJECTS_FAILED_METRIC_NAME;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class S3ObjectWorkerIT {
    private static final int TIMEOUT_IN_MILLIS = 200;
    private S3Client s3Client;
    private S3ObjectGenerator s3ObjectGenerator;
    private Buffer<Record<Event>> buffer;
    private CompressionEngine compressionEngine;
    private String bucket;
    private int recordsReceived;
    private PluginMetrics pluginMetrics;

    @BeforeEach
    void setUp() {
        s3Client = S3Client.builder()
                .region(Region.of(System.getProperty("tests.s3source.region")))
                .build();
        bucket = System.getProperty("tests.s3source.bucket");
        s3ObjectGenerator = new S3ObjectGenerator(s3Client, bucket);

        buffer = mock(Buffer.class);
        compressionEngine = new NoneCompressionEngine();
        recordsReceived = 0;

        pluginMetrics = mock(PluginMetrics.class);
        final Counter counter = mock(Counter.class);
        when(pluginMetrics.counter(S3_OBJECTS_FAILED_METRIC_NAME)).thenReturn(counter);
    }

    private void stubBufferWriter(final Consumer<Event> additionalEventAssertions) throws Exception {
        doAnswer(a -> {
            final Collection<Record<Event>> recordsCollection = a.getArgument(0);
            assertThat(recordsCollection.size(), greaterThanOrEqualTo(1));
            for (Record<Event> eventRecord : recordsCollection) {
                assertThat(eventRecord, notNullValue());
                assertThat(eventRecord.getData(), notNullValue());
                additionalEventAssertions.accept(eventRecord.getData());

            }
            recordsReceived += recordsCollection.size();
            return null;
        })
                .when(buffer).writeAll(anyCollection(), anyInt());
    }

    private S3ObjectWorker createObjectUnderTest(final Codec codec, final int numberOfRecordsToAccumulate) {
        return new S3ObjectWorker(s3Client, buffer, compressionEngine, codec, Duration.ofMillis(TIMEOUT_IN_MILLIS), numberOfRecordsToAccumulate, pluginMetrics);
    }

    @ParameterizedTest
    @ArgumentsSource(IntegrationTestArguments.class)
    void parseS3Object_correctly_loads_data_into_Buffer(
            final RecordsGenerator recordsGenerator,
            final int numberOfRecords,
            final int numberOfRecordsToAccumulate) throws Exception {

        final String key = "s3source/s3/" + numberOfRecords + "_" + Instant.now().toString() + recordsGenerator.getFileExtension();
        s3ObjectGenerator.write(numberOfRecords, key, recordsGenerator);

        final S3ObjectWorker objectUnderTest = createObjectUnderTest(recordsGenerator.getCodec(), numberOfRecordsToAccumulate);

        stubBufferWriter(recordsGenerator::assertEventIsCorrect);

        parseObject(key, objectUnderTest);

        final int expectedWrites = numberOfRecords / numberOfRecordsToAccumulate + (numberOfRecords % numberOfRecordsToAccumulate != 0 ? 1 : 0);

        verify(buffer, times(expectedWrites)).writeAll(anyCollection(), eq(TIMEOUT_IN_MILLIS));

        assertThat(recordsReceived, equalTo(numberOfRecords));
    }

    private void parseObject(final String key, final S3ObjectWorker objectUnderTest) throws IOException {
        final S3ObjectReference s3ObjectReference = S3ObjectReference.fromBucketAndKey(bucket, key);
        objectUnderTest.parseS3Object(s3ObjectReference);
    }

    static class IntegrationTestArguments implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(final ExtensionContext context) {
            final List<RecordsGenerator> recordsGenerators = List.of(new NewlineDelimitedRecordsGenerator(), new JsonRecordsGenerator());
            final List<Integer> numberOfRecordsList = List.of(0, 1, 25, 500, 5000);
            final List<Integer> recordsToAccumulateList = List.of(1, 100, 1000);

            return recordsGenerators
                    .stream()
                    .flatMap(recordsGenerator ->
                            numberOfRecordsList
                                    .stream()
                                    .flatMap(records -> recordsToAccumulateList
                                            .stream()
                                            .map(accumulate -> arguments(recordsGenerator, records, accumulate))
                                    ));
        }
    }
}
