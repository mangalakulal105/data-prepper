/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.peerforwarder;

import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import org.opensearch.dataprepper.metrics.PluginMetrics;
import org.opensearch.dataprepper.model.CheckpointState;
import org.opensearch.dataprepper.model.event.Event;
import org.opensearch.dataprepper.model.record.Record;
import io.micrometer.core.instrument.Counter;
import org.opensearch.dataprepper.peerforwarder.discovery.StaticPeerListProvider;
import org.opensearch.dataprepper.peerforwarder.client.PeerForwarderClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;

class RemotePeerForwarder implements PeerForwarder {
    private static final Logger LOG = LoggerFactory.getLogger(RemotePeerForwarder.class);
    static final String RECORDS_ACTUALLY_PROCESSED_LOCALLY = "recordsActuallyProcessedLocally";
    static final String RECORDS_TO_BE_PROCESSED_LOCALLY = "recordsToBeProcessedLocally";
    static final String RECORDS_TO_BE_FORWARDED = "recordsToBeForwarded";
    static final String RECORDS_SUCCESSFULLY_FORWARDED = "recordsSuccessfullyForwarded";
    static final String RECORDS_FAILED_FORWARDING = "recordsFailedForwarding";
    static final String RECORDS_MISSING_IDENTIFICATION_KEYS = "recordsMissingIdentificationKeys";
    static final String REQUESTS_FAILED = "requestsFailed";
    static final String REQUESTS_SUCCESSFUL = "requestsSuccessful";

    private final PeerForwarderClient peerForwarderClient;
    private final HashRing hashRing;
    private final PeerForwarderReceiveBuffer<Record<Event>> peerForwarderReceiveBuffer;
    private final String pipelineName;
    private final String pluginId;
    private final Set<String> identificationKeys;
    private final ConcurrentHashMap<String, LinkedBlockingQueue<Record<Event>>> peerBatchingQueueMap;

    private final Counter recordsActuallyProcessedLocallyCounter;
    private final Counter recordsToBeProcessedLocallyCounter;
    private final Counter recordsToBeForwardedCounter;
    private final Counter recordsSuccessfullyForwardedCounter;
    private final Counter recordsFailedForwardingCounter;
    private final Counter recordsMissingIdentificationKeys;
    private final Counter requestsFailedCounter;
    private final Counter requestsSuccessfulCounter;
    private final Integer batchDelay;
    private final Integer failedForwardingRequestLocalWriteTimeout;
    private final ExecutorService forwardingRequestExecutor;
    private final Integer forwardingBatchSize;

    RemotePeerForwarder(final PeerForwarderClient peerForwarderClient,
                        final HashRing hashRing,
                        final PeerForwarderReceiveBuffer<Record<Event>> peerForwarderReceiveBuffer,
                        final String pipelineName,
                        final String pluginId,
                        final Set<String> identificationKeys,
                        final PluginMetrics pluginMetrics,
                        final Integer batchDelay,
                        final Integer failedForwardingRequestLocalWriteTimeout,
                        final ExecutorService forwardingRequestExecutor,
                        final Integer forwardingBatchSize) {
        this.peerForwarderClient = peerForwarderClient;
        this.hashRing = hashRing;
        this.peerForwarderReceiveBuffer = peerForwarderReceiveBuffer;
        this.pipelineName = pipelineName;
        this.pluginId = pluginId;
        this.identificationKeys = identificationKeys;
        this.batchDelay = batchDelay;
        this.failedForwardingRequestLocalWriteTimeout = failedForwardingRequestLocalWriteTimeout;
        this.forwardingRequestExecutor = forwardingRequestExecutor;
        this.forwardingBatchSize = forwardingBatchSize;
        peerBatchingQueueMap = new ConcurrentHashMap<>();
        
        recordsActuallyProcessedLocallyCounter = pluginMetrics.counter(RECORDS_ACTUALLY_PROCESSED_LOCALLY);
        recordsToBeProcessedLocallyCounter = pluginMetrics.counter(RECORDS_TO_BE_PROCESSED_LOCALLY);
        recordsToBeForwardedCounter = pluginMetrics.counter(RECORDS_TO_BE_FORWARDED);
        recordsSuccessfullyForwardedCounter = pluginMetrics.counter(RECORDS_SUCCESSFULLY_FORWARDED);
        recordsFailedForwardingCounter = pluginMetrics.counter(RECORDS_FAILED_FORWARDING);
        recordsMissingIdentificationKeys = pluginMetrics.counter(RECORDS_MISSING_IDENTIFICATION_KEYS);
        requestsFailedCounter = pluginMetrics.counter(REQUESTS_FAILED);
        requestsSuccessfulCounter = pluginMetrics.counter(REQUESTS_SUCCESSFUL);
    }

