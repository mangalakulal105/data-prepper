/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;

import java.nio.file.Paths;

/**
 * Execute entry into Data Prepper.
 */
@ComponentScan
public class DataPrepperExecute {
    private static final Logger LOG = LoggerFactory.getLogger(DataPrepperExecute.class);

    public static void main(final String ... args) {
        java.security.Security.setProperty("networkaddress.cache.ttl", "60");

        final String dataPrepperHome = System.getProperty("data-prepper.dir");
        if (dataPrepperHome == null) {
            throw new RuntimeException("Data Prepper home directory (data-prepper.dir) not set in system properties.");
        }

        final String dataPrepperPipelines = Paths.get(dataPrepperHome).resolve("pipelines/pipelines.yaml").toString();
        final String dataPrepperConfig = Paths.get(dataPrepperHome).resolve("config/data-prepper-config.yaml").toString();
        final ContextManager contextManager = new ContextManager(dataPrepperPipelines, dataPrepperConfig);
        final DataPrepper dataPrepper = contextManager.getDataPrepperBean();

        LOG.trace("Starting Data Prepper execution");
        dataPrepper.execute();
    }
}
