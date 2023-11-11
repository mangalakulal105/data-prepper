/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.sink.opensearch.index;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.mockito.MockedStatic;
import org.opensearch.client.IndicesClient;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.cluster.GetClusterSettingsResponse;
import org.opensearch.dataprepper.model.event.JacksonEvent;
import org.opensearch.dataprepper.model.event.exceptions.EventKeyNotFoundException;
import org.opensearch.dataprepper.plugins.sink.opensearch.OpenSearchSinkConfiguration;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class DynamicIndexManagerTests {

    private static final String ID = "id";
    private static final String DYNAMIC = "dynamic";
    private static final String NEW_DYNAMIC = "new-dynamic";
    private static final String INDEX_ALIAS = "test-${" + ID + "}-index-alias";
    private static final String DATE_PATTERN = "yyyy.MM.dd";
    private static final String INDEX_ALIAS_WITH_DATE_PATTERN = INDEX_ALIAS+ "-%{" + DATE_PATTERN + "}";

    @Mock
    private IndexManagerFactory mockIndexManagerFactory;
    private IndexManagerFactory indexManagerFactory;

    private DynamicIndexManager dynamicIndexManager;

    private IndexManager innerIndexManager;

    @Mock
    private RestHighLevelClient restHighLevelClient;

    @Mock
    private OpenSearchClient openSearchClient;

    @Mock
    private OpenSearchSinkConfiguration openSearchSinkConfiguration;

    @Mock
    private IndexConfiguration indexConfiguration;

    @Mock
    private IndicesClient indicesClient;

    @Mock
    private ClusterSettingsParser clusterSettingsParser;

    @Mock
    private TemplateStrategy templateStrategy;

    @Mock
    private OpenSearchException openSearchException;

    static final String EVENT_TYPE = "event";

    @BeforeEach
    public void setup() throws IOException {
        initMocks(this);

        indexManagerFactory = new IndexManagerFactory(clusterSettingsParser);
        mockIndexManagerFactory = mock(IndexManagerFactory.class);
        when(openSearchSinkConfiguration.getIndexConfiguration()).thenReturn(indexConfiguration);
        when(indexConfiguration.getIsmPolicyFile()).thenReturn(Optional.empty());
        when(indexConfiguration.getIndexAlias()).thenReturn(INDEX_ALIAS);
        when(restHighLevelClient.indices()).thenReturn(indicesClient);
        dynamicIndexManager = new DynamicIndexManager(
                IndexType.CUSTOM, openSearchClient, restHighLevelClient, openSearchSinkConfiguration,
                clusterSettingsParser, templateStrategy, mockIndexManagerFactory);
    }

    @Test
    public void dynamicIndexBasicTest() throws IOException {
        when(indexConfiguration.getIndexAlias()).thenReturn(INDEX_ALIAS);
        when(clusterSettingsParser.getStringValueClusterSetting(any(GetClusterSettingsResponse.class), eq(IndexConstants.ISM_ENABLED_SETTING)))
                .thenReturn("true");
        String configuredIndexAlias = openSearchSinkConfiguration.getIndexConfiguration().getIndexAlias();
        String expectedIndexAlias = INDEX_ALIAS.replace("${" + ID + "}", DYNAMIC);
        innerIndexManager = mock(IndexManager.class);
        when(mockIndexManagerFactory.getIndexManager(
                IndexType.CUSTOM, openSearchClient, restHighLevelClient, openSearchSinkConfiguration, templateStrategy, expectedIndexAlias)).thenReturn(innerIndexManager);
        when(innerIndexManager.getIndexName(expectedIndexAlias)).thenReturn(expectedIndexAlias);
        JacksonEvent event = JacksonEvent.builder().withEventType(EVENT_TYPE).withData(Map.of(ID, DYNAMIC)).build();
        final String indexName = dynamicIndexManager.getIndexName(event.formatString(configuredIndexAlias));
        assertThat(expectedIndexAlias, equalTo(indexName));
    }

    @Test
    public void dynamicIndexWithDateTest() throws IOException {
        when(indexConfiguration.getIndexAlias()).thenReturn(INDEX_ALIAS_WITH_DATE_PATTERN);
        when(clusterSettingsParser.getStringValueClusterSetting(any(GetClusterSettingsResponse.class), eq(IndexConstants.ISM_ENABLED_SETTING)))
                .thenReturn("true");
        String configuredIndexAlias = openSearchSinkConfiguration.getIndexConfiguration().getIndexAlias();
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(DATE_PATTERN);
        String expectedIndexAlias = INDEX_ALIAS.replace("${" + ID + "}", DYNAMIC) + "-" + dateFormatter.format(AbstractIndexManager.getCurrentUtcTime());
        innerIndexManager = mock(IndexManager.class);
        when(mockIndexManagerFactory.getIndexManager(
                IndexType.CUSTOM, openSearchClient, restHighLevelClient, openSearchSinkConfiguration, templateStrategy, expectedIndexAlias)).thenReturn(innerIndexManager);
        when(innerIndexManager.getIndexName(expectedIndexAlias)).thenReturn(expectedIndexAlias);
        JacksonEvent event = JacksonEvent.builder().withEventType(EVENT_TYPE).withData(Map.of(ID, DYNAMIC)).build();
        final String indexName = dynamicIndexManager.getIndexName(event.formatString(configuredIndexAlias));
        assertThat(expectedIndexAlias, equalTo(indexName));
    }

    @Test
    public void dynamicIndexCacheTest() throws IOException {
        when(indexConfiguration.getIndexAlias()).thenReturn(INDEX_ALIAS);
        when(clusterSettingsParser.getStringValueClusterSetting(any(GetClusterSettingsResponse.class), eq(IndexConstants.ISM_ENABLED_SETTING)))
                .thenReturn("true");
        String configuredIndexAlias = openSearchSinkConfiguration.getIndexConfiguration().getIndexAlias();
        String expectedIndexAlias = INDEX_ALIAS.replace("${" + ID + "}", DYNAMIC);
        innerIndexManager = mock(IndexManager.class);
        when(mockIndexManagerFactory.getIndexManager(
                IndexType.CUSTOM, openSearchClient, restHighLevelClient, openSearchSinkConfiguration, templateStrategy, expectedIndexAlias)).thenReturn(innerIndexManager);
        when(innerIndexManager.getIndexName(expectedIndexAlias)).thenReturn(expectedIndexAlias);

        JacksonEvent event = JacksonEvent.builder().withEventType(EVENT_TYPE).withData(Map.of(ID, DYNAMIC)).build();
        // Try multiple times to make sure the getIndexManager is not called more than once and cached values are returned
        for (int i = 0; i < 10; i++) {
            final String indexName = dynamicIndexManager.getIndexName(event.formatString(configuredIndexAlias));
            assertThat(expectedIndexAlias, equalTo(indexName));
            verify(mockIndexManagerFactory, times(1)).getIndexManager(
                    eq(IndexType.CUSTOM), eq(openSearchClient), eq(restHighLevelClient), eq(openSearchSinkConfiguration), eq(templateStrategy), anyString());
        }

        expectedIndexAlias = INDEX_ALIAS.replace("${" + ID + "}", NEW_DYNAMIC);
        when(mockIndexManagerFactory.getIndexManager(
                IndexType.CUSTOM, openSearchClient, restHighLevelClient, openSearchSinkConfiguration, templateStrategy, expectedIndexAlias)).thenReturn(innerIndexManager);
        when(innerIndexManager.getIndexName(expectedIndexAlias)).thenReturn(expectedIndexAlias);
        event = JacksonEvent.builder().withEventType(EVENT_TYPE).withData(Map.of(ID, NEW_DYNAMIC)).build();
        final String newIndexName = dynamicIndexManager.getIndexName(event.formatString(configuredIndexAlias));
        // When a new index is used, verify that getIndexManager is called again
        verify(mockIndexManagerFactory, times(2)).getIndexManager(
                eq(IndexType.CUSTOM), eq(openSearchClient), eq(restHighLevelClient), eq(openSearchSinkConfiguration), eq(templateStrategy), anyString());
        assertThat(expectedIndexAlias, equalTo(newIndexName));
    }

    @Test
    public void missingDynamicIndexTest() throws IOException {
        when(indexConfiguration.getIndexAlias()).thenReturn(INDEX_ALIAS);
        when(clusterSettingsParser.getStringValueClusterSetting(any(GetClusterSettingsResponse.class), eq(IndexConstants.ISM_ENABLED_SETTING)))
                .thenReturn("true");
        String configuredIndexAlias = openSearchSinkConfiguration.getIndexConfiguration().getIndexAlias();

        JacksonEvent event = JacksonEvent.builder().withEventType(EVENT_TYPE).withData(Map.of(RandomStringUtils.randomAlphabetic(10), DYNAMIC)).build();
        assertThrows(EventKeyNotFoundException.class, () -> dynamicIndexManager.getIndexName(event.formatString(configuredIndexAlias)));
    }

    @Test
    public void getIndexName_DoesNotRetryOnNonOpenSearchExceptions() throws IOException {
        when(indexConfiguration.getIndexAlias()).thenReturn(INDEX_ALIAS);
        String configuredIndexAlias = openSearchSinkConfiguration.getIndexConfiguration().getIndexAlias();
        String expectedIndexAlias = INDEX_ALIAS.replace("${" + ID + "}", DYNAMIC);
        innerIndexManager = mock(IndexManager.class);
        when(mockIndexManagerFactory.getIndexManager(
                IndexType.CUSTOM, openSearchClient, restHighLevelClient, openSearchSinkConfiguration, templateStrategy, expectedIndexAlias)).thenReturn(innerIndexManager);
        doThrow(new RuntimeException())
                .when(innerIndexManager).setupIndex();

        JacksonEvent event = JacksonEvent.builder().withEventType(EVENT_TYPE).withData(Map.of(ID, DYNAMIC)).build();
        assertThrows(RuntimeException.class, () -> dynamicIndexManager.getIndexName(event.formatString(configuredIndexAlias)));

        verify(innerIndexManager, times(1)).setupIndex();
    }

    @Test
    public void getIndexName_DoesRetryOnOpenSearchExceptions_UntilSuccess() throws IOException {
        when(indexConfiguration.getIndexAlias()).thenReturn(INDEX_ALIAS);
        when(clusterSettingsParser.getStringValueClusterSetting(any(GetClusterSettingsResponse.class), eq(IndexConstants.ISM_ENABLED_SETTING)))
                .thenReturn("true");
        String configuredIndexAlias = openSearchSinkConfiguration.getIndexConfiguration().getIndexAlias();
        String expectedIndexAlias = INDEX_ALIAS.replace("${" + ID + "}", DYNAMIC);
        innerIndexManager = mock(IndexManager.class);
        when(mockIndexManagerFactory.getIndexManager(
                IndexType.CUSTOM, openSearchClient, restHighLevelClient, openSearchSinkConfiguration, templateStrategy, expectedIndexAlias)).thenReturn(innerIndexManager);
        doThrow(openSearchException)
                .doThrow(openSearchException)
                .doNothing()
                .when(innerIndexManager).setupIndex();
        when(innerIndexManager.getIndexName(expectedIndexAlias)).thenReturn(expectedIndexAlias);

        JacksonEvent event = JacksonEvent.builder().withEventType(EVENT_TYPE).withData(Map.of(ID, DYNAMIC)).build();
        final String indexName =  dynamicIndexManager.getIndexName(event.formatString(configuredIndexAlias));
        assertThat(expectedIndexAlias, equalTo(indexName));

        verify(innerIndexManager, times(3)).setupIndex();
    }

    @Test
    public void getIndexName_DoesRetryOnOpenSearchExceptions_UntilFailure() throws IOException {
        when(indexConfiguration.getIndexAlias()).thenReturn(INDEX_ALIAS);
        String configuredIndexAlias = openSearchSinkConfiguration.getIndexConfiguration().getIndexAlias();
        String expectedIndexAlias = INDEX_ALIAS.replace("${" + ID + "}", DYNAMIC);
        innerIndexManager = mock(IndexManager.class);
        when(mockIndexManagerFactory.getIndexManager(
                IndexType.CUSTOM, openSearchClient, restHighLevelClient, openSearchSinkConfiguration, templateStrategy, expectedIndexAlias)).thenReturn(innerIndexManager);
        doThrow(openSearchException)
                .doThrow(openSearchException)
                .doThrow(new RuntimeException())
                .when(innerIndexManager).setupIndex();
        when(innerIndexManager.getIndexName(expectedIndexAlias)).thenReturn(expectedIndexAlias);

        JacksonEvent event = JacksonEvent.builder().withEventType(EVENT_TYPE).withData(Map.of(ID, DYNAMIC)).build();
        assertThrows(RuntimeException.class, () -> dynamicIndexManager.getIndexName(event.formatString(configuredIndexAlias)));

        verify(innerIndexManager, times(3)).setupIndex();
    }

    @ParameterizedTest
    @CsvSource(value = {"INVALID_INDEX#, invalid_index", "-AAA:\\\"*+/\\\\|?#><, aaa", "_TeST_InDeX<, test_index", "--<t, t"})
    public void normalize_index_correctly_normalizes_invalid_indexes(final String dynamicIndexName, final String normalizedDynamicIndexName) throws IOException {
        when(indexConfiguration.isNormalizeIndex()).thenReturn(true);
        innerIndexManager = mock(IndexManager.class);


        when(mockIndexManagerFactory.getIndexManager(
                IndexType.CUSTOM, openSearchClient, restHighLevelClient, openSearchSinkConfiguration, templateStrategy, normalizedDynamicIndexName)).thenReturn(innerIndexManager);
        when(innerIndexManager.getIndexName(normalizedDynamicIndexName)).thenReturn(normalizedDynamicIndexName);

        final String result = dynamicIndexManager.getIndexName(dynamicIndexName);
        assertThat(result, equalTo(normalizedDynamicIndexName));
    }

    @Test
    public void normalize_index_correctly_normalizes_indexes_correctly_with_data_time_patterns() throws IOException {
        final String dynamicIndexName = "-<_-test-%{yyyy.MM.dd}";
        final String indexWithDateTimePatternResolved = "-<_-test-2023.11.11";
        final String normalizedDynamicIndexName = "test-2023.11.11";
        when(indexConfiguration.isNormalizeIndex()).thenReturn(true);
        innerIndexManager = mock(IndexManager.class);


        when(mockIndexManagerFactory.getIndexManager(
                IndexType.CUSTOM, openSearchClient, restHighLevelClient, openSearchSinkConfiguration, templateStrategy, normalizedDynamicIndexName)).thenReturn(innerIndexManager);
        when(innerIndexManager.getIndexName(normalizedDynamicIndexName)).thenReturn(normalizedDynamicIndexName);

        try (final MockedStatic<AbstractIndexManager> abstractIndexManagerMockedStatic = mockStatic(AbstractIndexManager.class)) {
            abstractIndexManagerMockedStatic.when(() -> AbstractIndexManager.getIndexAliasWithDate(dynamicIndexName))
                    .thenReturn(indexWithDateTimePatternResolved);
            final String result = dynamicIndexManager.getIndexName(dynamicIndexName);
            assertThat(result, equalTo(normalizedDynamicIndexName));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"*-<-", "<?"})
    public void normalize_index_resulting_in_empty_index_throws_expected_exception(final String dynamicIndexName) {
        when(indexConfiguration.isNormalizeIndex()).thenReturn(true);
        final RuntimeException exception = assertThrows(RuntimeException.class, () -> dynamicIndexManager.getIndexName(dynamicIndexName));

        assertThat(exception, notNullValue());
        assertThat(exception.getMessage(), startsWith("Unable to normalize index"));
    }
}