    public Collection<Record<Event>> forwardRecords(final Collection<Record<Event>> records) {
        final Map<String, List<Record<Event>>> groupedRecords = groupRecordsBasedOnIdentificationKeys(records, identificationKeys);

        final List<Record<Event>> recordsToProcessLocally = new ArrayList<>();

        for (final Map.Entry<String, List<Record<Event>>> entry : groupedRecords.entrySet()) {
            final String destinationIp = entry.getKey();

            if (isAddressDefinedLocally(destinationIp)) {
                recordsToProcessLocally.addAll(entry.getValue());
                recordsToBeProcessedLocallyCounter.increment(entry.getValue().size());
            } else {
                recordsToBeForwardedCounter.increment(entry.getValue().size());
                try {
                    final List<Record<Event>> recordsToForward = batchRecordsForForwarding(destinationIp, entry.getValue());
                    if (!recordsToForward.isEmpty()) {
                        submitForwardingRequest(recordsToForward, destinationIp);
                    }
                } catch (final Exception ex) {
                    LOG.warn("Unable to submit request for forwarding, processing locally.", ex);
                    recordsToProcessLocally.addAll(entry.getValue());
                    recordsFailedForwardingCounter.increment(entry.getValue().size());
                    requestsFailedCounter.increment();
                }
            }
        }
        recordsActuallyProcessedLocallyCounter.increment(recordsToProcessLocally.size());
        return recordsToProcessLocally;
    }

    public Collection<Record<Event>> receiveRecords() {
        final Map.Entry<Collection<Record<Event>>, CheckpointState> readResult = peerForwarderReceiveBuffer.read(batchDelay);

        final Collection<Record<Event>> records = readResult.getKey();
        final CheckpointState checkpointState = readResult.getValue();

        // Checkpoint the current batch read from the buffer after reading from buffer
        peerForwarderReceiveBuffer.checkpoint(checkpointState);

        return records;
    }

    private Map<String, List<Record<Event>>> groupRecordsBasedOnIdentificationKeys(
            final Collection<Record<Event>> records,
            final Set<String> identificationKeys
    ) {
        final Map<String, List<Record<Event>>> groupedRecords = new HashMap<>();

        // group records based on IP address calculated by HashRing
        for (final Record<Event> record : records) {
            final Event event = record.getData();

            final List<String> identificationKeyValues = new LinkedList<>();
            int numMissingIdentificationKeys = 0;
            for (final String identificationKey : identificationKeys) {
                final Object identificationKeyValue = event.get(identificationKey, Object.class);
                if (identificationKeyValue == null) {
                    identificationKeyValues.add(null);
                    numMissingIdentificationKeys++;
                } else {
                    identificationKeyValues.add(identificationKeyValue.toString());
                }
            }
            if (numMissingIdentificationKeys == identificationKeys.size()) {
                recordsMissingIdentificationKeys.increment(1);
                identificationKeyValues.clear();
            }

            final String dataPrepperIp = hashRing.getServerIp(identificationKeyValues).orElse(StaticPeerListProvider.LOCAL_ENDPOINT);
            groupedRecords.computeIfAbsent(dataPrepperIp, x -> new ArrayList<>()).add(record);
        }
        return groupedRecords;
    }

    private boolean isAddressDefinedLocally(final String address) {
        final InetAddress inetAddress;
        try {
            inetAddress = InetAddress.getByName(address);
        } catch (final UnknownHostException e) {
            return false;
        }
        if (inetAddress.isAnyLocalAddress() || inetAddress.isLoopbackAddress()) {
            return true;
        } else {
            try {
                return NetworkInterface.getByInetAddress(inetAddress) != null;
            } catch (final SocketException e) {
                return false;
            }
        }
    }

    private List<Record<Event>> batchRecordsForForwarding(final String destinationIp, final List<Record<Event>> records) {
        if (!peerBatchingQueueMap.containsKey(destinationIp)) {
            peerBatchingQueueMap.put(destinationIp, new LinkedBlockingQueue<>(forwardingBatchSize * 5));
        } else {
            peerBatchingQueueMap.get(destinationIp).addAll(records);
        }

        if (peerBatchingQueueMap.get(destinationIp).size() >= forwardingBatchSize) {
            final List<Record<Event>> recordsToForward = new ArrayList<>();
            peerBatchingQueueMap.get(destinationIp).drainTo(recordsToForward, forwardingBatchSize);

            return recordsToForward;
        }

        return Collections.emptyList();
    }

    private void submitForwardingRequest(final Collection<Record<Event>> records, final String destinationIp) {
        forwardingRequestExecutor.submit(() -> {
            AggregatedHttpResponse aggregatedHttpResponse;
            try {
                aggregatedHttpResponse = peerForwarderClient.serializeRecordsAndSendHttpRequest(records, destinationIp, pluginId, pipelineName);
            } catch (final Exception ex) {
                LOG.warn("Unable to send request to peer, processing locally.", ex);
                aggregatedHttpResponse = null;
            }

            processFailedRequestsLocally(aggregatedHttpResponse, records);
        });
    }

    void processFailedRequestsLocally(final AggregatedHttpResponse httpResponse, final Collection<Record<Event>> records) {
        if (httpResponse == null || httpResponse.status() != HttpStatus.OK) {
            try {
                peerForwarderReceiveBuffer.writeAll(records, failedForwardingRequestLocalWriteTimeout);
                recordsActuallyProcessedLocallyCounter.increment(records.size());
            } catch (final Exception e) {
                LOG.error("Unable to write failed records to local peer forwarder receive buffer due to exception. Dropping {} records.", records.size(), e);
            }

            recordsFailedForwardingCounter.increment(records.size());
            requestsFailedCounter.increment();
        } else {
            recordsSuccessfullyForwardedCounter.increment(records.size());
            requestsSuccessfulCounter.increment();
        }
    }

}
