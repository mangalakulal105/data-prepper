/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.model.source.coordinator.exceptions;

import org.opensearch.dataprepper.model.source.coordinator.SourceCoordinator;

/**
 * This exception is thrown when a call to {@link SourceCoordinator} is made by a source for a partition
 * that is not owned by this instance of Data Prepper. If this exception gets thrown,
 * the recommended approach is to call {@link SourceCoordinator#getNextPartition()}
 * to grab a new partition to process
 * @since 2.2
 */
public class PartitionNotOwnedException extends RuntimeException {
    public PartitionNotOwnedException(final String message) {
        super(message);
    }
}
