package org.opensearch.dataprepper.plugins.kafka.configuration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.yaml.snakeyaml.Yaml;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class KafkaSourceConfigTest {

	@Mock
	KafkaSourceConfig kafkaSourceConfig;

	List<String> bootstrapServers;

	@BeforeEach
	void setUp() throws IOException {
		//Added to load Yaml file - Start
		Yaml yaml = new Yaml();
		FileReader fileReader = new FileReader(getClass().getClassLoader().getResource("sample-pipelines.yaml").getFile());
		Object data = yaml.load(fileReader);
		if(data instanceof Map){
			Map<String, Object> propertyMap = (Map<String, Object>) data;
			Map<String, Object> logPipelineMap = (Map<String, Object>) propertyMap.get("log-pipeline");
			Map<String, Object> sourceMap = (Map<String, Object>) logPipelineMap.get("source");
			Map<String, Object> kafkaConfigMap = (Map<String, Object>) sourceMap.get("kafka");
			ObjectMapper mapper = new ObjectMapper();
			mapper.registerModule(new JavaTimeModule());
			String json = mapper.writeValueAsString(kafkaConfigMap);
			Reader reader = new StringReader(json);
			kafkaSourceConfig = mapper.readValue(reader, KafkaSourceConfig.class);
		}
	}

	@Test
	void test_kafka_config_not_null() {
		assertThat(kafkaSourceConfig, notNullValue());
	}

	@Test
	void test_bootStrapServers_not_null(){
		assertThat(kafkaSourceConfig.getBootStrapServers(), notNullValue());
		List<String> servers = kafkaSourceConfig.getBootStrapServers();
		bootstrapServers = servers.stream().
				flatMap(str -> Arrays.stream(str.split(","))).
				map(String::trim).
				collect(Collectors.toList());
		assertThat(bootstrapServers.size(), equalTo(1));
		assertThat(bootstrapServers, hasItem("127.0.0.1:9093"));
	}

	@Test
	void test_topics_not_null(){
		assertThat(kafkaSourceConfig.getTopics(), notNullValue());
	}

	@Test
	void test_setters(){
		kafkaSourceConfig = new KafkaSourceConfig();
		kafkaSourceConfig.setBootStrapServers(new ArrayList<>(Arrays.asList("127.0.0.1:9092")));
		TopicsConfig topicsConfig = mock(TopicsConfig.class);
		kafkaSourceConfig.setTopics(Collections.singletonList(topicsConfig));

		assertEquals(Arrays.asList("127.0.0.1:9092"), kafkaSourceConfig.getBootStrapServers());
		assertEquals(Collections.singletonList(topicsConfig), kafkaSourceConfig.getTopics());
	}
}
