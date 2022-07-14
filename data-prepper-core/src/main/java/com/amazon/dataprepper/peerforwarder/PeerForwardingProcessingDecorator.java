/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.dataprepper.peerforwarder;

import com.amazon.dataprepper.model.event.Event;
import com.amazon.dataprepper.model.processor.Processor;
import com.amazon.dataprepper.model.record.Record;
import org.apache.commons.lang3.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

public class PeerForwardingProcessingDecorator implements Processor<Record<Event>, Record<Event>> {
    private static final Logger LOG = LoggerFactory.getLogger(PeerForwardingProcessingDecorator.class);


    @Override
    public Collection<Record<Event>> execute(Collection<Record<Event>> collection) {
        LOG.info("Peer Forwarder not implemented yet, skipping events for now.");
        throw new NotImplementedException();
    }

    @Override
    public void prepareForShutdown() {

    }

    @Override
    public boolean isReadyForShutdown() {
        return false;
    }

    @Override
    public void shutdown() {

    }
}
