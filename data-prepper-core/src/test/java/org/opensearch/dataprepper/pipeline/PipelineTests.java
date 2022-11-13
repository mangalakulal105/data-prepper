/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.pipeline;

import com.amazon.dataprepper.model.configuration.PluginSetting;
import com.amazon.dataprepper.model.processor.Processor;
import com.amazon.dataprepper.model.record.Record;
import com.amazon.dataprepper.model.sink.Sink;
import com.amazon.dataprepper.model.source.Source;
import org.opensearch.dataprepper.pipeline.common.TestPrepper;
import org.opensearch.dataprepper.pipeline.common.TestProcessor;
import org.opensearch.dataprepper.plugins.TestSink;
import org.opensearch.dataprepper.plugins.TestSource;
import com.amazon.dataprepper.plugins.buffer.blockingbuffer.BlockingBuffer;
import org.junit.After;
import org.junit.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PipelineTests {
    private static final int TEST_READ_BATCH_TIMEOUT = 3000;
    private static final int TEST_PROCESSOR_THREADS = 1;
    private static final String TEST_PIPELINE_NAME = "test-pipeline";

    private Pipeline testPipeline;

    @After
    public void teardown() {
        if (testPipeline != null && !testPipeline.isStopRequested()) {
            testPipeline.shutdown();
        }
    }

    @Test
    public void testPipelineState() {
        final Source<Record<String>> testSource = new TestSource();
        final TestSink testSink = new TestSink();
        final Pipeline testPipeline = new Pipeline(TEST_PIPELINE_NAME, testSource, new BlockingBuffer(TEST_PIPELINE_NAME),
                Collections.emptyList(), Collections.singletonList(testSink), TEST_PROCESSOR_THREADS, TEST_READ_BATCH_TIMEOUT);
        assertThat("Pipeline isStopRequested is expected to be false", testPipeline.isStopRequested(), is(false));
        assertThat("Pipeline is expected to have a default buffer", testPipeline.getBuffer(), notNullValue());
        assertTrue("Pipeline processors should be empty", testPipeline.getProcessorSets().isEmpty());
        testPipeline.execute();
        assertThat("Pipeline isStopRequested is expected to be false", testPipeline.isStopRequested(), is(false));
        testPipeline.shutdown();
        assertThat("Pipeline isStopRequested is expected to be true", testPipeline.isStopRequested(), is(true));
        assertThat("Sink shutdown should be called", testSink.isShutdown, is(true));
    }

    @Test
    public void testPipelineStateWithPrepper() {
        final Source<Record<String>> testSource = new TestSource();
        final TestSink testSink = new TestSink();
        final TestProcessor testProcessor = new TestPrepper(new PluginSetting("test_processor", new HashMap<>()));
        final Pipeline testPipeline = new Pipeline(TEST_PIPELINE_NAME, testSource, new BlockingBuffer(TEST_PIPELINE_NAME),
                Collections.singletonList(Collections.singletonList(testProcessor)),
                Collections.singletonList(testSink),
                TEST_PROCESSOR_THREADS, TEST_READ_BATCH_TIMEOUT);
        assertThat("Pipeline isStopRequested is expected to be false", testPipeline.isStopRequested(), is(false));
        assertThat("Pipeline is expected to have a default buffer", testPipeline.getBuffer(), notNullValue());
        assertEquals("Pipeline processorSets size should be 1", 1, testPipeline.getProcessorSets().size());
        testPipeline.execute();
        assertThat("Pipeline isStopRequested is expected to be false", testPipeline.isStopRequested(), is(false));
        testPipeline.shutdown();
        assertThat("Pipeline isStopRequested is expected to be true", testPipeline.isStopRequested(), is(true));
        assertThat("Sink shutdown should be called", testSink.isShutdown, is(true));
        assertThat("Processor shutdown should be called", testProcessor.isShutdown, is(true));
    }

    @Test
    public void testPipelineStateWithProcessor() {
        final Source<Record<String>> testSource = new TestSource();
        final TestSink testSink = new TestSink();
        final TestProcessor testProcessor = new TestProcessor(new PluginSetting("test_processor", new HashMap<>()));
        final Pipeline testPipeline = new Pipeline(TEST_PIPELINE_NAME, testSource, new BlockingBuffer(TEST_PIPELINE_NAME),
                Collections.singletonList(Collections.singletonList(testProcessor)),
                Collections.singletonList(testSink),
                TEST_PROCESSOR_THREADS, TEST_READ_BATCH_TIMEOUT);
        assertThat("Pipeline isStopRequested is expected to be false", testPipeline.isStopRequested(), is(false));
        assertThat("Pipeline is expected to have a default buffer", testPipeline.getBuffer(), notNullValue());
        assertEquals("Pipeline processorSets size should be 1", 1, testPipeline.getProcessorSets().size());
        testPipeline.execute();
        assertThat("Pipeline isStopRequested is expected to be false", testPipeline.isStopRequested(), is(false));
        testPipeline.shutdown();
        assertThat("Pipeline isStopRequested is expected to be true", testPipeline.isStopRequested(), is(true));
        assertThat("Sink shutdown should be called", testSink.isShutdown, is(true));
        assertThat("Processor shutdown should be called", testProcessor.isShutdown, is(true));
    }

    @Test
    public void testExecuteFailingSource() {
        final Source<Record<String>> testSource = new TestSource(true);
        final TestSink testSink = new TestSink();
        try {
            final Pipeline testPipeline = new Pipeline(TEST_PIPELINE_NAME, testSource, new BlockingBuffer(TEST_PIPELINE_NAME),
                    Collections.emptyList(), Collections.singletonList(testSink), TEST_PROCESSOR_THREADS, TEST_READ_BATCH_TIMEOUT);
            testPipeline.execute();
        } catch (Exception ex) {
            assertThat("Incorrect exception message", ex.getMessage().contains("Source is expected to fail"));
            assertThat("Exception while starting the source should have pipeline.isStopRequested to false",
                    !testPipeline.isStopRequested());
        }
    }

    @Test
    public void testExecuteFailingSink() {
        final Source<Record<String>> testSource = new TestSource();
        final Sink<Record<String>> testSink = new TestSink(true);
        try {
            testPipeline = new Pipeline(TEST_PIPELINE_NAME, testSource, new BlockingBuffer(TEST_PIPELINE_NAME),
                    Collections.emptyList(), Collections.singletonList(testSink), TEST_PROCESSOR_THREADS, TEST_READ_BATCH_TIMEOUT);
            testPipeline.execute();
            Thread.sleep(TEST_READ_BATCH_TIMEOUT);
        } catch (Exception ex) {
            assertThat("Incorrect exception message", ex.getMessage().contains("Sink is expected to fail"));
            assertThat("Exception from sink should trigger shutdown of pipeline", testPipeline.isStopRequested());
        }
    }

    @Test
    public void testExecuteFailingProcessor() {
        final Source<Record<String>> testSource = new TestSource();
        final Sink<Record<String>> testSink = new TestSink();
        final Processor<Record<String>, Record<String>> testProcessor = new Processor<Record<String>, Record<String>>() {
            @Override
            public Collection<Record<String>> execute(Collection<Record<String>> records) {
                throw new RuntimeException("Processor is expected to fail");
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
        };
        try {
            testPipeline = new Pipeline(TEST_PIPELINE_NAME, testSource, new BlockingBuffer(TEST_PIPELINE_NAME),
                    Collections.singletonList(Collections.singletonList(testProcessor)), Collections.singletonList(testSink),
                    TEST_PROCESSOR_THREADS, TEST_READ_BATCH_TIMEOUT);
            testPipeline.execute();
            Thread.sleep(TEST_READ_BATCH_TIMEOUT);
        } catch (Exception ex) {
            assertThat("Incorrect exception message", ex.getMessage().contains("Processor is expected to fail"));
            assertThat("Exception from processor should trigger shutdown of pipeline", testPipeline.isStopRequested());
        }
    }

    @Test
    public void testGetSource() {
        final Source<Record<String>> testSource = new TestSource();
        final TestSink testSink = new TestSink();
        final Pipeline testPipeline = new Pipeline(TEST_PIPELINE_NAME, testSource, new BlockingBuffer(TEST_PIPELINE_NAME),
                Collections.emptyList(), Collections.singletonList(testSink), TEST_PROCESSOR_THREADS, TEST_READ_BATCH_TIMEOUT);

        assertEquals(testSource, testPipeline.getSource());
    }

    @Test
    public void testGetSinks() {
        final Source<Record<String>> testSource = new TestSource();
        final TestSink testSink = new TestSink();
        final Pipeline testPipeline = new Pipeline(TEST_PIPELINE_NAME, testSource, new BlockingBuffer(TEST_PIPELINE_NAME),
                Collections.emptyList(), Collections.singletonList(testSink),
                TEST_PROCESSOR_THREADS, TEST_READ_BATCH_TIMEOUT);

        assertEquals(1, testPipeline.getSinks().size());
        assertEquals(testSink, testPipeline.getSinks().iterator().next());
    }
}
