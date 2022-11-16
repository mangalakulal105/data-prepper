/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.prepper;

import com.amazon.dataprepper.model.annotations.DataPrepperPlugin;
import com.amazon.dataprepper.model.event.Event;
import com.amazon.dataprepper.model.processor.Processor;
import com.amazon.dataprepper.model.record.Record;

import java.util.Collection;

@DataPrepperPlugin(name = "no-op", pluginType = Processor.class)
public class NoOpPrepper implements Processor<Record<Event>, Record<Event>> {

    public NoOpPrepper() {

    }

    @Override
    public Collection<Record<Event>> execute(final Collection<Record<Event>> records) {
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

    }
}
