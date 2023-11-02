/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.dataprepper.plugins.kafka.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.opensearch.dataprepper.plugins.kafka.source.KafkaSourceConfig;
import org.yaml.snakeyaml.Yaml;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CommonTopicConfigTest {
    private TopicConfig topicConfig;

    private static final String YAML_FILE_WITH_CONSUMER_CONFIG = "sample-pipelines.yaml";

    private static final String YAML_FILE_WITH_MISSING_CONSUMER_CONFIG = "sample-pipelines-1.yaml";

    @BeforeEach
    void setUp(TestInfo testInfo) throws IOException {
        String fileName = testInfo.getTags().stream().findFirst().orElse("");
        Yaml yaml = new Yaml();
        FileReader fileReader = new FileReader(getClass().getClassLoader().getResource(fileName).getFile());
        Object data = yaml.load(fileReader);
        ObjectMapper mapper = new ObjectMapper();
        if (data instanceof Map) {
            Map<String, Object> propertyMap = (Map<String, Object>) data;
            Map<String, Object> logPipelineMap = (Map<String, Object>) propertyMap.get("log-pipeline");
            Map<String, Object> sourceMap = (Map<String, Object>) logPipelineMap.get("source");
            Map<String, Object> kafkaConfigMap = (Map<String, Object>) sourceMap.get("kafka");
            mapper.registerModule(new JavaTimeModule());
            String json = mapper.writeValueAsString(kafkaConfigMap);
            Reader reader = new StringReader(json);
            KafkaSourceConfig kafkaSourceConfig = mapper.readValue(reader, KafkaSourceConfig.class);
            List<? extends TopicConsumerConfig> topicConfigList = kafkaSourceConfig.getTopics();
            topicConfig = topicConfigList.get(0);
        }
    }

    @Test
    @Tag(YAML_FILE_WITH_CONSUMER_CONFIG)
    void test_topicsConfig_not_null() {
        assertThat(topicConfig, notNullValue());
    }

    @Test
    @Tag(YAML_FILE_WITH_MISSING_CONSUMER_CONFIG)
    void testConfigValues_default() {
        assertEquals("my-topic-2", topicConfig.getName());
        assertEquals(CommonTopicConfig.DEFAULT_RETRY_BACKOFF, topicConfig.getRetryBackoff());
        assertEquals(CommonTopicConfig.DEFAULT_RECONNECT_BACKOFF, topicConfig.getReconnectBackoff());
    }

    @Test
    @Tag(YAML_FILE_WITH_CONSUMER_CONFIG)
    void testConfigValues_from_yaml() {
        assertEquals("my-topic-1", topicConfig.getName());
        assertEquals(Duration.ofSeconds(100), topicConfig.getRetryBackoff());
    }

    @Test
    @Tag(YAML_FILE_WITH_CONSUMER_CONFIG)
    void testConfigValues_from_yaml_not_null() {
        assertNotNull(topicConfig.getName());
        assertNotNull(topicConfig.getRetryBackoff());
    }
}
