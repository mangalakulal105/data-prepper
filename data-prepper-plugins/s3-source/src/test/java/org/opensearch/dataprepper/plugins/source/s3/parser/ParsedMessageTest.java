package org.opensearch.dataprepper.plugins.source.s3.parser;

import org.joda.time.DateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.dataprepper.plugins.source.s3.S3EventBridgeNotification;
import org.opensearch.dataprepper.plugins.source.s3.S3EventNotification;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ParsedMessageTest {
    private static final Random RANDOM = new Random();
    private Message message;
    private S3EventNotification.S3Entity s3Entity;
    private S3EventNotification.S3BucketEntity s3BucketEntity;
    private S3EventNotification.S3ObjectEntity s3ObjectEntity;
    private S3EventNotification.S3EventNotificationRecord s3EventNotificationRecord;
    private S3EventBridgeNotification s3EventBridgeNotification;
    private S3EventBridgeNotification.Detail detail;
    private S3EventBridgeNotification.Bucket bucket;
    private S3EventBridgeNotification.Object object;

    @BeforeEach
    void setUp() {
        message = mock(Message.class);
        s3Entity = mock(S3EventNotification.S3Entity.class);
        s3BucketEntity = mock(S3EventNotification.S3BucketEntity.class);
        s3ObjectEntity = mock(S3EventNotification.S3ObjectEntity.class);
        s3EventNotificationRecord = mock(S3EventNotification.S3EventNotificationRecord.class);
        s3EventBridgeNotification = mock(S3EventBridgeNotification.class);
        detail = mock(S3EventBridgeNotification.Detail.class);
        bucket = mock(S3EventBridgeNotification.Bucket.class);
        object = mock(S3EventBridgeNotification.Object.class);
    }

    @Test
    void test_parsed_message_with_failed_parsing() {
        final ParsedMessage parsedMessage = new ParsedMessage(message, true);
        assertThat(parsedMessage.getMessage(), equalTo(message));
        assertThat(parsedMessage.isFailedParsing(), equalTo(true));
        assertThat(parsedMessage.isEmptyNotification(), equalTo(true));
    }

    @Test
    void test_parsed_message_with_S3EventNotificationRecord() {
        final String  testBucketName = UUID.randomUUID().toString();
        final String  testDecodedObjectKey = UUID.randomUUID().toString();
        final String  testEventName = UUID.randomUUID().toString();
        final DateTime testEventTime = DateTime.now();
        final long testSize = RANDOM.nextLong();

        when(s3EventNotificationRecord.getS3()).thenReturn(s3Entity);
        when(s3Entity.getBucket()).thenReturn(s3BucketEntity);
        when(s3Entity.getObject()).thenReturn(s3ObjectEntity);
        when(s3ObjectEntity.getSizeAsLong()).thenReturn(testSize);
        when(s3BucketEntity.getName()).thenReturn(testBucketName);
        when(s3ObjectEntity.getUrlDecodedKey()).thenReturn(testDecodedObjectKey);
        when(s3EventNotificationRecord.getEventName()).thenReturn(testEventName);
        when(s3EventNotificationRecord.getEventTime()).thenReturn(testEventTime);

        final ParsedMessage parsedMessage = new ParsedMessage(message, List.of(s3EventNotificationRecord));

        assertThat(parsedMessage.getMessage(), equalTo(message));
        assertThat(parsedMessage.getBucketName(), equalTo(testBucketName));
        assertThat(parsedMessage.getObjectKey(), equalTo(testDecodedObjectKey));
        assertThat(parsedMessage.getObjectSize(), equalTo(testSize));
        assertThat(parsedMessage.getEventName(), equalTo(testEventName));
        assertThat(parsedMessage.getEventTime(), equalTo(testEventTime));
        assertThat(parsedMessage.isFailedParsing(), equalTo(false));
        assertThat(parsedMessage.isEmptyNotification(), equalTo(false));
    }

    @Test
    void test_parsed_message_with_S3EventBridgeNotification() {
        final String  testBucketName = UUID.randomUUID().toString();
        final String  testDecodedObjectKey = UUID.randomUUID().toString();
        final String  testDetailType = UUID.randomUUID().toString();
        final DateTime testEventTime = DateTime.now();
        final int testSize = RANDOM.nextInt();

        when(s3EventBridgeNotification.getDetail()).thenReturn(detail);
        when(s3EventBridgeNotification.getDetail().getBucket()).thenReturn(bucket);
        when(s3EventBridgeNotification.getDetail().getObject()).thenReturn(object);

        when(bucket.getName()).thenReturn(testBucketName);
        when(object.getUrlDecodedKey()).thenReturn(testDecodedObjectKey);
        when(object.getSize()).thenReturn(testSize);
        when(s3EventBridgeNotification.getDetailType()).thenReturn(testDetailType);
        when(s3EventBridgeNotification.getTime()).thenReturn(testEventTime);

        final ParsedMessage parsedMessage = new ParsedMessage(message, s3EventBridgeNotification);

        assertThat(parsedMessage.getMessage(), equalTo(message));
        assertThat(parsedMessage.getBucketName(), equalTo(testBucketName));
        assertThat(parsedMessage.getObjectKey(), equalTo(testDecodedObjectKey));
        assertThat(parsedMessage.getObjectSize(), equalTo((long) testSize));
        assertThat(parsedMessage.getDetailType(), equalTo(testDetailType));
        assertThat(parsedMessage.getEventTime(), equalTo(testEventTime));
        assertThat(parsedMessage.isFailedParsing(), equalTo(false));
        assertThat(parsedMessage.isEmptyNotification(), equalTo(false));
    }
}
