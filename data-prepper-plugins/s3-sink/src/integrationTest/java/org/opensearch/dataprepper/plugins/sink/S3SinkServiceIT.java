/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.sink;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.dataprepper.metrics.PluginMetrics;
import org.opensearch.dataprepper.model.event.Event;
import org.opensearch.dataprepper.model.event.EventHandle;
import org.opensearch.dataprepper.model.event.JacksonEvent;
import org.opensearch.dataprepper.model.log.JacksonLog;
import org.opensearch.dataprepper.model.record.Record;
import org.opensearch.dataprepper.model.types.ByteCount;
import org.opensearch.dataprepper.plugins.sink.accumulator.BufferFactory;
import org.opensearch.dataprepper.plugins.sink.accumulator.InMemoryBufferFactory;
import org.opensearch.dataprepper.plugins.sink.accumulator.ObjectKey;
import org.opensearch.dataprepper.plugins.sink.codec.JsonCodec;
import org.opensearch.dataprepper.plugins.sink.configuration.AwsAuthenticationOptions;
import org.opensearch.dataprepper.plugins.sink.configuration.BucketOptions;
import org.opensearch.dataprepper.plugins.sink.configuration.ObjectKeyOptions;
import org.opensearch.dataprepper.plugins.sink.configuration.ThresholdOptions;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsResponse;
import software.amazon.awssdk.services.s3.model.S3Object;

@ExtendWith(MockitoExtension.class)
class S3SinkServiceIT {

    private static final String PATH_PREFIX = UUID.randomUUID().toString() + "/%{yyyy}/%{MM}/%{dd}/";
    private S3Client s3Client;
    private String bucketName;
    private JsonCodec codec;
    private BufferFactory bufferFactory;
    @Mock
    private S3SinkConfig s3SinkConfig;
    @Mock
    private ThresholdOptions thresholdOptions;
    @Mock
    private AwsAuthenticationOptions awsAuthenticationOptions;
    @Mock
    private ObjectKeyOptions objectKeyOptions;
    @Mock
    private BucketOptions bucketOptions;
    @Mock
    private PluginMetrics pluginMetrics;
    @Mock
    private Counter snapshotSuccessCounter;
    @Mock
    private Counter snapshotFailedCounter;
    @Mock
    private Counter numberOfRecordsSuccessCounter;
    @Mock
    private Counter numberOfRecordsFailedCounter;
    @Mock
    private DistributionSummary s3ObjectSizeSummary;


    @BeforeEach
    public void setUp() {
        String s3region = System.getProperty("tests.s3ink.region");

        s3Client = S3Client.builder().region(Region.of(s3region)).build();
        bucketName = System.getProperty("tests.s3sink.bucket");

        codec = new JsonCodec();
        bufferFactory = new InMemoryBufferFactory();

        when(objectKeyOptions.getNamePattern()).thenReturn("elb-log-%{yyyy-MM-dd'T'hh-mm-ss}");
        when(objectKeyOptions.getPathPrefix()).thenReturn(PATH_PREFIX);
        when(bucketOptions.getBucketName()).thenReturn(bucketName);
        when(bucketOptions.getObjectKeyOptions()).thenReturn(objectKeyOptions);
        when(thresholdOptions.getEventCount()).thenReturn(2);
        when(thresholdOptions.getMaximumSize()).thenReturn(ByteCount.parse("2mb"));
        when(thresholdOptions.getEventCollectTimeOut()).thenReturn(Duration.parse("PT3M"));
        when(s3SinkConfig.getThresholdOptions()).thenReturn(thresholdOptions);
        when(s3SinkConfig.getBucketOptions()).thenReturn(bucketOptions);
        when(s3SinkConfig.getAwsAuthenticationOptions()).thenReturn(awsAuthenticationOptions);
        when(awsAuthenticationOptions.getAwsRegion()).thenReturn(Region.of(s3region));

        lenient().when(pluginMetrics.counter(S3SinkService.OBJECTS_SUCCEEDED)).thenReturn(snapshotSuccessCounter);
        lenient().when(pluginMetrics.counter(S3SinkService.OBJECTS_FAILED)).thenReturn(snapshotFailedCounter);
        lenient().when(pluginMetrics.counter(S3SinkService.NUMBER_OF_RECORDS_FLUSHED_TO_S3_SUCCESS)).
                thenReturn(numberOfRecordsSuccessCounter);
        lenient().when(pluginMetrics.counter(S3SinkService.NUMBER_OF_RECORDS_FLUSHED_TO_S3_FAILED)).
                thenReturn(numberOfRecordsFailedCounter);
        lenient().when(pluginMetrics.summary(S3SinkService.S3_OBJECTS_SIZE)).thenReturn(s3ObjectSizeSummary);
    }

