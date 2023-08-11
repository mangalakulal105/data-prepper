/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.kafka.util;

import io.micrometer.core.instrument.Counter;
import org.opensearch.dataprepper.metrics.PluginMetrics;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.clients.consumer.KafkaConsumer;

import java.time.Instant;
import java.util.Objects;
import java.util.Map;
import java.util.HashMap;

public class KafkaTopicMetrics {
    static final String NUMBER_OF_POSITIVE_ACKNOWLEDGEMENTS = "numberOfPositiveAcknowledgements";
    static final String NUMBER_OF_NEGATIVE_ACKNOWLEDGEMENTS = "numberOfNegativeAcknowledgements";
    static final String NUMBER_OF_RECORDS_FAILED_TO_PARSE = "numberOfRecordsFailedToParse";
    static final String NUMBER_OF_DESERIALIZATION_ERRORS = "numberOfDeserializationErrors";
    static final String NUMBER_OF_BUFFER_SIZE_OVERFLOWS = "numberOfBufferSizeOverflows";
    static final String NUMBER_OF_POLL_AUTH_ERRORS = "numberOfPollAuthErrors";
    static final String NUMBER_OF_RECORDS_COMMITTED = "numberOfRecordsCommitted";
    static final String NUMBER_OF_RECORDS_CONSUMED = "numberOfRecordsConsumed";
    static final String NUMBER_OF_BYTES_CONSUMED = "numberOfBytesConsumed";

    private final String topicName;
    private long updateTime;
    private Map<String, String> metricsNameMap;
    private Map<KafkaConsumer, Map<String, Double>> metricValues;
    private final PluginMetrics pluginMetrics;
    private final Counter numberOfPositiveAcknowledgements;
    private final Counter numberOfNegativeAcknowledgements;
    private final Counter numberOfRecordsFailedToParse;
    private final Counter numberOfDeserializationErrors;
    private final Counter numberOfBufferSizeOverflows;
    private final Counter numberOfPollAuthErrors;
    private final Counter numberOfRecordsCommitted;
    private final Counter numberOfRecordsConsumed;
    private final Counter numberOfBytesConsumed;

    public KafkaTopicMetrics(final String topicName, final PluginMetrics pluginMetrics) {
        this.pluginMetrics = pluginMetrics;
        this.topicName = topicName;
        this.updateTime = Instant.now().getEpochSecond();
        this.metricValues = new HashMap<>();
        initializeMetricNamesMap();
        this.numberOfRecordsConsumed = pluginMetrics.counter(getTopicMetricName(NUMBER_OF_RECORDS_CONSUMED));
        this.numberOfBytesConsumed = pluginMetrics.counter(getTopicMetricName(NUMBER_OF_BYTES_CONSUMED));
        this.numberOfRecordsCommitted = pluginMetrics.counter(getTopicMetricName(NUMBER_OF_RECORDS_COMMITTED));
        this.numberOfRecordsFailedToParse = pluginMetrics.counter(getTopicMetricName(NUMBER_OF_RECORDS_FAILED_TO_PARSE));
        this.numberOfDeserializationErrors = pluginMetrics.counter(getTopicMetricName(NUMBER_OF_DESERIALIZATION_ERRORS));
        this.numberOfBufferSizeOverflows = pluginMetrics.counter(getTopicMetricName(NUMBER_OF_BUFFER_SIZE_OVERFLOWS));
        this.numberOfPollAuthErrors = pluginMetrics.counter(getTopicMetricName(NUMBER_OF_POLL_AUTH_ERRORS));
        this.numberOfPositiveAcknowledgements = pluginMetrics.counter(getTopicMetricName(NUMBER_OF_POSITIVE_ACKNOWLEDGEMENTS));
        this.numberOfNegativeAcknowledgements = pluginMetrics.counter(getTopicMetricName(NUMBER_OF_NEGATIVE_ACKNOWLEDGEMENTS));
    }

