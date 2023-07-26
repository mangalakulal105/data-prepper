/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.dataprepper.plugins.sink;

import io.micrometer.core.instrument.Counter;

import org.opensearch.dataprepper.metrics.PluginMetrics;
import org.opensearch.dataprepper.model.configuration.PluginSetting;
import org.opensearch.dataprepper.model.event.Event;
import org.opensearch.dataprepper.model.event.EventHandle;
import org.opensearch.dataprepper.model.plugin.PluginFactory;
import org.opensearch.dataprepper.model.record.Record;
import org.opensearch.dataprepper.plugins.sink.dlq.DlqPushHandler;
import org.opensearch.dataprepper.plugins.sink.dlq.SNSSinkFailedDlqData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishBatchRequest;
import software.amazon.awssdk.services.sns.model.PublishBatchRequestEntry;
import software.amazon.awssdk.services.sns.model.PublishBatchResponse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Class responsible for create {@link SnsClient} object, check thresholds,
 * get new buffer and write records into buffer.
 */
public class SNSSinkService {

    private static final Logger LOG = LoggerFactory.getLogger(SNSSinkService.class);

    public static final String NUMBER_OF_RECORDS_FLUSHED_TO_SNS_SUCCESS = "snsSinkObjectsEventsSucceeded";

    public static final String NUMBER_OF_RECORDS_FLUSHED_TO_SNS_FAILED = "snsSinkObjectsEventsFailed";

    private static final String BUCKET = "bucket";

    private static final String KEY_PATH = "key_path_prefix";

    public static final String FIFO = ".fifo";

    private final SNSSinkConfig snsSinkConfig;

    private final Lock reentrantLock;

    private final Collection<EventHandle> bufferedEventHandles;

    private final SnsClient snsClient;

    private final DlqPushHandler dlqPushHandler;

    private final PluginSetting pluginSetting;

    private final List<String> processRecordsList;

    private final String topicName;

    private final int maxRetries;

    private final Counter numberOfRecordsSuccessCounter;

    private final Counter numberOfRecordsFailedCounter;

    /**
     * @param snsSinkConfig sns sink related configuration.
     * @param snsClient
     * @param pluginMetrics metrics.
     * @param pluginFactory
     * @param pluginSetting
     */
    public SNSSinkService(final SNSSinkConfig snsSinkConfig,
                          final SnsClient snsClient,
                          final PluginMetrics pluginMetrics,
                          final PluginFactory pluginFactory,
                          final PluginSetting pluginSetting) {
        this.snsSinkConfig = snsSinkConfig;
        this.snsClient = snsClient;
        this.reentrantLock = new ReentrantLock();
        this.bufferedEventHandles = new LinkedList<>();
        this.topicName = snsSinkConfig.getTopicArn();
        this.maxRetries = snsSinkConfig.getMaxUploadRetries();
        this.pluginSetting = pluginSetting;

        this.processRecordsList = new ArrayList();


        this.numberOfRecordsSuccessCounter = pluginMetrics.counter(NUMBER_OF_RECORDS_FLUSHED_TO_SNS_SUCCESS);
        this.numberOfRecordsFailedCounter = pluginMetrics.counter(NUMBER_OF_RECORDS_FLUSHED_TO_SNS_FAILED);

        this.dlqPushHandler = new DlqPushHandler(snsSinkConfig.getDlqFile(), pluginFactory,
                String.valueOf(snsSinkConfig.getDlqPluginSetting().get(BUCKET)),
                snsSinkConfig.getDlqStsRoleARN()
                ,snsSinkConfig.getDlqStsRegion(),
                String.valueOf(snsSinkConfig.getDlqPluginSetting().get(KEY_PATH)));
    }

    /**
     * @param records received records and add into buffer.
     */
    void output(Collection<Record<Event>> records) {
        reentrantLock.lock();
        try {
            for (Record<Event> record : records) {
                final Event event = record.getData();
                processRecordsList.add(event.toJsonString());
                if (event.getEventHandle() != null) {
                    bufferedEventHandles.add(event.getEventHandle());
                }
                if (snsSinkConfig.getBatchSize() == processRecordsList.size()) {
                    processRecords();
                    processRecordsList.clear();
                }
            }
            // This block will process the last set of events below batch size
            if(!processRecordsList.isEmpty()) {
                processRecords();
                processRecordsList.clear();
            }
        } catch (InterruptedException e) {
            LOG.error("Exception while write event into buffer :", e);
        }
        reentrantLock.unlock();
    }

