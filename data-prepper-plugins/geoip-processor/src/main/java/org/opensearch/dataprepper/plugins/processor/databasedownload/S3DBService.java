/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.processor.databasedownload;


import org.opensearch.dataprepper.plugins.processor.GeoIPProcessorConfig;
import org.opensearch.dataprepper.plugins.processor.configuration.DatabasePathURLConfig;
import org.opensearch.dataprepper.plugins.processor.databaseenrich.DownloadFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.DirectoryDownload;
import software.amazon.awssdk.transfer.s3.model.DownloadDirectoryRequest;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.List;

/**
 * Implementation class for Download through S3
 */
public class S3DBService implements DBSource {

    private static final Logger LOG = LoggerFactory.getLogger(S3DBService.class);
    private GeoIPProcessorConfig geoIPProcessorConfig;
    private String bucketName;
    private String bucketPath;
    private final String prefixDir;

    /**
     * S3DBService constructor for initialisation of attributes
     * @param geoIPProcessorConfig geoIPProcessorConfig
     * @param prefixDir prefixDir
     */
    public S3DBService(GeoIPProcessorConfig geoIPProcessorConfig, String prefixDir) {
        this.geoIPProcessorConfig = geoIPProcessorConfig;
        this.prefixDir = prefixDir;
    }

    /**
     * Initialisation of Download through Url
     * @param s3URLs s3URLs
     */
    public void initiateDownload(List<DatabasePathURLConfig> s3URLs) {
        for (DatabasePathURLConfig s3Url : s3URLs) {
            try {
                URI uri = new URI(s3Url.getUrl());
                bucketName = uri.getHost();
                bucketPath = removeTrailingSlash(removeLeadingSlash(uri.getPath()));
                DBSource.createFolderIfNotExist(tempFolderPath + File.separator + prefixDir);
                buildRequestAndDownloadFile(bucketName, bucketPath);
            } catch (URISyntaxException ex) {
                LOG.info("Initiate Download Exception", ex);
            }
        }
    }

    /**
     * Removes leading slashes from the input string
     * @param str url path
     * @return String
     */
    public String removeLeadingSlash(String str) {
        StringBuilder sb = new StringBuilder(str);
        while (sb.length() > 0 && sb.charAt(0) == '/') {
            sb.deleteCharAt(0);
        }
        return sb.toString();
    }

    /**
     * Removes trial slashes from the input string
     * @param str url path
     * @return String
     */
    public String removeTrailingSlash(String str) {
        StringBuilder sb = new StringBuilder(str);
        while (sb.length() > 0 && sb.charAt(sb.length() - 1) == '/') {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }

    /**
     * Download the mmdb file from the S3
     * @param path path
     */
    public void buildRequestAndDownloadFile(String... path) {
        try {
            S3TransferManager transferManager = createCustomTransferManager();
            DirectoryDownload directoryDownload =
                    transferManager.downloadDirectory(
                            DownloadDirectoryRequest.builder()
                                    .destination(Paths.get(tempFolderPath + File.separator + prefixDir))
                                    .bucket(path[0])
                                    .listObjectsV2RequestTransformer(l -> l.prefix(path[1]))
                                    .build());
            directoryDownload.completionFuture().join();

        } catch (Exception ex) {
            throw new DownloadFailedException("Download failed: " + ex);
        }
    }

    /**
     * Create S3TransferManager instance
     *
     * @return S3TransferManager
     */
    public S3TransferManager createCustomTransferManager() {
        S3AsyncClient s3AsyncClient =
                S3AsyncClient.crtBuilder()
                        .region(geoIPProcessorConfig.getAwsAuthenticationOptions().getAwsRegion())
                        .credentialsProvider(geoIPProcessorConfig.getAwsAuthenticationOptions().authenticateAwsConfiguration())
                        .build();

        return S3TransferManager.builder()
                .s3Client(s3AsyncClient)
                .build();
    }
}

