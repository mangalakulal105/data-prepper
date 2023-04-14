/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.codec.parquet;

import org.apache.avro.SchemaBuilder;
import org.apache.hadoop.fs.FileSystem;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.dataprepper.model.event.Event;
import org.opensearch.dataprepper.model.event.EventType;
import org.opensearch.dataprepper.model.log.JacksonLog;
import org.opensearch.dataprepper.model.record.Record;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;



@ExtendWith(MockitoExtension.class)
class ParquetInputCodecTest {

    @Mock
    private Consumer<Record<Event>> eventConsumer;

    private ParquetInputCodec parquetInputCodec;

    private static FileInputStream fileInputStream;

    private static int numberOfRecords;

    private static final String FILE_NAME="test-parquet.parquet";

    private static final String INVALID_PARQUET_INPUT_STREAM = "Invalid Parquet Input Stream";

    @TempDir
    private static java.nio.file.Path path;

    private ParquetInputCodec createObjectUnderTest() {
        return new ParquetInputCodec();
    }

    @BeforeEach
    void setup() {
        parquetInputCodec = createObjectUnderTest();
    }

    @Test
    void test_when_nullInputStream_then_throwsException() {
        assertThrows(NullPointerException.class, () ->
                parquetInputCodec.parse(null, eventConsumer));

        verifyNoInteractions(eventConsumer);
    }

    @Test
    void parse_with_null_Consumer_throws()  {
        parquetInputCodec = createObjectUnderTest();

        final InputStream inputStream = mock(InputStream.class);
        assertThrows(NullPointerException.class, () ->
                parquetInputCodec.parse(inputStream, null));
        verifyNoInteractions(inputStream);
    }

    @Test
    void parse_with_empty_InputStream_does_not_call_Consumer() throws IOException {
        final ByteArrayInputStream emptyInputStream = new ByteArrayInputStream(new byte[]{});
        createObjectUnderTest().parse(emptyInputStream, eventConsumer);
        verifyNoInteractions(eventConsumer);
    }

    @Test
    void parse_with_Invalid_InputStream_then_catches_exception() {
        Consumer<Record<Event>> eventConsumer = mock(Consumer.class);
        Assertions.assertDoesNotThrow(()->
                parquetInputCodec.parse(createInvalidParquetStream(),eventConsumer));
    }

    @ParameterizedTest
    @ValueSource(ints = {1,10,100,1000})
    void test_when_HappyCaseParquetInputStream_then_callsConsumerWithParsedEvents(final int numberOfRecords) throws Exception {
        ParquetInputCodecTest.numberOfRecords =numberOfRecords;
        InputStream inputStream = createRandomParquetStream(numberOfRecords);

        parquetInputCodec.parse(inputStream,eventConsumer);

        final ArgumentCaptor<Record<Event>> recordArgumentCaptor = ArgumentCaptor.forClass(Record.class);
        verify(eventConsumer, times(numberOfRecords)).accept(recordArgumentCaptor.capture());
        final List<Record<Event>> actualRecords = recordArgumentCaptor.getAllValues();
        assertThat(actualRecords.size(), equalTo(numberOfRecords));
        int index=0;
        for (final Record<Event> actualRecord : actualRecords) {

            assertThat(actualRecord, notNullValue());
            assertThat(actualRecord.getData(), notNullValue());
            assertThat(actualRecord.getData().getMetadata(), notNullValue());
            assertThat(actualRecord.getData().getMetadata().getEventType(), equalTo(EventType.LOG.toString()));
            Object expectedMap=getExpectedOutput(index).toMap();
            assertThat(actualRecord.getData().toMap(), equalTo(expectedMap));
            index++;
        }

        fileInputStream.close();
        FileSystem fs = FileSystem.get(new Configuration());
        String filepath = path.toUri().getPath();
        fs.delete(new Path(filepath));
        fs.close();
    }

    private static InputStream createRandomParquetStream(int numberOfRecords) throws IOException {

        Files.deleteIfExists(java.nio.file.Path.of(FILE_NAME));
        Schema schema = parseSchema();

        List<GenericRecord> recordList = generateRecords(schema, numberOfRecords);

        path = Paths.get(FILE_NAME);

        try(ParquetWriter<GenericData.Record> writer = AvroParquetWriter.<GenericData.Record>builder(new Path(path.toUri().getPath()))
                .withSchema(schema)
                .withCompressionCodec(CompressionCodecName.SNAPPY)
                .withRowGroupSize(ParquetWriter.DEFAULT_BLOCK_SIZE)
                .withPageSize(ParquetWriter.DEFAULT_PAGE_SIZE)
                .withConf(new Configuration())
                .withValidation(false)
                .withDictionaryEncoding(false)
                .build()) {
            for (GenericRecord record : recordList) {
                writer.write((GenericData.Record)record);
            }
        }
        fileInputStream = new FileInputStream(path.toString());
        return fileInputStream;
    }

    private static Schema parseSchema() {
        return SchemaBuilder.record("Person")
                .fields()
                .name("name").type().stringType().noDefault()
                .name("age").type().intType().noDefault()
                .endRecord();
    }

    private static Event getExpectedOutput(int index) {
        List<GenericRecord> recordList=generateRecords(parseSchema(),numberOfRecords);
        GenericRecord record=recordList.get(index);
        Schema schema=parseSchema();
        final Map<String, Object> eventData = new HashMap<>();
        for(Schema.Field field : schema.getFields()) {

            eventData.put(field.name(), record.get(field.name()));

        }
        final Event event = JacksonLog.builder().withData(eventData).build();
        return event;
    }

    private static List<GenericRecord> generateRecords(Schema schema, int numberOfRecords) {

        List<GenericRecord> recordList = new ArrayList<>();

        for(int recordValue = 1; recordValue <= numberOfRecords; recordValue++) {
            GenericRecord record = new GenericData.Record(schema);
            record.put("age", recordValue);
            record.put("name", recordValue + "testString");
            recordList.add(record);
        }
        return recordList;
    }

    private static InputStream createInvalidParquetStream() {
        return  new ByteArrayInputStream(INVALID_PARQUET_INPUT_STREAM.getBytes());
    }
}
