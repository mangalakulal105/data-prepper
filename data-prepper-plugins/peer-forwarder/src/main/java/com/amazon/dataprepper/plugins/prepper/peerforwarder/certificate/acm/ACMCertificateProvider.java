/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.dataprepper.plugins.prepper.peerforwarder.certificate.acm;

import com.amazon.dataprepper.plugins.prepper.peerforwarder.certificate.CertificateProvider;
import com.amazon.dataprepper.plugins.prepper.peerforwarder.certificate.model.Certificate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.acm.AcmClient;
import software.amazon.awssdk.services.acm.model.ExportCertificateRequest;
import software.amazon.awssdk.services.acm.model.ExportCertificateResponse;
import software.amazon.awssdk.services.acm.model.GetCertificateRequest;
import software.amazon.awssdk.services.acm.model.GetCertificateResponse;
import software.amazon.awssdk.services.acm.model.InvalidArnException;
import software.amazon.awssdk.services.acm.model.RequestInProgressException;
import software.amazon.awssdk.services.acm.model.ResourceNotFoundException;

import java.util.Objects;

public class ACMCertificateProvider implements CertificateProvider {
    private static final Logger LOG = LoggerFactory.getLogger(ACMCertificateProvider.class);
    private static final long SLEEP_INTERVAL = 10000L;
    private final AcmClient acmClient;
    private final String acmArn;
    private final long totalTimeout;
    public ACMCertificateProvider(final AcmClient acmClient,
                                  final String acmArn,
                                  final long totalTimeout) {
        this.acmClient = Objects.requireNonNull(acmClient);
        this.acmArn = Objects.requireNonNull(acmArn);
        this.totalTimeout = totalTimeout;
    }

    public Certificate getCertificate() {
        GetCertificateResponse getCertificateResponse = null;
        long timeSlept = 0L;

        while (getCertificateResponse == null && timeSlept < totalTimeout) {
            try {
                GetCertificateRequest getCertificateRequest = GetCertificateRequest.builder()
                        .certificateArn(acmArn)
                        .build();
                getCertificateResponse = acmClient.getCertificate(getCertificateRequest);

            } catch (final RequestInProgressException ex) {
                try {
                    Thread.sleep(SLEEP_INTERVAL);
                } catch (InterruptedException iex) {
                    throw new RuntimeException(iex);
                }
            } catch (final ResourceNotFoundException | InvalidArnException ex) {
                LOG.error("Exception retrieving the certificate with arn: {}", acmArn, ex);
                throw ex;
            }
            timeSlept += SLEEP_INTERVAL;
        }
        if(getCertificateResponse != null) {
            return new Certificate(getCertificateResponse.certificate());
        } else {
            throw new IllegalStateException(String.format("Exception retrieving certificate results. Time spent retrieving certificate is %d ms and total time out set is %d ms.", timeSlept, totalTimeout));
        }
    }
}