    private void processRecords() throws InterruptedException {
        final AtomicReference<String> errorMsgObj = new AtomicReference<>();
        final boolean isFlushToSNS = retryFlushToSNS(processRecordsList, topicName,errorMsgObj);
        if (isFlushToSNS) {
            numberOfRecordsSuccessCounter.increment(processRecordsList.size());
        } else {
            numberOfRecordsFailedCounter.increment(processRecordsList.size());
            dlqPushHandler.perform(pluginSetting,new SNSSinkFailedDlqData(topicName,errorMsgObj.get(),0));
        }
        releaseEventHandles(true);
    }

    private void releaseEventHandles(final boolean result) {
        for (EventHandle eventHandle : bufferedEventHandles) {
            eventHandle.release(result);
        }
        bufferedEventHandles.clear();
    }

    /**
     * perform retry in-case any issue occurred, based on max_upload_retries configuration.
     *
     * @param processRecordsList records to be process
     * @param errorMsgObj pass the error message
     * @return boolean based on object upload status.
     * @throws InterruptedException interruption during sleep.
     */
    protected boolean retryFlushToSNS(final List<String> processRecordsList,
                                      final String topicName,
                                      final AtomicReference<String> errorMsgObj) throws InterruptedException {
        boolean isUploadedToSNS = Boolean.FALSE;
        int retryCount = maxRetries;
        do {
            try {
                publishToTopic(snsClient, topicName,processRecordsList);
                isUploadedToSNS = Boolean.TRUE;
            } catch (AwsServiceException | SdkClientException e) {
                errorMsgObj.set(e.getMessage());
                LOG.error("Exception occurred while uploading records to sns. Retry countdown  : {} | exception:",
                        retryCount, e);
                --retryCount;
                if (retryCount == 0) {
                    return isUploadedToSNS;
                }
                Thread.sleep(5000);
            }
        } while (!isUploadedToSNS);
        return isUploadedToSNS;
    }

    public void publishToTopic(SnsClient snsClient, String topicName,final List<String> processRecordsList) {
        LOG.debug("Trying to Push Msg to SNS: {}",topicName);
        try {
            final PublishBatchRequest request = createPublicRequestByTopic(topicName, processRecordsList);
            final PublishBatchResponse publishBatchResponse = snsClient.publishBatch(request);
            LOG.info(" Message sent. Status is " + publishBatchResponse.sdkHttpResponse().statusCode());
        }catch (Exception e) {
            throw e;
        }
    }

    private PublishBatchRequest createPublicRequestByTopic(final String topicName, final List<String>  processRecordsList) {
        final PublishBatchRequest.Builder requestBatch = PublishBatchRequest.builder().topicArn(topicName);
        final List<PublishBatchRequestEntry> batchRequestEntries = new ArrayList<PublishBatchRequestEntry>();
        final String defaultRandomGroupId = UUID.randomUUID().toString();
        if(processRecordsList.isEmpty()){
            System.out.println(processRecordsList.size());
        }
        for (String message : processRecordsList) {
            final PublishBatchRequestEntry.Builder entry = PublishBatchRequestEntry.builder().id(String.valueOf(new Random().nextInt())).message(message);
            if(topicName.endsWith(FIFO))
                batchRequestEntries.add(entry
                    .messageGroupId(snsSinkConfig.getMessageGroupId() != null ? snsSinkConfig.getMessageGroupId() : defaultRandomGroupId)
                    .messageDeduplicationId(snsSinkConfig.getMessageDeduplicationId() != null ? snsSinkConfig.getMessageDeduplicationId() : UUID.randomUUID().toString()).build());
            else
                batchRequestEntries.add(entry.build());
        }
        requestBatch.publishBatchRequestEntries(batchRequestEntries);
        return requestBatch.build();
    }

}
