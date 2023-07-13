/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.processor.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.opensearch.dataprepper.plugins.processor.configuration.DatabasePathURLConfig;
import org.opensearch.dataprepper.plugins.processor.databasedownload.DBSourceOptions;
import org.opensearch.dataprepper.test.helper.ReflectivelySetField;

import java.util.List;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DbSourceIdentificationTest {

    private static final String S3_URI = "s3://dataprepper/logdata/22833bd46b8e0.mmdb";
    private static final String S3_URL = "https://dataprepper.s3.amazonaws.com/logdata/22833bd46b8e0.json";
    private static final String URL = "https://www.dataprepper.com";
    private static final String PATH = "./src/test/resources/mmdb-file/geo-lite2";

    @Test
    void test_positive_case() {
        assertTrue(DbSourceIdentification.isS3Uri(S3_URI));
        assertTrue(DbSourceIdentification.isS3Url(S3_URL));
        assertTrue(DbSourceIdentification.isURL(URL));
        assertTrue(DbSourceIdentification.isFilePath(PATH));
    }

    @Test
    void test_negative_case() {
        assertFalse(DbSourceIdentification.isS3Uri(S3_URL));
        assertFalse(DbSourceIdentification.isS3Uri(URL));
        assertFalse(DbSourceIdentification.isS3Uri(PATH));

        assertFalse(DbSourceIdentification.isS3Url(S3_URI));
        assertFalse(DbSourceIdentification.isS3Url(URL));
        assertFalse(DbSourceIdentification.isS3Url(PATH));

        assertFalse(DbSourceIdentification.isURL(S3_URI));
        assertFalse(DbSourceIdentification.isURL(S3_URL));
        assertFalse(DbSourceIdentification.isURL(PATH));

        assertFalse(DbSourceIdentification.isFilePath(S3_URI));
        assertFalse(DbSourceIdentification.isFilePath(S3_URL));
        assertFalse(DbSourceIdentification.isFilePath(URL));
    }

    @Test
    void getDatabasePathTypeTest_PATH() throws NoSuchFieldException, IllegalAccessException {

        DatabasePathURLConfig databasePathURLConfig1 = new DatabasePathURLConfig();
        ReflectivelySetField.setField(DatabasePathURLConfig.class,
                databasePathURLConfig1, "url", "./src/test/resources/mmdb-file/geo-lite2");
        List<DatabasePathURLConfig> urlList = List.of(databasePathURLConfig1);
        DBSourceOptions dbSourceOptions = DbSourceIdentification.getDatabasePathType(urlList);
        Assertions.assertNotNull(dbSourceOptions);
        assertThat(dbSourceOptions, equalTo(DBSourceOptions.PATH));
    }

    @Test
    void getDatabasePathTypeTest_URL() throws NoSuchFieldException, IllegalAccessException {

        DatabasePathURLConfig databasePathURLConfig2 = new DatabasePathURLConfig();
        ReflectivelySetField.setField(DatabasePathURLConfig.class,
                databasePathURLConfig2, "url", "https://download.maxmind.com/app/geoip_download?" +
                        "edition_id=GeoLite2-ASN&license_key=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&suffix=tar.gz");
        List<DatabasePathURLConfig> urlList = List.of(databasePathURLConfig2);
        DBSourceOptions dbSourceOptions = DbSourceIdentification.getDatabasePathType(urlList);
        Assertions.assertNotNull(dbSourceOptions);
        assertThat(dbSourceOptions, equalTo(DBSourceOptions.URL));
    }

    @Test
    void getDatabasePathTypeTest_S3() throws NoSuchFieldException, IllegalAccessException {

        DatabasePathURLConfig databasePathURLConfig3 = new DatabasePathURLConfig();
        ReflectivelySetField.setField(DatabasePathURLConfig.class,
                databasePathURLConfig3, "url", "s3://mybucket10012023/GeoLite2/");
        List<DatabasePathURLConfig> urlList = List.of(databasePathURLConfig3);
        DBSourceOptions dbSourceOptions = DbSourceIdentification.getDatabasePathType(urlList);
        Assertions.assertNotNull(dbSourceOptions);
        assertThat(dbSourceOptions, equalTo(DBSourceOptions.S3));
    }

    @Test
    void isS3Uri_NullPointerException_test() {
        assertDoesNotThrow(() -> {
            DbSourceIdentification.isS3Uri(null);
        });
    }
}