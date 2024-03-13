/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.geoip.utils;

import com.google.common.net.InetAddresses;
import org.opensearch.dataprepper.plugins.geoip.exception.InvalidIPAddressException;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

/**
 * Implementation of class for checking IP validation
 * IP should be public it can be either IPV4 or IPV6
 */
public class IPValidationCheck {

    /**
     * Check for IP is valid or not
     * @param ipAddress ipAddress
     * @return boolean
     * @throws  InvalidIPAddressException InvalidIPAddressException
     */
    public static boolean isPublicIpAddress(final String ipAddress) {
        InetAddress address;
        try {
            address = InetAddresses.forString(ipAddress);
        } catch (final IllegalArgumentException e) {
            return false;
        }
        if (address instanceof Inet6Address || address instanceof Inet4Address) {
            return !address.isSiteLocalAddress() && !address.isLoopbackAddress();
        }
        return false;
    }
}