    @Test
    void verify_flushed_object_count_into_s3_bucket() {
        int s3ObjectCountBeforeIngest = gets3ObjectCount();
        S3SinkService s3SinkService = createObjectUnderTest();
        s3SinkService.output(setEventQueue());
        int s3ObjectCountAfterIngest = gets3ObjectCount();
        assertThat(s3ObjectCountAfterIngest, equalTo(s3ObjectCountBeforeIngest + 1));
    }

    @Test
    void verify_flushed_records_into_s3_bucket() {

        S3SinkService s3SinkService = createObjectUnderTest();
        Collection<Record<Event>> recordsData = setEventQueue();

        s3SinkService.output(recordsData);
        String objectData = getS3Object();

        for (Record<Event> recordData : recordsData) {
            String objectRecord = recordData.getData().toJsonString();
            assertThat(objectData, CoreMatchers.containsString(objectRecord));
        }
    }

    private S3SinkService createObjectUnderTest() {
        return new S3SinkService(s3SinkConfig, bufferFactory, codec, pluginMetrics);
    }

    private int gets3ObjectCount() {
        int s3ObjectCount = 0;
        ListObjectsRequest listObjects = ListObjectsRequest.builder()
                .bucket(bucketName).prefix(getPathPrefix())
                .build();
        ListObjectsResponse res = s3Client.listObjects(listObjects);
        List<S3Object> objects = res.contents();
        s3ObjectCount = objects.size();

        return s3ObjectCount;
    }

    private String getS3Object() {

        ListObjectsRequest listObjects = ListObjectsRequest.builder()
                .bucket(bucketName)
                .prefix(getPathPrefix())
                .build();
        ListObjectsResponse res = s3Client.listObjects(listObjects);
        List<S3Object> objects = res.contents();

        S3Object object = objects.get(objects.size() - 1);

        String objectKey = object.key();
        GetObjectRequest objectRequest = GetObjectRequest
                .builder()
                .key(objectKey)
                .bucket(bucketName).build();

        ResponseBytes<GetObjectResponse> objectBytes = s3Client.getObjectAsBytes(objectRequest);
        byte[] data = objectBytes.asByteArray();
        return new String(data);
    }

    private String getPathPrefix() {
        return ObjectKey.buildingPathPrefix(s3SinkConfig);
    }

    private Collection<Record<Event>> setEventQueue() {
        final Collection<Record<Event>> jsonObjects = new LinkedList<>();
        for (int i = 0; i < 2; i++)
            jsonObjects.add(createRecord());
        return jsonObjects;
    }

    private static Record<Event> createRecord() {
        Map<String, Object> json = generateJson();
        final JacksonEvent event = JacksonLog.builder().withData(json).build();
        event.setEventHandle(mock(EventHandle.class));
        return new Record<>(event);
    }

    private static Map<String, Object> generateJson() {
        final Map<String, Object> jsonObject = new LinkedHashMap<>();
        for (int i = 0; i < 2; i++) {
            jsonObject.put(UUID.randomUUID().toString(), UUID.randomUUID().toString());
        }
        jsonObject.put(UUID.randomUUID().toString(), Arrays.asList(UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), UUID.randomUUID().toString()));
        return jsonObject;
    }
}