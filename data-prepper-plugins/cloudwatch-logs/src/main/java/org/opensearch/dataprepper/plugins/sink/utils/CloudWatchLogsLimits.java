/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.sink.utils;
/**
 * ThresholdCheck receives parameters for which to reference the
 * limits of a buffer and CloudWatchLogsClient before making a
 * PutLogEvent request to AWS.
 */
public class CloudWatchLogsLimits {
    public static final int APPROXIMATE_LOG_EVENT_OVERHEAD_SIZE = 26; //Size of overhead for each log event message.
    private final int maxBatchSize;
    private final int maxEventSizeBytes;
    private final int maxRequestSizeBytes;
    private final long logSendInterval;

    public CloudWatchLogsLimits(final int maxBatchSize, final int maxEventSizeBytes, final int maxRequestSizeBytes, final int logSendInterval) {
        this.maxBatchSize = maxBatchSize;
        this.maxEventSizeBytes = maxEventSizeBytes;
        this.maxRequestSizeBytes = maxRequestSizeBytes;
        this.logSendInterval = logSendInterval;
    }

    /**
     * Checks to see if we exceed any of the threshold conditions.
     * @param currentTime - (long) denoting the time in seconds.
     * @param currentRequestSize - size of request in bytes.
     * @param batchSize - size of batch in events.
     * @return boolean - true if we exceed the threshold events or false otherwise.
     */
    public boolean isGreaterThanLimitReached(final long currentTime, final int currentRequestSize, final int batchSize) {
        int bufferSizeWithOverhead = (currentRequestSize + ((batchSize) * APPROXIMATE_LOG_EVENT_OVERHEAD_SIZE));
        return (isGreaterThanBatchSize(batchSize) || isGreaterEqualToLogSendInterval(currentTime)
                || isGreaterThanMaxRequestSize(bufferSizeWithOverhead));
    }

    /**
     * Checks to see if we equal any of the threshold conditions.
     * @param currentRequestSize - size of request in bytes.
     * @param batchSize - size of batch in events.
     * @return boolean - true if we equal the threshold events or false otherwise.
     */
    public boolean isEqualToLimitReached(final int currentRequestSize, final int batchSize) {
        int bufferSizeWithOverhead = (currentRequestSize + ((batchSize) * APPROXIMATE_LOG_EVENT_OVERHEAD_SIZE));
        return (isEqualBatchSize(batchSize) || isEqualMaxRequestSize(bufferSizeWithOverhead));
    }

    /**
     * Checks if the interval passed in is equal to or greater
     * than the threshold interval for sending PutLogEvents.
     * @param currentTimeSeconds int denoting seconds.
     * @return boolean - true if greater than or equal to logInterval, false otherwise.
     */
    private boolean isGreaterEqualToLogSendInterval(final long currentTimeSeconds) {
        return currentTimeSeconds >= logSendInterval;
    }

    /**
     * Determines if the event size is greater than the max event size.
     * @param eventSize int denoting size of event.
     * @return boolean - true if greater than MaxEventSize, false otherwise.
     */
    public boolean isGreaterThanMaxEventSize(final int eventSize) {
        return (eventSize + APPROXIMATE_LOG_EVENT_OVERHEAD_SIZE) > maxEventSizeBytes;
    }

    /**
     * Checks if the request size is greater than or equal to the current size passed in.
     * @param currentRequestSize int denoting size of request(Sum of PutLogEvent messages).
     * @return boolean - true if greater than Max request size, smaller otherwise.
     */
    private boolean isGreaterThanMaxRequestSize(final int currentRequestSize) {
        return currentRequestSize > maxRequestSizeBytes;
    }

    /**
     * Checks if the current batch size is greater to the threshold
     * batch size.
     * @param batchSize int denoting the size of the batch of PutLogEvents.
     * @return boolean - true if greater, false otherwise.
     */
    private boolean isGreaterThanBatchSize(final int batchSize) {
        return batchSize > this.maxBatchSize;
    }

    /**
     * Checks if the request size is greater than or equal to the current size passed in.
     * @param currentRequestSize int denoting size of request(Sum of PutLogEvent messages).
     * @return boolean - true if equal Max request size, smaller otherwise.
     */
    private boolean isEqualMaxRequestSize(final int currentRequestSize) {
        return currentRequestSize == maxRequestSizeBytes;
    }

    private boolean isEqualBatchSize(final int batchSize) {
        return batchSize == this.maxBatchSize;
    }
}
