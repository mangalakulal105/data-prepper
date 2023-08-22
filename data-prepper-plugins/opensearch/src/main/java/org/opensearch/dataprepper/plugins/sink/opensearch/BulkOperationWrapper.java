/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.sink.opensearch;

import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.dataprepper.model.event.EventHandle;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

public class BulkOperationWrapper {
    private static final Predicate<BulkOperation> IS_INDEX_OPERATION = BulkOperation::isIndex;
    private static final Predicate<BulkOperation> IS_CREATE_OPERATION = BulkOperation::isCreate;

    private static final Map<Predicate<BulkOperation>, Function<BulkOperation, Object>> BULK_OPERATION_TO_DOCUMENT_CONVERTERS = Map.of(
            IS_INDEX_OPERATION, operation -> operation.index().document(),
            IS_CREATE_OPERATION, operation -> operation.create().document()
    );

    private static final Map<Predicate<BulkOperation>, Function<BulkOperation, String>> BULK_OPERATION_TO_INDEX_NAME_CONVERTERS = Map.of(
            IS_INDEX_OPERATION, operation -> operation.index().index(),
            IS_CREATE_OPERATION, operation -> operation.create().index()
    );

    private static final Map<Predicate<BulkOperation>, Function<BulkOperation, String>> BULK_OPERATION_TO_ID_CONVERTERS = Map.of(
            IS_INDEX_OPERATION, operation -> operation.index().id(),
            IS_CREATE_OPERATION, operation -> operation.create().id()
    );

    private final EventHandle eventHandle;
    private final BulkOperation bulkOperation;

    public BulkOperationWrapper(final BulkOperation bulkOperation) {
        this.bulkOperation = bulkOperation;
        this.eventHandle = null;
    }

    public BulkOperationWrapper(final BulkOperation bulkOperation, final EventHandle eventHandle) {
        checkNotNull(bulkOperation);
        this.bulkOperation = bulkOperation;
        this.eventHandle = eventHandle;
    }

    public BulkOperation getBulkOperation() {
        return bulkOperation;
    }

    public EventHandle getEventHandle() {
        return eventHandle;
    }

    public void releaseEventHandle(boolean result) {
        if (eventHandle != null) {
            eventHandle.release(result);
        }
    }

    public Object getDocument() {
        return getValueFromConverter(BULK_OPERATION_TO_DOCUMENT_CONVERTERS);
    }

    public String getIndex() {
        return getValueFromConverter(BULK_OPERATION_TO_INDEX_NAME_CONVERTERS);
    }

    public String getId() {
        return getValueFromConverter(BULK_OPERATION_TO_ID_CONVERTERS);
    }

    private <T> T getValueFromConverter(final Map<Predicate<BulkOperation>, Function<BulkOperation, T>> converters) {
        final List<T> values = converters.entrySet().stream()
                .filter(entry -> entry.getKey().test(bulkOperation))
                .map(entry -> entry.getValue().apply(bulkOperation))
                .collect(Collectors.toList());

        if (values.size() != 1) {
            throw new UnsupportedOperationException("Only index or create operations are supported currently." + bulkOperation);
        }

        return values.get(0);
    }
}
