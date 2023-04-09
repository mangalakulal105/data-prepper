/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.sink.opensearch;

import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.dataprepper.model.event.EventHandle;

public class BulkOperationWrapper {
    private final EventHandle eventHandle;
    private final BulkOperation bulkOperation;

    public BulkOperationWrapper(final BulkOperation bulkOperation) {
        this.bulkOperation = bulkOperation;
        this.eventHandle = null;
    }

    public BulkOperationWrapper(final BulkOperation bulkOperation, final EventHandle eventHandle) {
        this.bulkOperation = bulkOperation;
        this.eventHandle = eventHandle;
    }

    public BulkOperation getBulkOperation() {
        return bulkOperation;
    }

    public EventHandle getEventHandle() {
        return eventHandle;
    }
}
