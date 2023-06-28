package org.opensearch.dataprepper.plugins.kafka.sink;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.opensearch.dataprepper.model.configuration.PluginSetting;
import org.opensearch.dataprepper.model.event.Event;
import org.opensearch.dataprepper.model.event.JacksonEvent;
import org.opensearch.dataprepper.model.plugin.PluginFactory;
import org.opensearch.dataprepper.model.record.Record;
import org.opensearch.dataprepper.plugins.kafka.configuration.*;
import org.opensearch.dataprepper.plugins.kafka.producer.ProducerWorker;
import org.opensearch.dataprepper.plugins.kafka.util.SinkPropertyConfigurer;
import org.springframework.test.util.ReflectionTestUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.FileReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class KafkasinkTest {


    KafkaSink kafkaSink;

    // @Mock
    KafkaSinkConfig kafkaSinkConfig;


    ExecutorService executorService;


    private SchemaConfig schemaConfig;

    @Mock
    PluginSetting pluginSetting;

    @Mock
    FutureTask futureTask;

    @Mock
    AuthConfig authConfig;

    Event event;

    KafkaSink spySink;

    private static final Integer totalWorkers = 1;

    MockedStatic<Executors> executorsMockedStatic;

    MockedStatic<SinkPropertyConfigurer> propertyConfigurerMockedStatic;

    @Mock
    PlainTextAuthConfig plainTextAuthConfig;

    @Mock
    OAuthConfig oAuthConfig;

    @Mock
    private PluginSetting pluginSettingMock;

    @Mock
    private KafkaSinkConfig kafkaSinkConfigMock;

    @Mock
    private PluginFactory pluginFactoryMock;

    Properties props;

    @Mock
    AwsDLQConfig dlqConfig;


    @BeforeEach
    void setUp() throws Exception {
        Yaml yaml = new Yaml();
        FileReader fileReader = new FileReader(getClass().getClassLoader().getResource("sample-pipelines-sink.yaml").getFile());
        Object data = yaml.load(fileReader);
        if (data instanceof Map) {
            Map<String, Object> propertyMap = (Map<String, Object>) data;
            Map<String, Object> logPipelineMap = (Map<String, Object>) propertyMap.get("log-pipeline");
            Map<String, Object> sinkeMap = (Map<String, Object>) logPipelineMap.get("sink");
            Map<String, Object> kafkaConfigMap = (Map<String, Object>) sinkeMap.get("kafka-sink");
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            String json = mapper.writeValueAsString(kafkaConfigMap);
            Reader reader = new StringReader(json);
            kafkaSinkConfig = mapper.readValue(reader, KafkaSinkConfig.class);
        }
        executorService = mock(ExecutorService.class);
        when(pluginSetting.getPipelineName()).thenReturn("Kafka-sink");
        event = JacksonEvent.fromMessage(UUID.randomUUID().toString());
        kafkaSink = new KafkaSink(pluginSetting, kafkaSinkConfig, pluginFactoryMock);
        spySink = spy(kafkaSink);
        executorsMockedStatic = mockStatic(Executors.class);
        props = new Properties();
        props.put("bootstrap.servers", "127.0.0.1:9093");
        props.put("key.serializer", StringSerializer.class.getName());
        props.put("value.serializer", StringSerializer.class.getName());
        ReflectionTestUtils.setField(spySink, "executorService", executorService);


    }

    @AfterEach
    public void after() {
        executorsMockedStatic.close();
    }

    @Test
    public void doOutputTest() {
        when(executorService.submit(any(ProducerWorker.class))).thenReturn(futureTask);
        final Collection records = Arrays.asList(new Record(event));
        spySink.doOutput(records);
        verify(spySink).doOutput(records);
    }


    @Test
    public void doOutputExceptionTest() {
        final Collection records = Arrays.asList(new Record(event));
        when(executorService.submit(any(ProducerWorker.class))).thenThrow(new RuntimeException());
        assertThrows(RuntimeException.class, () -> spySink.doOutput(records));
    }

    @Test
    public void doOutputEmptyRecordsTest() {
        final Collection records = Arrays.asList();
        spySink.doOutput(records);
        verify(spySink).doOutput(records);

    }

    @Test
    public void shutdownTest() throws InterruptedException {
        spySink.shutdown();
        verify(spySink).shutdown();
    }

    @Test
    public void shutdownExceptionTest() throws InterruptedException {
        final InterruptedException interruptedException = new InterruptedException();
        interruptedException.initCause(new InterruptedException());

        when(executorService.awaitTermination(
                1000L, TimeUnit.MILLISECONDS)).thenThrow(interruptedException);
        spySink.shutdown();

    }


    @Test
    public void doInitializeTest() {
        spySink.doInitialize();
        verify(spySink).doInitialize();
    }

    @Test
    public void doInitializeNullPointerExceptionTest() {
        when(Executors.newFixedThreadPool(totalWorkers)).thenThrow(NullPointerException.class);
        assertThrows(NullPointerException.class, () -> spySink.doInitialize());
    }


    @Test
    public void isReadyTest() {
        ReflectionTestUtils.setField(kafkaSink, "sinkInitialized", true);
        assertEquals(true, kafkaSink.isReady());
    }
}
