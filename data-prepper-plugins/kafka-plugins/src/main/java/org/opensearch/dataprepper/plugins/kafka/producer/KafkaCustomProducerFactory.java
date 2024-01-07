/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.kafka.producer;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.Serializer;
import org.opensearch.dataprepper.aws.api.AwsCredentialsSupplier;
import org.opensearch.dataprepper.expression.ExpressionEvaluator;
import org.opensearch.dataprepper.metrics.PluginMetrics;
import org.opensearch.dataprepper.model.configuration.PluginSetting;
import org.opensearch.dataprepper.model.plugin.PluginFactory;
import org.opensearch.dataprepper.model.sink.SinkContext;
import org.opensearch.dataprepper.plugins.kafka.common.KafkaDataConfig;
import org.opensearch.dataprepper.plugins.kafka.common.KafkaDataConfigAdapter;
import org.opensearch.dataprepper.plugins.kafka.common.PlaintextKafkaDataConfig;
import org.opensearch.dataprepper.plugins.kafka.common.aws.AwsContext;
import org.opensearch.dataprepper.plugins.kafka.common.key.KeyFactory;
import org.opensearch.dataprepper.plugins.kafka.common.serialization.SerializationFactory;
import org.opensearch.dataprepper.plugins.kafka.configuration.KafkaProducerConfig;
import org.opensearch.dataprepper.plugins.kafka.configuration.KafkaProducerProperties;

import org.opensearch.dataprepper.plugins.kafka.configuration.TopicProducerConfig;
import org.opensearch.dataprepper.plugins.kafka.configuration.SchemaConfig;
import org.opensearch.dataprepper.plugins.kafka.consumer.KafkaCustomConsumerFactory;
import org.opensearch.dataprepper.plugins.kafka.service.SchemaService;
import org.opensearch.dataprepper.plugins.kafka.service.TopicService;
import org.opensearch.dataprepper.plugins.kafka.sink.DLQSink;
import org.opensearch.dataprepper.plugins.kafka.util.KafkaSecurityConfigurer;
import org.opensearch.dataprepper.plugins.kafka.util.KafkaTopicProducerMetrics;
import org.opensearch.dataprepper.plugins.kafka.util.RestUtils;
import org.opensearch.dataprepper.plugins.kafka.util.SinkPropertyConfigurer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Properties;

public class KafkaCustomProducerFactory {
    private static final Logger LOG = LoggerFactory.getLogger(KafkaCustomConsumerFactory.class);
    private final SerializationFactory serializationFactory;
    private final AwsCredentialsSupplier awsCredentialsSupplier;

    public KafkaCustomProducerFactory(final SerializationFactory serializationFactory, AwsCredentialsSupplier awsCredentialsSupplier) {
        this.serializationFactory = serializationFactory;
        this.awsCredentialsSupplier = awsCredentialsSupplier;
    }

