/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.processor.extension;

import org.opensearch.dataprepper.plugins.processor.databaseenrich.GeoIPDatabaseReader;
import org.opensearch.dataprepper.plugins.processor.extension.databasedownload.GeoIPDatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Implementation class of geoIP-processor plugin service class.
 * It is responsible for calling of mmdb files download
 */
public class GeoIPProcessorService {
    private static final Logger LOG = LoggerFactory.getLogger(GeoIPProcessorService.class);
    private final MaxMindConfig maxMindConfig;
    private final GeoIPDatabaseManager geoIPDatabaseManager;
    private final ReentrantReadWriteLock.ReadLock readLock;
    private Instant nextUpdateAt;
    private ExecutorService executorService = null;

    /**
     * GeoIPProcessorService constructor for initialization of required attributes
     *
     * @param geoIpServiceConfig geoIpServiceConfig
     */
    public GeoIPProcessorService(final GeoIpServiceConfig geoIpServiceConfig,
                                 final GeoIPDatabaseManager geoIPDatabaseManager,
                                 final ReentrantReadWriteLock.ReadLock readLock
                                 ) {
        this.maxMindConfig = geoIpServiceConfig.getMaxMindConfig();
        this.geoIPDatabaseManager = geoIPDatabaseManager;
        this.readLock = readLock;

        geoIPDatabaseManager.initiateDatabaseDownload();
        this.nextUpdateAt = Instant.now().plus(maxMindConfig.getDatabaseRefreshInterval());
    }

    public GeoIPDatabaseReader getGeoIPDatabaseReader() {
        readLock.lock();
        try {
            final GeoIPDatabaseReader geoIPDatabaseReader = geoIPDatabaseManager.getGeoIPDatabaseReader();
            geoIPDatabaseReader.retain();
            checkAndUpdateDatabases();
            return geoIPDatabaseReader;
        } finally {
            readLock.unlock();
        }
    }

    private synchronized void checkAndUpdateDatabases() {
        if (nextUpdateAt.isBefore(Instant.now())) {
            nextUpdateAt = Instant.now().plus(maxMindConfig.getDatabaseRefreshInterval());
            LOG.info("Trying to update geoip Database readers");
            executorService = Executors.newSingleThreadExecutor();
            executorService.execute(geoIPDatabaseManager::updateDatabaseReader);
            executorService.shutdown();
        }
    }

    public void shutdown() {
        if (executorService != null) {
            try {
                if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (final InterruptedException e) {
                executorService.shutdownNow();
            }
        }
        geoIPDatabaseManager.deleteDatabasesOnShutdown();
    }
}
