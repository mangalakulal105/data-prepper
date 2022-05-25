/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.dataprepper.parser.config;

import com.amazon.dataprepper.DataPrepper;
import com.amazon.dataprepper.meter.EMFLoggingMeterRegistry;
import com.amazon.dataprepper.metrics.MetricNames;
import com.amazon.dataprepper.parser.model.DataPrepperConfiguration;
import com.amazon.dataprepper.parser.model.MetricRegistryType;
import com.amazon.dataprepper.pipeline.server.CloudWatchMeterRegistryProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.micrometer.cloudwatch2.CloudWatchMeterRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isA;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class MetricsConfigTest {
    private static final MetricsConfig metricsConfig = new MetricsConfig();

    @Test
    public void testYamlFactory() {
        final YAMLFactory factory = metricsConfig.yamlFactory();

        assertThat(factory, isA(YAMLFactory.class));
    }

    @Test
    public void testObjectMapper() {
        final ObjectMapper mapper = metricsConfig.objectMapper(null);

        assertThat(mapper, isA(ObjectMapper.class));
    }

    @Test
    public void testClassLoaderMetrics() {
        final ClassLoaderMetrics actual = metricsConfig.classLoaderMetrics();

        assertThat(actual, isA(ClassLoaderMetrics.class));
    }

    @Test
    public void testJvmMemoryMetrics() {
        final JvmMemoryMetrics actual = metricsConfig.jvmMemoryMetrics();

        assertThat(actual, isA(JvmMemoryMetrics.class));
    }

    @Test
    public void testJvmGcMetrics() {
        final JvmGcMetrics actual = metricsConfig.jvmGcMetrics();

        assertThat(actual, isA(JvmGcMetrics.class));
    }

    @Test
    public void testProcessorMetrics() {
        final ProcessorMetrics actual = metricsConfig.processorMetrics();

        assertThat(actual, isA(ProcessorMetrics.class));
    }

    @Test
    public void testJvmThreadMetrics() {
        final JvmThreadMetrics actual = metricsConfig.jvmThreadMetrics();

        assertThat(actual, isA(JvmThreadMetrics.class));
    }

    @Test
    public void testGivenConfigWithPrometheusMeterRegistryThenMeterRegistryCreated() {
        final DataPrepperConfiguration dataPrepperConfiguration = mock(DataPrepperConfiguration.class);

        when(dataPrepperConfiguration.getMetricRegistryTypes())
                .thenReturn(Collections.singletonList(MetricRegistryType.Prometheus));

        final MeterRegistry meterRegistry = metricsConfig.prometheusMeterRegistry(dataPrepperConfiguration);

        assertThat(meterRegistry, isA(PrometheusMeterRegistry.class));
    }

    @Test
    public void testGivenEmptyConfigThenMeterRegistryCreated() {
        final DataPrepperConfiguration dataPrepperConfiguration = mock(DataPrepperConfiguration.class);

        when(dataPrepperConfiguration.getMetricRegistryTypes())
                .thenReturn(Collections.emptyList());

        final MeterRegistry meterRegistry = metricsConfig.prometheusMeterRegistry(dataPrepperConfiguration);

        assertThat(meterRegistry, is(nullValue()));
    }

    @Test
    public void testGivenConfigWithNoCloudWatchMeterRegistryThenNoMeterRegistryCreated() {
        final DataPrepperConfiguration dataPrepperConfiguration = mock(DataPrepperConfiguration.class);

        when(dataPrepperConfiguration.getMetricRegistryTypes())
                .thenReturn(Collections.singletonList(MetricRegistryType.Prometheus));

        final MeterRegistry meterRegistry = metricsConfig.cloudWatchMeterRegistry(dataPrepperConfiguration, null);

        assertThat(meterRegistry, is(nullValue()));
    }

    @Test
    public void testGivenConfigWithCloudWatchMeterRegistryThenNoMeterRegistryCreated() {
        final CloudWatchMeterRegistryProvider provider = mock(CloudWatchMeterRegistryProvider.class);
        final CloudWatchMeterRegistry expected = mock(CloudWatchMeterRegistry.class);
        final MeterRegistry.Config config = mock(MeterRegistry.Config.class);
        final DataPrepperConfiguration dataPrepperConfiguration = mock(DataPrepperConfiguration.class);

        when(provider.getCloudWatchMeterRegistry())
                .thenReturn(expected);
        when(expected.config())
                .thenReturn(config);

        when(dataPrepperConfiguration.getMetricRegistryTypes())
                .thenReturn(Collections.singletonList(MetricRegistryType.CloudWatch));

        final MeterRegistry meterRegistry = metricsConfig.cloudWatchMeterRegistry(dataPrepperConfiguration, provider);

        assertThat(meterRegistry, is(expected));
    }

    @Test
    public void testGivenConfigWithEMFLoggingMeterRegistryThenMeterRegistryCreated() {
        final DataPrepperConfiguration dataPrepperConfiguration = mock(DataPrepperConfiguration.class);

        when(dataPrepperConfiguration.getMetricRegistryTypes())
                .thenReturn(Collections.singletonList(MetricRegistryType.EmbeddedMetricsFormat));

        final MeterRegistry meterRegistry = metricsConfig.emfLoggingMeterRegistry(dataPrepperConfiguration);
        final Counter counter = meterRegistry.counter("counter");
        final List<Tag> commonTags = counter.getId().getConventionTags(meterRegistry.config().namingConvention());

        assertThat(meterRegistry, isA(EMFLoggingMeterRegistry.class));
        assertThat(commonTags.size(), equalTo(1));
        final Tag commonTag = commonTags.get(0);
        assertThat(commonTag.getKey(), equalTo(MetricNames.SERVICE_NAME));
        assertThat(commonTag.getValue(), equalTo(DataPrepper.getServiceNameForMetrics()));
    }

    @ParameterizedTest
    @MethodSource("provideMetricRegistryTypesAndCreators")
    public void testGivenConfigWithMetricTagsThenMeterRegistryConfigured(final MetricRegistryType metricRegistryType,
                                                                         final Function<DataPrepperConfiguration, MeterRegistry> creator) {
        final DataPrepperConfiguration dataPrepperConfiguration = mock(DataPrepperConfiguration.class);
        when(dataPrepperConfiguration.getMetricRegistryTypes()).thenReturn(Collections.singletonList(metricRegistryType));
        when(dataPrepperConfiguration.getMetricTags()).thenReturn(Map.of("testKey", "testValue"));

        MeterRegistry meterRegistry = creator.apply(dataPrepperConfiguration);
        Counter counter = meterRegistry.counter("counter");
        List<Tag> commonTags = counter.getId().getConventionTags(meterRegistry.config().namingConvention());

        assertThat(commonTags.equals(
                Arrays.asList(
                        Tag.of(MetricNames.SERVICE_NAME, DataPrepper.getServiceNameForMetrics()),
                        Tag.of("testKey", "testValue")
                        )
        ), is(true));

        when(dataPrepperConfiguration.getMetricRegistryTypes()).thenReturn(Collections.singletonList(metricRegistryType));
        when(dataPrepperConfiguration.getMetricTags()).thenReturn(Map.of(MetricNames.SERVICE_NAME, "testServiceName"));

        meterRegistry = creator.apply(dataPrepperConfiguration);
        counter = meterRegistry.counter("counter");
        commonTags = counter.getId().getConventionTags(meterRegistry.config().namingConvention());

        assertThat(commonTags.size(), equalTo(1));
        assertThat(commonTags.equals(
                List.of(Tag.of(MetricNames.SERVICE_NAME, "testServiceName"))
        ), is(true));
    }

    @Test
    public void testGivenListOfMeterBindersWhenSystemMeterRegistryThenAllMeterBindersRegistered() {
        final Random r = new Random();
        final int copies = r.nextInt(10) + 5;
        final MeterBinder binder = mock(MeterBinder.class);
        final List<MeterBinder> meterBinders = Collections.nCopies(copies, binder);

        final MeterRegistry meterRegistryMock = mock(MeterRegistry.class);
        final List<MeterRegistry> meterRegistries = Collections.nCopies(1, meterRegistryMock);

        final CompositeMeterRegistry meterRegistry = metricsConfig.systemMeterRegistry(
                meterBinders,
                meterRegistries);

        assertThat(meterRegistry, isA(CompositeMeterRegistry.class));
        verify(binder, times(copies)).bindTo(any(MeterRegistry.class));
    }

    @Test
    public void testGivenEmptyListOfMeterBindersWhenSystemMeterRegistryThenNoMeterBindersRegistered() {
        final List<MeterBinder> meterBinders = Collections.emptyList();

        final MeterRegistry meterRegistryMock = mock(MeterRegistry.class);
        final List<MeterRegistry> meterRegistries = Collections.nCopies(1, meterRegistryMock);

        final CompositeMeterRegistry meterRegistry = metricsConfig.systemMeterRegistry(
                meterBinders,
                meterRegistries);

        assertThat(meterRegistry, isA(CompositeMeterRegistry.class));
    }

    private static Stream<Arguments> provideMetricRegistryTypesAndCreators() {
        return Stream.of(
                Arguments.of(MetricRegistryType.EmbeddedMetricsFormat, (Function<DataPrepperConfiguration, MeterRegistry>) metricsConfig::emfLoggingMeterRegistry),
                Arguments.of(MetricRegistryType.Prometheus, (Function<DataPrepperConfiguration, MeterRegistry>) metricsConfig::prometheusMeterRegistry)
        );
    }
}