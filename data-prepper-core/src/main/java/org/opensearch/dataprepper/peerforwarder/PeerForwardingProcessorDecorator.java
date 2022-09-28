/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.peerforwarder;

import com.amazon.dataprepper.model.event.Event;
import com.amazon.dataprepper.model.peerforwarder.RequiresPeerForwarding;
import com.amazon.dataprepper.model.processor.Processor;
import com.amazon.dataprepper.model.record.Record;
import org.apache.commons.collections.CollectionUtils;
import org.opensearch.dataprepper.peerforwarder.exception.EmptyPeerForwarderPluginIdentificationKeysException;
import org.opensearch.dataprepper.peerforwarder.exception.UnsupportedPeerForwarderPluginException;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class PeerForwardingProcessorDecorator implements Processor<Record<Event>, Record<Event>> {
    private final PeerForwarder peerForwarder;
    private final Processor innerProcessor;

    public static List<Processor> decorateProcessors(
            final List<Processor> processors,
            final PeerForwarderProvider peerForwarderProvider,
            final String pipelineName,
            final String pluginId) {

        Set<String> identificationKeys;
        Processor firstInnerProcessor;
        if (!processors.isEmpty()) {
            firstInnerProcessor = processors.get(0);
        }
        else {
            return Collections.emptyList();
        }

        if (firstInnerProcessor instanceof RequiresPeerForwarding) {
            identificationKeys = new HashSet<> (((RequiresPeerForwarding) firstInnerProcessor).getIdentificationKeys());
        }
        else {
            throw new UnsupportedPeerForwarderPluginException(
                    "Peer Forwarding is only supported for plugins which implement RequiresPeerForwarding interface."
            );
        }

        // verify if identification keys of all processors are same with
        // identification keys of first processor in case Processor is annotated with SingleThread.
        processors.forEach(processor -> {
            if (processor instanceof RequiresPeerForwarding) {
                final Set<String> processorIdentificationKeys = new HashSet<>(((RequiresPeerForwarding) processor).getIdentificationKeys());
                if (!identificationKeys.equals(processorIdentificationKeys)) {
                    throw new RuntimeException(
                            "All the processors of same type within a single pipeline should have same identification keys.");
                }
            }
        });

        if (identificationKeys.isEmpty()) {
            throw new EmptyPeerForwarderPluginIdentificationKeysException(
                    "Peer Forwarder Plugin: %s cannot have empty identification keys." + pluginId);
        }

        final PeerForwarder peerForwarder = peerForwarderProvider.register(pipelineName, pluginId, identificationKeys);

        return processors.stream().map(processor -> new PeerForwardingProcessorDecorator(peerForwarder, processor))
                .collect(Collectors.toList());
    }

    private PeerForwardingProcessorDecorator(final PeerForwarder peerForwarder, final Processor innerProcessor) {
        this.peerForwarder = peerForwarder;
        this.innerProcessor = innerProcessor;
    }

    @Override
    public Collection<Record<Event>> execute(final Collection<Record<Event>> records) {
        final Collection<Record<Event>> recordsToProcessOnLocalPeer = peerForwarder.forwardRecords(records);
        final Collection<Record<Event>> receivedRecordsFromBuffer = peerForwarder.receiveRecords();

        final Collection<Record<Event>> recordsToProcessLocally = CollectionUtils.union(
                recordsToProcessOnLocalPeer, receivedRecordsFromBuffer);

        return innerProcessor.execute(recordsToProcessLocally);
    }

    @Override
    public void prepareForShutdown() {
        innerProcessor.prepareForShutdown();
    }

    @Override
    public boolean isReadyForShutdown() {
        return peerForwarder.isReadyForShutdown() && innerProcessor.isReadyForShutdown();
    }

    @Override
    public void shutdown() {
        innerProcessor.shutdown();
    }
}
