/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.model.metric;

/**
 * Represents a bucket of a {@link Histogram}.
 * .
 * @since 1.4
 */
public interface Bucket {

    /**
     * Gets the lower bound of the bucket
     * @return the min
     * @since 1.4
     */
    Double getMin();

    /**
     * Gets the upper bound of the bucket
     * @return the max
     * @since 1.4
     */
    Double getMax();

    /**
     * Gets the number of events in a bucket
     * @return the count
     * @since 1.4
     */
    Long getCount();
}