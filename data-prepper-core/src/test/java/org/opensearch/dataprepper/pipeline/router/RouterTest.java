/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.pipeline.router;

import org.opensearch.dataprepper.model.record.Record;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.dataprepper.parser.DataFlowComponent;
import org.opensearch.dataprepper.pipeline.PipelineConnector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.times;
import static org.junit.Assert.assertFalse;

@ExtendWith(MockitoExtension.class)
class RouterTest {

    @Mock
    private RouteEventEvaluator routeEventEvaluator;

    @Mock
    private DataFlowComponentRouter dataFlowComponentRouter;
    private Collection<DataFlowComponent<TestComponent>> dataFlowComponents;
    private Collection<DataFlowComponent<PipelineConnector>> pipelineDataFlowComponents;
    @Mock
    private BiConsumer<TestComponent, Collection<Record>> componentRecordsConsumer;
    @Mock
    private BiConsumer<PipelineConnector, Collection<Record>> pipelineComponentRecordsConsumer;

    @Mock
    private DataFlowComponent<PipelineConnector> pipelineDataFlowComponent;

    private Collection<Record> recordsIn;

    private static class TestComponent {
    }

    @BeforeEach
    void setUp() {
        recordsIn = Collections.emptyList();
        dataFlowComponents = Collections.emptyList();
    }

    private Router createObjectUnderTest() {
        return new Router(routeEventEvaluator, dataFlowComponentRouter);
    }

    @Test
    void constructor_throws_with_null_routeEventEvaluator() {
        routeEventEvaluator = null;
        assertThrows(NullPointerException.class, this::createObjectUnderTest);
    }

    @Test
    void route_throws_if_records_is_null() {
        final Router objectUnderTest = createObjectUnderTest();

        assertThrows(NullPointerException.class, () -> objectUnderTest.route(null, dataFlowComponents, null, componentRecordsConsumer));
    }

    @Test
    void route_throws_if_dataFlowComponents_is_null() {
        final Router objectUnderTest = createObjectUnderTest();

        assertThrows(NullPointerException.class, () -> objectUnderTest.route(recordsIn, null, null, componentRecordsConsumer));
    }

    @Test
    void route_throws_if_componentRecordsConsumer_is_null() {
        final Router objectUnderTest = createObjectUnderTest();

        assertThrows(NullPointerException.class, () -> objectUnderTest.route(recordsIn, dataFlowComponents, null, null));
    }

    @Nested
    class WithEmptyRecords {

        private Map<Record, Set<String>> recordsToRoutes;

        @BeforeEach
        void setUp() {
            recordsIn = Collections.emptyList();
            dataFlowComponents = Collections.emptyList();

            recordsToRoutes = Collections.singletonMap(
                    mock(Record.class), Collections.singleton(UUID.randomUUID().toString())
            );
            when(routeEventEvaluator.evaluateEventRoutes(recordsIn)).thenReturn(recordsToRoutes);
        }

        @Test
        void route_with_empty_DataFlowComponent() {
            createObjectUnderTest().route(recordsIn, dataFlowComponents, null, componentRecordsConsumer);
        }

        @Test
        void route_with_single_DataFlowComponent() {
            DataFlowComponent<TestComponent> dataFlowComponent = mock(DataFlowComponent.class);
            dataFlowComponents = Collections.singletonList(dataFlowComponent);

            createObjectUnderTest().route(recordsIn, dataFlowComponents, null, componentRecordsConsumer);

            verify(dataFlowComponentRouter).route(recordsIn, dataFlowComponent, recordsToRoutes, null, componentRecordsConsumer);
        }

        @Test
        void route_with_multiple_DataFlowComponents() {

            dataFlowComponents = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                final DataFlowComponent dataFlowComponent = mock(DataFlowComponent.class);
                dataFlowComponents.add(dataFlowComponent);
            }

            createObjectUnderTest().route(recordsIn, dataFlowComponents, null, componentRecordsConsumer);