    private void initializeMetricNamesMap() {
        this.metricsNameMap = new HashMap<>();
        metricsNameMap.put("bytes-consumed-total", "bytesConsumedTotal");
        metricsNameMap.put("records-consumed-total", "recordsConsumedTotal");
        metricsNameMap.put("bytes-consumed-rate", "bytesConsumedRate");
        metricsNameMap.put("records-consumed-rate", "recordsConsumedRate");
        metricsNameMap.put("records-lag-max", "recordsLagMax");
        metricsNameMap.put("records-lead-min", "recordsLeadMin");
        metricsNameMap.put("commit-rate", "commitRate");
        metricsNameMap.put("join-rate", "joinRate");
        metricsNameMap.put("incoming-byte-rate", "incomingByteRate");
        metricsNameMap.put("outgoing-byte-rate", "outgoingByteRate");
        metricsNameMap.put("assigned-partitions", "numberOfNonConsumers");
        metricsNameMap.forEach((metricName, camelCaseName) -> {
            if (metricName.equals("records-lag-max")) {
                pluginMetrics.gauge(getTopicMetricName(camelCaseName), metricValues, metricValues -> {
                    double max = 0.0;
                    for (Map.Entry<KafkaConsumer, Map<String, Double>> entry : metricValues.entrySet()) {
                        Map<String, Double> consumerMetrics = entry.getValue();
                        synchronized(consumerMetrics) {
                            max = Math.max(max, consumerMetrics.get(metricName));
                        }
                    }
                    return max;
                });
            } else if (metricName.equals("records-lead-min")) {
                pluginMetrics.gauge(getTopicMetricName(camelCaseName), metricValues, metricValues -> {
                    double min = Double.MAX_VALUE;
                    for (Map.Entry<KafkaConsumer, Map<String, Double>> entry : metricValues.entrySet()) {
                        Map<String, Double> consumerMetrics = entry.getValue();
                        synchronized(consumerMetrics) {
                            min = Math.min(min, consumerMetrics.get(metricName));
                        }
                    }
                    return min;
                });
            } else if (!metricName.contains("-total")) {
                pluginMetrics.gauge(getTopicMetricName(camelCaseName), metricValues, metricValues -> {
                    double sum = 0;
                    for (Map.Entry<KafkaConsumer, Map<String, Double>> entry : metricValues.entrySet()) {
                        Map<String, Double> consumerMetrics = entry.getValue();
                        synchronized(consumerMetrics) {
                            sum += consumerMetrics.get(metricName);
                        }
                    }
                    return sum;
                });
            }
        });
    }

    public void register(final KafkaConsumer consumer) {
        metricValues.put(consumer, new HashMap<>());
        final Map<String, Double> consumerMetrics = metricValues.get(consumer);
        metricsNameMap.forEach((k, name) -> {
            consumerMetrics.put(k, 0.0);
        });
    }

    Counter getNumberOfRecordsConsumed() {
        return numberOfRecordsConsumed;
    }

    Counter getNumberOfBytesConsumed() {
        return numberOfBytesConsumed;
    }

    public Counter getNumberOfRecordsCommitted() {
        return numberOfRecordsCommitted;
    }

    public Counter getNumberOfPollAuthErrors() {
        return numberOfPollAuthErrors;
    }

    public Counter getNumberOfBufferSizeOverflows() {
        return numberOfBufferSizeOverflows;
    }

    public Counter getNumberOfDeserializationErrors() {
        return numberOfDeserializationErrors;
    }

    public Counter getNumberOfRecordsFailedToParse() {
        return numberOfRecordsFailedToParse;
    }

    public Counter getNumberOfNegativeAcknowledgements() {
        return numberOfNegativeAcknowledgements;
    }

    public Counter getNumberOfPositiveAcknowledgements() {
        return numberOfPositiveAcknowledgements;
    }

    private String getTopicMetricName(final String metricName) {
        return "topic."+topicName+"."+metricName;
    }

    private String getCamelCaseName(final String name) {
        String camelCaseName = metricsNameMap.get(name);
        if (Objects.isNull(camelCaseName)) {
            return name;
        }
        return camelCaseName;
    }

    Map<KafkaConsumer, Map<String, Double>> getMetricValues() {
        return metricValues;
    }

    public void update(final KafkaConsumer consumer) {
        Map<String, Double> consumerMetrics = metricValues.get(consumer);

        Map<MetricName, ? extends Metric> metrics = consumer.metrics();
        for (Map.Entry<MetricName, ? extends Metric> entry : metrics.entrySet()) {
            MetricName metric = entry.getKey();
            Metric value = entry.getValue();
            String metricName = metric.name();
            if (Objects.nonNull(metricsNameMap.get(metricName))) {
                if (metric.tags().containsKey("partition") &&
                   (metricName.equals("records-lag-max") || metricName.equals("records-lead-min"))) {
                   continue;
                }

                if (metricName.contains("consumed-total") && !metric.tags().containsKey("topic")) {
                    continue;
                }
                if (metricName.contains("byte-rate") && metric.tags().containsKey("node-id")) {
                    continue;
                }
                double newValue = (Double)value.metricValue();
                if (metricName.equals("records-consumed-total")) {
                    synchronized(consumerMetrics) {
                        double prevValue = consumerMetrics.get(metricName);
                        numberOfRecordsConsumed.increment(newValue - prevValue);
                    }
                } else if (metricName.equals("bytes-consumed-total")) {
                    synchronized(consumerMetrics) {
                        double prevValue = consumerMetrics.get(metricName);
                        numberOfBytesConsumed.increment(newValue - prevValue);
                    }
                }
                // Keep the count of number of consumers without any assigned partitions. This value can go up or down. So, it is made as Guage metric
                if (metricName.equals("assigned-partitions")) {
                    newValue = (newValue == 0.0) ? 1.0 : 0.0;
                }
                synchronized(consumerMetrics) {
                    consumerMetrics.put(metricName, newValue);
                }
            }
        }
    }
}