    public KafkaCustomProducer createProducer(final KafkaProducerConfig kafkaProducerConfig, final PluginFactory pluginFactory, final PluginSetting pluginSetting,
                                              final ExpressionEvaluator expressionEvaluator, final SinkContext sinkContext, final PluginMetrics pluginMetrics,
                                              final boolean topicNameInMetrics) {
        AwsContext awsContext = new AwsContext(kafkaProducerConfig, awsCredentialsSupplier);
        KeyFactory keyFactory = new KeyFactory(awsContext);
        // If either or both of Producer's max_request_size or
        // Topic's max_message_bytes is set, then maximum of the
        // two is set for both. If neither is set, then defaults are used.
        Integer maxRequestSize = null;
        KafkaProducerProperties producerProperties = kafkaProducerConfig.getKafkaProducerProperties();
        if (producerProperties != null) {
            int producerMaxRequestSize = producerProperties.getMaxRequestSize();
            if (producerMaxRequestSize != KafkaProducerProperties.DEFAULT_MAX_REQUEST_SIZE) {
                maxRequestSize = Integer.valueOf(producerMaxRequestSize);
            }
        }
        prepareTopicAndSchema(kafkaProducerConfig, maxRequestSize);
        Properties properties = SinkPropertyConfigurer.getProducerProperties(kafkaProducerConfig);
        properties = Objects.requireNonNull(properties);
        KafkaSecurityConfigurer.setAuthProperties(properties, kafkaProducerConfig, LOG);
        TopicProducerConfig topic = kafkaProducerConfig.getTopic();

        Long maxMessageBytes = topic.getMaxMessageBytes();
        if (maxMessageBytes != null) {
            if (maxRequestSize == null) {
                properties.put(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, (int)(long)maxMessageBytes);
            } else {
                properties.put(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, Math.max(maxRequestSize, (int)(long)maxMessageBytes));
            }
        }
        KafkaDataConfig dataConfig = new KafkaDataConfigAdapter(keyFactory, topic);
        Serializer<Object> keyDeserializer = (Serializer<Object>) serializationFactory.getSerializer(PlaintextKafkaDataConfig.plaintextDataConfig(dataConfig));
        Serializer<Object> valueSerializer = (Serializer<Object>) serializationFactory.getSerializer(dataConfig);
        final KafkaProducer<Object, Object> producer = new KafkaProducer<>(properties, keyDeserializer, valueSerializer);
        final DLQSink dlqSink = new DLQSink(pluginFactory, kafkaProducerConfig, pluginSetting);
        final KafkaTopicProducerMetrics topicMetrics = new KafkaTopicProducerMetrics(topic.getName(), pluginMetrics, topicNameInMetrics);
        final String topicName = ObjectUtils.isEmpty(kafkaProducerConfig.getTopic()) ? null : kafkaProducerConfig.getTopic().getName();
        final SchemaService schemaService = new SchemaService.SchemaServiceBuilder().getFetchSchemaService(topicName, kafkaProducerConfig.getSchemaConfig()).build();
        return new KafkaCustomProducer(producer,
            kafkaProducerConfig, dlqSink,
            expressionEvaluator, Objects.nonNull(sinkContext) ? sinkContext.getTagsTargetKey() : null, topicMetrics, schemaService);
    }
    private void prepareTopicAndSchema(final KafkaProducerConfig kafkaProducerConfig, final Integer maxRequestSize) {
        checkTopicCreationCriteriaAndCreateTopic(kafkaProducerConfig, maxRequestSize);
        final SchemaConfig schemaConfig = kafkaProducerConfig.getSchemaConfig();
        if (schemaConfig != null) {
            if (schemaConfig.isCreate()) {
                final RestUtils restUtils = new RestUtils(schemaConfig);
                final String topic = kafkaProducerConfig.getTopic().getName();
                final SchemaService schemaService = new SchemaService.SchemaServiceBuilder()
                    .getRegisterationAndCompatibilityService(topic, kafkaProducerConfig.getSerdeFormat(),
                        restUtils, schemaConfig).build();
                schemaService.registerSchema(topic);
            }

        }

    }

    private void checkTopicCreationCriteriaAndCreateTopic(final KafkaProducerConfig kafkaProducerConfig, final Integer maxRequestSize) {
        final TopicProducerConfig topic = kafkaProducerConfig.getTopic();
        if (!topic.isCreateTopic()) {
            final TopicService topicService = new TopicService(kafkaProducerConfig);
            Long maxMessageBytes = topic.getMaxMessageBytes();
            if (maxMessageBytes == null) {
                if (maxRequestSize != null) {
                    maxMessageBytes = Long.valueOf((long)maxRequestSize);
                }
            } else if (maxRequestSize != null) {
                maxMessageBytes = Long.valueOf((long)Math.max(maxMessageBytes, maxRequestSize));
            }
            topicService.createTopic(kafkaProducerConfig.getTopic().getName(), topic.getNumberOfPartitions(), topic.getReplicationFactor(), maxMessageBytes);
            topicService.closeAdminClient();
        }


    }
}
