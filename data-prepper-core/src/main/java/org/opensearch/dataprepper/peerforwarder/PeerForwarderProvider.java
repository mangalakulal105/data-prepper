/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.peerforwarder;

import com.amazon.dataprepper.model.record.Record;
import org.opensearch.dataprepper.peerforwarder.client.PeerForwarderClient;
import org.opensearch.dataprepper.peerforwarder.discovery.DiscoveryMode;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class PeerForwarderProvider {

    private final PeerForwarderClientFactory peerForwarderClientFactory;
    private final PeerForwarderClient peerForwarderClient;
    private final PeerForwarderConfiguration peerForwarderConfiguration;
    private final Map<String, Map<String, PeerForwarderReceiveBuffer<Record<?>>>> pipelinePeerForwarderReceiveBufferMap = new HashMap<>();
    private HashRing hashRing;

    PeerForwarderProvider(final PeerForwarderClientFactory peerForwarderClientFactory,
                          final PeerForwarderClient peerForwarderClient,
                          final PeerForwarderConfiguration peerForwarderConfiguration) {
        this.peerForwarderClientFactory = peerForwarderClientFactory;
        this.peerForwarderClient = peerForwarderClient;
        this.peerForwarderConfiguration = peerForwarderConfiguration;
    }

    public PeerForwarder register(final String pipelineName, final String pluginId, final Set<String> identificationKeys) {
        if (pipelinePeerForwarderReceiveBufferMap.containsKey(pipelineName) &&
                pipelinePeerForwarderReceiveBufferMap.get(pipelineName).containsKey(pluginId)) {
            throw new RuntimeException("Data Prepper 2.0 will only support a single peer-forwarder per pipeline/plugin type");
        }

        final PeerForwarderReceiveBuffer<Record<?>> peerForwarderReceiveBuffer = createBufferPerPipelineProcessor(pipelineName, pluginId);
        // TODO: pass buffer to RemotePeerForwarder constructor

        if (isPeerForwardingRequired()) {
            if (hashRing == null) {
                hashRing = peerForwarderClientFactory.createHashRing();
            }
            return new RemotePeerForwarder(peerForwarderClient, hashRing, pipelineName, pluginId, identificationKeys);
        }
        else {
            return new LocalPeerForwarder();
        }
    }

    private PeerForwarderReceiveBuffer<Record<?>> createBufferPerPipelineProcessor(final String pipelineName, final String pluginId) {
        final PeerForwarderReceiveBuffer<Record<?>> peerForwarderReceiveBuffer = new
                PeerForwarderReceiveBuffer<>(peerForwarderConfiguration.getBufferSize(), peerForwarderConfiguration.getBatchSize());
        if (pipelinePeerForwarderReceiveBufferMap.containsKey(pipelineName)) {
            pipelinePeerForwarderReceiveBufferMap.get(pipelineName).put(
                    pluginId, peerForwarderReceiveBuffer
            );
        }
        else {
            Map<String, PeerForwarderReceiveBuffer<Record<?>>> peerForwarderReceiveBufferMap = new HashMap<>();
            peerForwarderReceiveBufferMap.put(
                    pluginId, peerForwarderReceiveBuffer
            );
            pipelinePeerForwarderReceiveBufferMap.put(pipelineName, peerForwarderReceiveBufferMap);
        }
        return peerForwarderReceiveBuffer;
    }

    public boolean isPeerForwardingRequired() {
        return arePeersConfigured() && pipelinePeerForwarderReceiveBufferMap.size() > 0;
    }

    private boolean arePeersConfigured() {
        final DiscoveryMode discoveryMode = peerForwarderConfiguration.getDiscoveryMode();
        if (discoveryMode.equals(DiscoveryMode.LOCAL)) {
            return false;
        }
        else if (discoveryMode.equals(DiscoveryMode.STATIC) && peerForwarderConfiguration.getStaticEndpoints().size() <= 1) {
            return false;
        }
        return true;
    }

    public Map<String, Map<String, PeerForwarderReceiveBuffer<Record<?>>>> getPipelinePeerForwarderReceiveBufferMap() {
        return pipelinePeerForwarderReceiveBufferMap;
    }
}
