/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.dataprepper.peerforwarder.discovery;

import com.google.common.net.InternetDomainName;
import org.apache.commons.validator.routines.InetAddressValidator;

class DiscoveryUtils {
    static boolean validateEndpoint(final String endpoint) {
        return InternetDomainName.isValid(endpoint) || InetAddressValidator.getInstance().isValid(endpoint);
    }
}