            for (DataFlowComponent<TestComponent> dataFlowComponent : dataFlowComponents) {
                verify(dataFlowComponentRouter).route(recordsIn, dataFlowComponent, recordsToRoutes, null, componentRecordsConsumer);
            }
        }
    }

    @Nested
    class WithRecords {

        private Map<Record, Set<String>> recordsToRoutes;

        @BeforeEach
        void setUp() {
            recordsIn = IntStream.range(0, 10)
                    .mapToObj(i -> mock(Record.class))
                    .collect(Collectors.toList());
            ;
            dataFlowComponents = Collections.emptyList();

            recordsToRoutes = recordsIn
                    .stream()
                    .collect(Collectors.toMap(Function.identity(), r -> Collections.singleton(UUID.randomUUID().toString())));
            when(routeEventEvaluator.evaluateEventRoutes(recordsIn)).thenReturn(recordsToRoutes);
        }

        @Test
        void route_with_single_DataFlowComponent() {
            DataFlowComponent<TestComponent> dataFlowComponent = mock(DataFlowComponent.class);
            dataFlowComponents = Collections.singletonList(dataFlowComponent);

            createObjectUnderTest().route(recordsIn, dataFlowComponents, null, componentRecordsConsumer);

            verify(dataFlowComponentRouter).route(recordsIn, dataFlowComponent, recordsToRoutes, null, componentRecordsConsumer);
        }

        @Test
        void route_with_multiple_DataFlowComponents() {

            dataFlowComponents = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                final DataFlowComponent dataFlowComponent = mock(DataFlowComponent.class);
                dataFlowComponents.add(dataFlowComponent);
            }

            createObjectUnderTest().route(recordsIn, dataFlowComponents, null, componentRecordsConsumer);

            for (DataFlowComponent<TestComponent> dataFlowComponent : dataFlowComponents) {
                verify(dataFlowComponentRouter).route(recordsIn, dataFlowComponent, recordsToRoutes, null, componentRecordsConsumer);
            }
        }

        @Test
        void route_with_one_DataFlowComponents_And_PipelineConnector() {

            Collection<DataFlowComponent<PipelineConnector>> pipelineDataFlowComponents;
            pipelineDataFlowComponents = new ArrayList<>();
            final DataFlowComponent<PipelineConnector> dataFlowComponent = mock(DataFlowComponent.class);
            pipelineDataFlowComponents.add(dataFlowComponent);

            createObjectUnderTest().route(recordsIn, pipelineDataFlowComponents, null, pipelineComponentRecordsConsumer);

            verify(dataFlowComponentRouter).route(recordsIn, dataFlowComponent, recordsToRoutes, null, pipelineComponentRecordsConsumer);
        }

        @Test
        void route_with_multiple_DataFlowComponents_And_Strategy() {
            final DataFlowComponent dataFlowComponent = mock(DataFlowComponent.class);
            dataFlowComponents = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                dataFlowComponents.add(dataFlowComponent);
            }
            RouterCopyRecordStrategy routerCopyRecordStrategy = new RouterCopyRecordStrategy(dataFlowComponents);
            createObjectUnderTest().route(recordsIn, dataFlowComponents, routerCopyRecordStrategy, componentRecordsConsumer);
            verify(dataFlowComponentRouter, times(5)).route(recordsIn, dataFlowComponent, recordsToRoutes, routerCopyRecordStrategy, componentRecordsConsumer);
            createObjectUnderTest().route(recordsIn, dataFlowComponents, routerCopyRecordStrategy, (sink, recordsOut) -> {
                Set<Record> recordsInSet =  recordsIn.stream().collect(Collectors.toSet());
                Set<Record> recordsOutSet =  recordsOut.stream().collect(Collectors.toSet());
                assertThat(recordsInSet.size(), equalTo(recordsOutSet.size()));
                assertThat(recordsInSet, equalTo(recordsOutSet));
            });
        }

        @Test
        void route_with_multiple_pipeline_DataFlowComponents_And_Strategy() {
            pipelineDataFlowComponent = mock(DataFlowComponent.class);
            when(pipelineDataFlowComponent.getComponent()).thenReturn(new PipelineConnector());
            pipelineDataFlowComponents = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                pipelineDataFlowComponents.add(pipelineDataFlowComponent);
            }
            RouterCopyRecordStrategy routerCopyRecordStrategy = new RouterCopyRecordStrategy(pipelineDataFlowComponents);
            createObjectUnderTest().route(recordsIn, pipelineDataFlowComponents, routerCopyRecordStrategy, pipelineComponentRecordsConsumer);
            verify(dataFlowComponentRouter, times(5)).route(recordsIn, pipelineDataFlowComponent, recordsToRoutes, routerCopyRecordStrategy, pipelineComponentRecordsConsumer);
            createObjectUnderTest().route(recordsIn, pipelineDataFlowComponents, routerCopyRecordStrategy, (sink, recordsOut) -> {
                Set<Record> recordsOutSet =  recordsOut.stream().collect(Collectors.toSet());
                assertThat(recordsIn.size(), equalTo(recordsOutSet.size()));
                recordsIn.forEach(recordIn -> assertFalse(recordsOutSet.contains(recordIn)));
            });
        }
    }
}
