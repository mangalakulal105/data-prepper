/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.source;

import org.opensearch.dataprepper.model.event.Event;

import java.util.Objects;
import java.util.function.BiConsumer;

class EventMetadataModifier implements BiConsumer<Event, S3ObjectReference> {
    private static final String BUCKET_FIELD_NAME = "bucket";
    private static final String KEY_FIELD_NAME = "key";
    private final String baseKey;

    EventMetadataModifier(final String metadataRootKey) {
        baseKey = generateBaseKey(metadataRootKey);
    }

    @Override
    public void accept(final Event event, final S3ObjectReference s3ObjectReference) {
        event.put(baseKey + BUCKET_FIELD_NAME, s3ObjectReference.getBucketName());
        event.put(baseKey + KEY_FIELD_NAME, s3ObjectReference.getKey());
    }

    private static String generateBaseKey(String metadataRootKey) {
        Objects.requireNonNull(metadataRootKey);

        if(metadataRootKey.startsWith("/"))
            metadataRootKey = metadataRootKey.substring(1);

        if(metadataRootKey.isEmpty() || metadataRootKey.endsWith("/"))
            return metadataRootKey;

        return metadataRootKey + "/";
    }
}
