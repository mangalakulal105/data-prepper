package org.opensearch.dataprepper.plugins.sink.threshold;

import org.opensearch.dataprepper.plugins.sink.buffer.Buffer;

/**
 * ThresholdCheck receives paramaters for which to reference the
 * limits of a buffer and CloudWatchLogsClient before making a
 * PutLogEvent request to AWS.
 */
public class ThresholdCheck {
    private final int batchSize;
    private final int maxEventSize;
    private final int maxRequestSize;
    private final int logSendInterval;

    ThresholdCheck (int batchSize, int maxEventSize, int maxRequestSize, int logSendInterval) {
        this.batchSize = batchSize;
        this.maxEventSize = maxEventSize;
        this.maxRequestSize = maxRequestSize;
        this.logSendInterval = logSendInterval;
    }

    /**
     * Checks if the interval passed in is equal to or greater
     * than the threshold interval for sending PutLogEvents.
     * @param currentTime int
     * @return boolean - true if greater than or equal to logInterval, false otherwise.
     */
    public boolean checkLogSendInterval(int currentTime) {
        return currentTime >= logSendInterval;
    }

    /**
     * Determines if the event size is greater than the max event size.
     * @param eventSize int denoting size of event.
     * @return boolean - true if greater than MaxEventSize, false otherwise.
     */
    public boolean checkMaxEventSize(int eventSize) {
        return eventSize > maxEventSize;
    }

    /**
     * Checks if the request size is greater than or equal to the current size passed in.
     * @param currentRequestSize int denoting size of request(Sum of PutLogEvent messages).
     * @return boolean - true if greater than or equal to the Max request size, smaller otherwise.
     */
    public boolean checkMaxRequestSize(int currentRequestSize) {
        return currentRequestSize >= maxRequestSize;
    }

    /**
     * Checks if the current batch size is equal to the threshold
     * batch size.
     * @param batchSize int denoting the size of the batch of PutLogEvents.
     * @return boolean - true if equal, false otherwise.
     */
    public boolean checkBatchSize(int batchSize) {
        return batchSize == this.batchSize;
    }
}
