/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.pipeline.common;

import com.amazon.dataprepper.model.annotations.DataPrepperPlugin;
import com.amazon.dataprepper.model.annotations.SingleThread;
import com.amazon.dataprepper.model.configuration.PluginSetting;
import com.amazon.dataprepper.model.processor.Processor;
import com.amazon.dataprepper.model.record.Record;

import java.util.Collection;

@SingleThread
@DataPrepperPlugin(name = "test_processor", pluginType = Processor.class)
public class TestProcessor implements Processor<Record<String>, Record<String>> {
    public boolean isShutdown = false;

    public TestProcessor(final PluginSetting pluginSetting) {}

    @Override
    public Collection<Record<String>> execute(Collection<Record<String>> records) {
        return records;
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
        isShutdown = true;
    }
}
