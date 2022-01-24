/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.dataprepper.plugins.otel.codec;

/**
 * An exception representing that opentelemetry protobuf data type was unable to
 * be converted to java Object.
 *
 * @since 1.3
 */
public class OTelEncodingException extends RuntimeException {
    public OTelEncodingException(Throwable cause) {
        super(cause);
    }
}
