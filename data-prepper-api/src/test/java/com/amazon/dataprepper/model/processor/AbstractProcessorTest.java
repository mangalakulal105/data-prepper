/*
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  The OpenSearch Contributors require contributions made to
 *  this file be licensed under the Apache-2.0 license or a
 *  compatible open source license.
 *
 *  Modifications Copyright OpenSearch Contributors. See
 *  GitHub history for details.
 */

package com.amazon.dataprepper.model.processor;

import com.amazon.dataprepper.metrics.MetricNames;
import com.amazon.dataprepper.metrics.MetricsTestUtil;
import com.amazon.dataprepper.metrics.PluginMetrics;
import com.amazon.dataprepper.model.configuration.PluginSetting;
import com.amazon.dataprepper.model.record.Record;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Statistic;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;

class AbstractProcessorTest {

    @Test
    public void testMetricsWithPluginSettingsConstructor() {
        final String processorName = "testProcessor";
        final String pipelineName = "testPipeline";
        MetricsTestUtil.initMetrics();

        PluginSetting pluginSetting = new PluginSetting(processorName, Collections.emptyMap());
        pluginSetting.setPipelineName(pipelineName);
        AbstractProcessor<Record<String>, Record<String>> processor = new ProcessorImpl(pluginSetting);

        processor.execute(Arrays.asList(
                new Record<>("Value1"),
                new Record<>("Value2"),
                new Record<>("Value3")
        ));

        final List<Measurement> recordsInMeasurements = MetricsTestUtil.getMeasurementList(
                new StringJoiner(MetricNames.DELIMITER).add(pipelineName).add(processorName).add(MetricNames.RECORDS_IN).toString());
        final List<Measurement> recordsOutMeasurements = MetricsTestUtil.getMeasurementList(
                new StringJoiner(MetricNames.DELIMITER).add(pipelineName).add(processorName).add(MetricNames.RECORDS_OUT).toString());
        final List<Measurement> elapsedTimeMeasurements = MetricsTestUtil.getMeasurementList(
                new StringJoiner(MetricNames.DELIMITER).add(pipelineName).add(processorName).add(MetricNames.TIME_ELAPSED).toString());

        Assertions.assertEquals(1, recordsInMeasurements.size());
        Assertions.assertEquals(3.0, recordsInMeasurements.get(0).getValue(), 0);
        Assertions.assertEquals(1, recordsOutMeasurements.size());
        Assertions.assertEquals(6.0, recordsOutMeasurements.get(0).getValue(), 0);
        Assertions.assertEquals(3, elapsedTimeMeasurements.size());
        Assertions.assertEquals(1.0, MetricsTestUtil.getMeasurementFromList(elapsedTimeMeasurements, Statistic.COUNT).getValue(), 0);
        Assertions.assertTrue(MetricsTestUtil.isBetween(
                MetricsTestUtil.getMeasurementFromList(elapsedTimeMeasurements, Statistic.TOTAL_TIME).getValue(),
                0.1,
                0.2));
    }

    @Test
    public void testMetricsWithPluginMetricsConstructor() {
        final String processorName = "testProcessor";
        final String pipelineName = "testPipeline";
        MetricsTestUtil.initMetrics();

        PluginMetrics pluginMetrics = PluginMetrics.fromNames(processorName, pipelineName);
        AbstractProcessor<Record<String>, Record<String>> processor = new ProcessorImpl(pluginMetrics);

        processor.execute(Arrays.asList(
                new Record<>("Value1"),
                new Record<>("Value2"),
                new Record<>("Value3")
        ));

        final List<Measurement> recordsInMeasurements = MetricsTestUtil.getMeasurementList(
                new StringJoiner(MetricNames.DELIMITER).add(pipelineName).add(processorName).add(MetricNames.RECORDS_IN).toString());
        final List<Measurement> recordsOutMeasurements = MetricsTestUtil.getMeasurementList(
                new StringJoiner(MetricNames.DELIMITER).add(pipelineName).add(processorName).add(MetricNames.RECORDS_OUT).toString());
        final List<Measurement> elapsedTimeMeasurements = MetricsTestUtil.getMeasurementList(
                new StringJoiner(MetricNames.DELIMITER).add(pipelineName).add(processorName).add(MetricNames.TIME_ELAPSED).toString());

        Assertions.assertEquals(1, recordsInMeasurements.size());
        Assertions.assertEquals(3.0, recordsInMeasurements.get(0).getValue(), 0);
        Assertions.assertEquals(1, recordsOutMeasurements.size());
        Assertions.assertEquals(6.0, recordsOutMeasurements.get(0).getValue(), 0);
        Assertions.assertEquals(3, elapsedTimeMeasurements.size());
        Assertions.assertEquals(1.0, MetricsTestUtil.getMeasurementFromList(elapsedTimeMeasurements, Statistic.COUNT).getValue(), 0);
        Assertions.assertTrue(MetricsTestUtil.isBetween(
                MetricsTestUtil.getMeasurementFromList(elapsedTimeMeasurements, Statistic.TOTAL_TIME).getValue(),
                0.1,
                0.2));
    }

    public static class ProcessorImpl extends AbstractProcessor<Record<String>, Record<String>> {
        public ProcessorImpl(PluginSetting pluginSetting) {
            super(pluginSetting);
        }

        public ProcessorImpl(PluginMetrics pluginMetrics) {
            super(pluginMetrics);
        }

        @Override
        public Collection<Record<String>> doExecute(Collection<Record<String>> records) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
            }
            return records.stream()
                    .flatMap(stringRecord -> Arrays.asList(stringRecord, stringRecord).stream())
                    .collect(Collectors.toList());
        }

        @Override
        public void prepareForShutdown() {

        }

        @Override
        public boolean isReadyForShutdown() {
            return true;
        }

        @Override
        public void shutdown() {

        }
    }
}