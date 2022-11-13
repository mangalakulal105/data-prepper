/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.dataprepper.plugins.sink.opensearch;

import com.amazon.dataprepper.metrics.MetricNames;
import com.amazon.dataprepper.metrics.MetricsTestUtil;
import com.amazon.dataprepper.model.configuration.PluginSetting;
import com.amazon.dataprepper.model.event.Event;
import com.amazon.dataprepper.model.event.EventType;
import com.amazon.dataprepper.model.event.JacksonEvent;
import com.amazon.dataprepper.model.record.Record;
import com.amazon.dataprepper.plugins.sink.opensearch.bulk.BulkAction;
import com.amazon.dataprepper.plugins.sink.opensearch.index.IndexConfiguration;
import com.amazon.dataprepper.plugins.sink.opensearch.index.IndexConstants;
import com.amazon.dataprepper.plugins.sink.opensearch.index.IndexManager;
import com.amazon.dataprepper.plugins.sink.opensearch.index.IndexType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Measurement;
import org.apache.commons.io.FileUtils;
import org.apache.http.util.EntityUtils;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.client.RestClient;
import org.opensearch.common.Strings;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;

import javax.ws.rs.HttpMethod;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.amazon.dataprepper.plugins.sink.opensearch.OpenSearchIntegrationHelper.createContentParser;
import static com.amazon.dataprepper.plugins.sink.opensearch.OpenSearchIntegrationHelper.createOpenSearchClient;
import static com.amazon.dataprepper.plugins.sink.opensearch.OpenSearchIntegrationHelper.getHosts;
import static com.amazon.dataprepper.plugins.sink.opensearch.OpenSearchIntegrationHelper.isOSBundle;
import static com.amazon.dataprepper.plugins.sink.opensearch.OpenSearchIntegrationHelper.waitForClusterStateUpdatesToFinish;
import static com.amazon.dataprepper.plugins.sink.opensearch.OpenSearchIntegrationHelper.wipeAllTemplates;
import static org.apache.http.HttpStatus.SC_OK;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.closeTo;

public class OpenSearchSinkIT {
  private static final String PLUGIN_NAME = "opensearch";
  private static final String PIPELINE_NAME = "integTestPipeline";
  private static final String TEST_CUSTOM_INDEX_POLICY_FILE = "test-custom-index-policy-file.json";
  private static final String TEST_TEMPLATE_V1_FILE = "test-index-template.json";
  private static final String TEST_TEMPLATE_V2_FILE = "test-index-template-v2.json";
  private static final String DEFAULT_RAW_SPAN_FILE_1 = "raw-span-1.json";
  private static final String DEFAULT_RAW_SPAN_FILE_2 = "raw-span-2.json";
  private static final String DEFAULT_SERVICE_MAP_FILE = "service-map-1.json";

  private RestClient client;

  @BeforeEach
  public void metricsInit() throws IOException {
    MetricsTestUtil.initMetrics();

    client = createOpenSearchClient();
  }

  @AfterEach
  public void cleanOpenSearch() throws Exception {
    wipeAllOpenSearchIndices();
    wipeAllTemplates();
    waitForClusterStateUpdatesToFinish();
  }

  @Test
  public void testInstantiateSinkRawSpanDefault() throws IOException {
    final PluginSetting pluginSetting = generatePluginSetting(IndexType.TRACE_ANALYTICS_RAW.getValue(), null, null);
    OpenSearchSink sink = new OpenSearchSink(pluginSetting);
    final String indexAlias = IndexConstants.TYPE_TO_DEFAULT_ALIAS.get(IndexType.TRACE_ANALYTICS_RAW);
    Request request = new Request(HttpMethod.HEAD, indexAlias);
    Response response = client.performRequest(request);
    MatcherAssert.assertThat(response.getStatusLine().getStatusCode(), equalTo(SC_OK));
    final String index = String.format("%s-000001", indexAlias);
    final Map<String, Object> mappings = getIndexMappings(index);
    MatcherAssert.assertThat(mappings, notNullValue());
    MatcherAssert.assertThat((boolean) mappings.get("date_detection"), equalTo(false));
    sink.shutdown();

      if (isOSBundle()) {
          // Check managed index
          await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
                      MatcherAssert.assertThat(getIndexPolicyId(index), equalTo(IndexConstants.RAW_ISM_POLICY));
                  }
          );
      }

    // roll over initial index
    request = new Request(HttpMethod.POST, String.format("%s/_rollover", indexAlias));
    request.setJsonEntity("{ \"conditions\" : { } }\n");
    response = client.performRequest(request);
    MatcherAssert.assertThat(response.getStatusLine().getStatusCode(), equalTo(SC_OK));

    // Instantiate sink again
    sink = new OpenSearchSink(pluginSetting);
    // Make sure no new write index *-000001 is created under alias
    final String rolloverIndexName = String.format("%s-000002", indexAlias);
    request = new Request(HttpMethod.GET, rolloverIndexName + "/_alias");
    response = client.performRequest(request);
    MatcherAssert.assertThat(checkIsWriteIndex(EntityUtils.toString(response.getEntity()), indexAlias, rolloverIndexName), equalTo(true));
    sink.shutdown();

    if (isOSBundle()) {
      // Check managed index
      MatcherAssert.assertThat(getIndexPolicyId(rolloverIndexName), equalTo(IndexConstants.RAW_ISM_POLICY));
    }
  }

  @Test
  public void testInstantiateSinkRawSpanReservedAliasAlreadyUsedAsIndex() throws IOException {
    final String reservedIndexAlias = IndexConstants.TYPE_TO_DEFAULT_ALIAS.get(IndexType.TRACE_ANALYTICS_RAW);
    final Request request = new Request(HttpMethod.PUT, reservedIndexAlias);
    client.performRequest(request);
    final PluginSetting pluginSetting = generatePluginSetting(IndexType.TRACE_ANALYTICS_RAW.getValue(), null, null);
    Assert.assertThrows(String.format(IndexManager.INDEX_ALIAS_USED_AS_INDEX_ERROR, reservedIndexAlias),
            RuntimeException.class, () -> new OpenSearchSink(pluginSetting));
  }

  @ParameterizedTest
  @ArgumentsSource(MultipleRecordTypeArgumentProvider.class)
  public void testOutputRawSpanDefault(final Function<String, Record> stringToRecord) throws IOException, InterruptedException {
    final String testDoc1 = readDocFromFile(DEFAULT_RAW_SPAN_FILE_1);
    final String testDoc2 = readDocFromFile(DEFAULT_RAW_SPAN_FILE_2);
    final ObjectMapper mapper = new ObjectMapper();
    @SuppressWarnings("unchecked") final Map<String, Object> expData1 = mapper.readValue(testDoc1, Map.class);
    @SuppressWarnings("unchecked") final Map<String, Object> expData2 = mapper.readValue(testDoc2, Map.class);

    final List<Record<Object>> testRecords = Arrays.asList(stringToRecord.apply(testDoc1), stringToRecord.apply(testDoc2));
    final PluginSetting pluginSetting = generatePluginSetting(IndexType.TRACE_ANALYTICS_RAW.getValue(), null, null);
    final OpenSearchSink sink = new OpenSearchSink(pluginSetting);
    sink.output(testRecords);

    final String expIndexAlias = IndexConstants.TYPE_TO_DEFAULT_ALIAS.get(IndexType.TRACE_ANALYTICS_RAW);
    final List<Map<String, Object>> retSources = getSearchResponseDocSources(expIndexAlias);
    MatcherAssert.assertThat(retSources.size(), equalTo(2));
    MatcherAssert.assertThat(retSources, hasItems(expData1, expData2));
    MatcherAssert.assertThat(getDocumentCount(expIndexAlias, "_id", (String) expData1.get("spanId")), equalTo(Integer.valueOf(1)));
    sink.shutdown();

    // Verify metrics
    final List<Measurement> bulkRequestErrors = MetricsTestUtil.getMeasurementList(
            new StringJoiner(MetricNames.DELIMITER).add(PIPELINE_NAME).add(PLUGIN_NAME)
                    .add(OpenSearchSink.BULKREQUEST_ERRORS).toString());
    MatcherAssert.assertThat(bulkRequestErrors.size(), equalTo(1));
    Assert.assertEquals(0.0, bulkRequestErrors.get(0).getValue(), 0);
    final List<Measurement> bulkRequestLatencies = MetricsTestUtil.getMeasurementList(
            new StringJoiner(MetricNames.DELIMITER).add(PIPELINE_NAME).add(PLUGIN_NAME)
                    .add(OpenSearchSink.BULKREQUEST_LATENCY).toString());
    MatcherAssert.assertThat(bulkRequestLatencies.size(), equalTo(3));
    // COUNT
    Assert.assertEquals(1.0, bulkRequestLatencies.get(0).getValue(), 0);
    // TOTAL_TIME
    Assert.assertTrue(bulkRequestLatencies.get(1).getValue() > 0.0);
    // MAX
    Assert.assertTrue(bulkRequestLatencies.get(2).getValue() > 0.0);
    final List<Measurement> documentsSuccessMeasurements = MetricsTestUtil.getMeasurementList(
            new StringJoiner(MetricNames.DELIMITER).add(PIPELINE_NAME).add(PLUGIN_NAME)
                    .add(BulkRetryStrategy.DOCUMENTS_SUCCESS).toString());
    MatcherAssert.assertThat(documentsSuccessMeasurements.size(), equalTo(1));
    MatcherAssert.assertThat(documentsSuccessMeasurements.get(0).getValue(), closeTo(2.0, 0));
    final List<Measurement> documentsSuccessFirstAttemptMeasurements = MetricsTestUtil.getMeasurementList(
            new StringJoiner(MetricNames.DELIMITER).add(PIPELINE_NAME).add(PLUGIN_NAME)
                    .add(BulkRetryStrategy.DOCUMENTS_SUCCESS_FIRST_ATTEMPT).toString());
    MatcherAssert.assertThat(documentsSuccessFirstAttemptMeasurements.size(), equalTo(1));
    MatcherAssert.assertThat(documentsSuccessFirstAttemptMeasurements.get(0).getValue(), closeTo(2.0, 0));
    final List<Measurement> documentErrorsMeasurements = MetricsTestUtil.getMeasurementList(
            new StringJoiner(MetricNames.DELIMITER).add(PIPELINE_NAME).add(PLUGIN_NAME)
                    .add(BulkRetryStrategy.DOCUMENT_ERRORS).toString());
    MatcherAssert.assertThat(documentErrorsMeasurements.size(), equalTo(1));
    MatcherAssert.assertThat(documentErrorsMeasurements.get(0).getValue(), closeTo(0.0, 0));

    /**
     * Metrics: Bulk Request Size in Bytes
     */
    final List<Measurement> bulkRequestSizeBytesMetrics = MetricsTestUtil.getMeasurementList(
            new StringJoiner(MetricNames.DELIMITER).add(PIPELINE_NAME).add(PLUGIN_NAME)
                    .add(OpenSearchSink.BULKREQUEST_SIZE_BYTES).toString());
    MatcherAssert.assertThat(bulkRequestSizeBytesMetrics.size(), equalTo(3));
    MatcherAssert.assertThat(bulkRequestSizeBytesMetrics.get(0).getValue(), closeTo(1.0, 0));
    MatcherAssert.assertThat(bulkRequestSizeBytesMetrics.get(1).getValue(), closeTo(2058.0, 0));
    MatcherAssert.assertThat(bulkRequestSizeBytesMetrics.get(2).getValue(), closeTo(2058.0, 0));
  }

  @ParameterizedTest
  @ArgumentsSource(MultipleRecordTypeArgumentProvider.class)
  public void testOutputRawSpanWithDLQ(final Function<String, Record> stringToRecord) throws IOException, InterruptedException {
    // TODO: write test case
    final String testDoc1 = readDocFromFile("raw-span-error.json");
    final String testDoc2 = readDocFromFile(DEFAULT_RAW_SPAN_FILE_1);
    final ObjectMapper mapper = new ObjectMapper();
    @SuppressWarnings("unchecked") final Map<String, Object> expData = mapper.readValue(testDoc2, Map.class);
    final List<Record<Object>> testRecords = Arrays.asList(stringToRecord.apply(testDoc1), stringToRecord.apply(testDoc2));
    final PluginSetting pluginSetting = generatePluginSetting(IndexType.TRACE_ANALYTICS_RAW.getValue(), null, null);
    // generate temporary directory for dlq file
    final File tempDirectory = Files.createTempDirectory("").toFile();
    // add dlq file path into setting
    final String expDLQFile = tempDirectory.getAbsolutePath() + "/test-dlq.txt";
    pluginSetting.getSettings().put(RetryConfiguration.DLQ_FILE, expDLQFile);

    final OpenSearchSink sink = new OpenSearchSink(pluginSetting);
    sink.output(testRecords);
    sink.shutdown();

    final StringBuilder dlqContent = new StringBuilder();
    Files.lines(Paths.get(expDLQFile)).forEach(dlqContent::append);
    final String nonPrettyJsonString = mapper.writeValueAsString(mapper.readValue(testDoc1, JsonNode.class));
    MatcherAssert.assertThat(dlqContent.toString(), containsString(nonPrettyJsonString));
    final String expIndexAlias = IndexConstants.TYPE_TO_DEFAULT_ALIAS.get(IndexType.TRACE_ANALYTICS_RAW);
    final List<Map<String, Object>> retSources = getSearchResponseDocSources(expIndexAlias);
    MatcherAssert.assertThat(retSources.size(), equalTo(1));
    MatcherAssert.assertThat(retSources.get(0), equalTo(expData));

    // clean up temporary directory
    FileUtils.deleteQuietly(tempDirectory);

    // verify metrics
    final List<Measurement> documentsSuccessMeasurements = MetricsTestUtil.getMeasurementList(
            new StringJoiner(MetricNames.DELIMITER).add(PIPELINE_NAME).add(PLUGIN_NAME)
                    .add(BulkRetryStrategy.DOCUMENTS_SUCCESS).toString());
    MatcherAssert.assertThat(documentsSuccessMeasurements.size(), equalTo(1));
    MatcherAssert.assertThat(documentsSuccessMeasurements.get(0).getValue(), closeTo(1.0, 0));
    final List<Measurement> documentErrorsMeasurements = MetricsTestUtil.getMeasurementList(
            new StringJoiner(MetricNames.DELIMITER).add(PIPELINE_NAME).add(PLUGIN_NAME)
                    .add(BulkRetryStrategy.DOCUMENT_ERRORS).toString());
    MatcherAssert.assertThat(documentErrorsMeasurements.size(), equalTo(1));
    MatcherAssert.assertThat(documentErrorsMeasurements.get(0).getValue(), closeTo(1.0, 0));

    /**
     * Metrics: Bulk Request Size in Bytes
     */
    final List<Measurement> bulkRequestSizeBytesMetrics = MetricsTestUtil.getMeasurementList(
            new StringJoiner(MetricNames.DELIMITER).add(PIPELINE_NAME).add(PLUGIN_NAME)
                    .add(OpenSearchSink.BULKREQUEST_SIZE_BYTES).toString());
    MatcherAssert.assertThat(bulkRequestSizeBytesMetrics.size(), equalTo(3));
    MatcherAssert.assertThat(bulkRequestSizeBytesMetrics.get(0).getValue(), closeTo(1.0, 0));
    MatcherAssert.assertThat(bulkRequestSizeBytesMetrics.get(1).getValue(), closeTo(2072.0, 0));
    MatcherAssert.assertThat(bulkRequestSizeBytesMetrics.get(2).getValue(), closeTo(2072.0, 0));

  }

  @Test
  public void testInstantiateSinkServiceMapDefault() throws IOException {
    final PluginSetting pluginSetting = generatePluginSetting(IndexType.TRACE_ANALYTICS_SERVICE_MAP.getValue(), null, null);
    final OpenSearchSink sink = new OpenSearchSink(pluginSetting);
    final String indexAlias = IndexConstants.TYPE_TO_DEFAULT_ALIAS.get(IndexType.TRACE_ANALYTICS_SERVICE_MAP);
    final Request request = new Request(HttpMethod.HEAD, indexAlias);
    final Response response = client.performRequest(request);
    MatcherAssert.assertThat(response.getStatusLine().getStatusCode(), equalTo(SC_OK));
    final Map<String, Object> mappings = getIndexMappings(indexAlias);
    MatcherAssert.assertThat(mappings, notNullValue());
    MatcherAssert.assertThat((boolean) mappings.get("date_detection"), equalTo(false));
    sink.shutdown();

    if (isOSBundle()) {
      // Check managed index
      MatcherAssert.assertThat(getIndexPolicyId(indexAlias), nullValue());
    }
  }

  @ParameterizedTest
  @ArgumentsSource(MultipleRecordTypeArgumentProvider.class)
  public void testOutputServiceMapDefault(final Function<String, Record> stringToRecord) throws IOException, InterruptedException {
    final String testDoc = readDocFromFile(DEFAULT_SERVICE_MAP_FILE);
    final ObjectMapper mapper = new ObjectMapper();
    @SuppressWarnings("unchecked") final Map<String, Object> expData = mapper.readValue(testDoc, Map.class);

    final List<Record<Object>> testRecords = Collections.singletonList(stringToRecord.apply(testDoc));
    final PluginSetting pluginSetting = generatePluginSetting(IndexType.TRACE_ANALYTICS_SERVICE_MAP.getValue(), null, null);
    OpenSearchSink sink = new OpenSearchSink(pluginSetting);
    sink.output(testRecords);
    final String expIndexAlias = IndexConstants.TYPE_TO_DEFAULT_ALIAS.get(IndexType.TRACE_ANALYTICS_SERVICE_MAP);
    final List<Map<String, Object>> retSources = getSearchResponseDocSources(expIndexAlias);
    MatcherAssert.assertThat(retSources.size(), equalTo(1));
    MatcherAssert.assertThat(retSources.get(0), equalTo(expData));
    MatcherAssert.assertThat(getDocumentCount(expIndexAlias, "_id", (String) expData.get("hashId")), equalTo(Integer.valueOf(1)));
    sink.shutdown();

    // verify metrics
    final List<Measurement> bulkRequestLatencies = MetricsTestUtil.getMeasurementList(
            new StringJoiner(MetricNames.DELIMITER).add(PIPELINE_NAME).add(PLUGIN_NAME)
                    .add(OpenSearchSink.BULKREQUEST_LATENCY).toString());
    MatcherAssert.assertThat(bulkRequestLatencies.size(), equalTo(3));
    // COUNT
    MatcherAssert.assertThat(bulkRequestLatencies.get(0).getValue(), closeTo(1.0, 0));

    /**
     * Metrics: Bulk Request Size in Bytes
     */
    final List<Measurement> bulkRequestSizeBytesMetrics = MetricsTestUtil.getMeasurementList(
            new StringJoiner(MetricNames.DELIMITER).add(PIPELINE_NAME).add(PLUGIN_NAME)
                    .add(OpenSearchSink.BULKREQUEST_SIZE_BYTES).toString());
    MatcherAssert.assertThat(bulkRequestSizeBytesMetrics.size(), equalTo(3));
    MatcherAssert.assertThat(bulkRequestSizeBytesMetrics.get(0).getValue(), closeTo(1.0, 0));
    MatcherAssert.assertThat(bulkRequestSizeBytesMetrics.get(1).getValue(), closeTo(265.0, 0));
    MatcherAssert.assertThat(bulkRequestSizeBytesMetrics.get(2).getValue(), closeTo(265.0, 0));

    // Check restart for index already exists
    sink = new OpenSearchSink(pluginSetting);
    sink.shutdown();
  }

  @Test
  public void testInstantiateSinkCustomIndex_NoRollOver() throws IOException {
    final String testIndexAlias = "test-alias";
    final String testTemplateFile = Objects.requireNonNull(
            getClass().getClassLoader().getResource(TEST_TEMPLATE_V1_FILE)).getFile();
    final PluginSetting pluginSetting = generatePluginSetting(null, testIndexAlias, testTemplateFile);
    OpenSearchSink sink = new OpenSearchSink(pluginSetting);
    final Request request = new Request(HttpMethod.HEAD, testIndexAlias);
    final Response response = client.performRequest(request);
    MatcherAssert.assertThat(response.getStatusLine().getStatusCode(), equalTo(SC_OK));
    sink.shutdown();

    // Check restart for index already exists
    sink = new OpenSearchSink(pluginSetting);
    sink.shutdown();
  }

  @Test
  public void testInstantiateSinkCustomIndex_WithIsmPolicy() throws IOException {
    final String indexAlias = "sink-custom-index-ism-test-alias";
    final String testTemplateFile = Objects.requireNonNull(
            getClass().getClassLoader().getResource(TEST_TEMPLATE_V1_FILE)).getFile();
    final Map<String, Object> metadata = initializeConfigurationMetadata(null, indexAlias, testTemplateFile);
    metadata.put(IndexConfiguration.ISM_POLICY_FILE, TEST_CUSTOM_INDEX_POLICY_FILE);
    final PluginSetting pluginSetting = generatePluginSettingByMetadata(metadata);
    OpenSearchSink sink = new OpenSearchSink(pluginSetting);
    Request request = new Request(HttpMethod.HEAD, indexAlias);
    Response response = client.performRequest(request);
    MatcherAssert.assertThat(response.getStatusLine().getStatusCode(), equalTo(SC_OK));
    final String index = String.format("%s-000001", indexAlias);
    final Map<String, Object> mappings = getIndexMappings(index);
    MatcherAssert.assertThat(mappings, notNullValue());
    MatcherAssert.assertThat((boolean) mappings.get("date_detection"), equalTo(false));
    sink.shutdown();

    final String expectedIndexPolicyName = indexAlias + "-policy";
    if (isOSBundle()) {
      // Check managed index
      await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
        MatcherAssert.assertThat(getIndexPolicyId(index), equalTo(expectedIndexPolicyName)); }
      );
    }

    // roll over initial index
    request = new Request(HttpMethod.POST, String.format("%s/_rollover", indexAlias));
    request.setJsonEntity("{ \"conditions\" : { } }\n");
    response = client.performRequest(request);
    MatcherAssert.assertThat(response.getStatusLine().getStatusCode(), equalTo(SC_OK));

    // Instantiate sink again
    sink = new OpenSearchSink(pluginSetting);
    // Make sure no new write index *-000001 is created under alias
    final String rolloverIndexName = String.format("%s-000002", indexAlias);
    request = new Request(HttpMethod.GET, rolloverIndexName + "/_alias");
    response = client.performRequest(request);
    MatcherAssert.assertThat(checkIsWriteIndex(EntityUtils.toString(response.getEntity()), indexAlias, rolloverIndexName), equalTo(true));
    sink.shutdown();

    if (isOSBundle()) {
      // Check managed index
      MatcherAssert.assertThat(getIndexPolicyId(rolloverIndexName), equalTo(expectedIndexPolicyName));
    }
  }

  @Test
  public void testInstantiateSinkDoesNotOverwriteNewerIndexTemplates() throws IOException {
    final String testIndexAlias = "test-alias";
    final String expectedIndexTemplateName = testIndexAlias + "-index-template";
    final String testTemplateFileV1 = getClass().getClassLoader().getResource(TEST_TEMPLATE_V1_FILE).getFile();
    final String testTemplateFileV2 = getClass().getClassLoader().getResource(TEST_TEMPLATE_V2_FILE).getFile();

    // Create sink with template version 1
    PluginSetting pluginSetting = generatePluginSetting(null, testIndexAlias, testTemplateFileV1);
    OpenSearchSink sink = new OpenSearchSink(pluginSetting);

    Request getTemplateRequest = new Request(HttpMethod.GET, "/_template/" + expectedIndexTemplateName);
    Response getTemplateResponse = client.performRequest(getTemplateRequest);
    MatcherAssert.assertThat(getTemplateResponse.getStatusLine().getStatusCode(), equalTo(SC_OK));

    String responseBody = EntityUtils.toString(getTemplateResponse.getEntity());
    @SuppressWarnings("unchecked") final Integer firstResponseVersion =
            (Integer) ((Map<String, Object>) createContentParser(XContentType.JSON.xContent(),
            responseBody).map().get(expectedIndexTemplateName)).get("version");

    MatcherAssert.assertThat(firstResponseVersion, equalTo(Integer.valueOf(1)));
    sink.shutdown();

    // Create sink with template version 2
    pluginSetting = generatePluginSetting(null, testIndexAlias, testTemplateFileV2);
    sink = new OpenSearchSink(pluginSetting);

    getTemplateRequest = new Request(HttpMethod.GET, "/_template/" + expectedIndexTemplateName);
    getTemplateResponse = client.performRequest(getTemplateRequest);
    MatcherAssert.assertThat(getTemplateResponse.getStatusLine().getStatusCode(), equalTo(SC_OK));

    responseBody = EntityUtils.toString(getTemplateResponse.getEntity());
    @SuppressWarnings("unchecked") final Integer secondResponseVersion =
            (Integer) ((Map<String, Object>) createContentParser(XContentType.JSON.xContent(),
            responseBody).map().get(expectedIndexTemplateName)).get("version");

    MatcherAssert.assertThat(secondResponseVersion, equalTo(Integer.valueOf(2)));
    sink.shutdown();

    // Create sink with template version 1 again
    pluginSetting = generatePluginSetting(null, testIndexAlias, testTemplateFileV1);
    sink = new OpenSearchSink(pluginSetting);

    getTemplateRequest = new Request(HttpMethod.GET, "/_template/" + expectedIndexTemplateName);
    getTemplateResponse = client.performRequest(getTemplateRequest);
    MatcherAssert.assertThat(getTemplateResponse.getStatusLine().getStatusCode(), equalTo(SC_OK));

    responseBody = EntityUtils.toString(getTemplateResponse.getEntity());
    @SuppressWarnings("unchecked") final Integer thirdResponseVersion =
            (Integer) ((Map<String, Object>) createContentParser(XContentType.JSON.xContent(),
            responseBody).map().get(expectedIndexTemplateName)).get("version");

    // Assert version 2 was not overwritten by version 1
    MatcherAssert.assertThat(thirdResponseVersion, equalTo(Integer.valueOf(2)));
    sink.shutdown();

  }

  @ParameterizedTest
  @ArgumentsSource(MultipleRecordTypeArgumentProvider.class)
  public void testOutputCustomIndex(final Function<String, Record> stringToRecord) throws IOException, InterruptedException {
    final String testIndexAlias = "test-alias";
    final String testTemplateFile = Objects.requireNonNull(
            getClass().getClassLoader().getResource(TEST_TEMPLATE_V1_FILE)).getFile();
    final String testIdField = "someId";
    final String testId = "foo";
    final List<Record<Object>> testRecords = Collections.singletonList(stringToRecord.apply(generateCustomRecordJson(testIdField, testId)));
    final PluginSetting pluginSetting = generatePluginSetting(null, testIndexAlias, testTemplateFile);
    pluginSetting.getSettings().put(IndexConfiguration.DOCUMENT_ID_FIELD, testIdField);
    final OpenSearchSink sink = new OpenSearchSink(pluginSetting);
    sink.output(testRecords);
    final List<Map<String, Object>> retSources = getSearchResponseDocSources(testIndexAlias);
    MatcherAssert.assertThat(retSources.size(), equalTo(1));
    MatcherAssert.assertThat(getDocumentCount(testIndexAlias, "_id", testId), equalTo(Integer.valueOf(1)));
    sink.shutdown();

    // verify metrics
    final List<Measurement> bulkRequestLatencies = MetricsTestUtil.getMeasurementList(
            new StringJoiner(MetricNames.DELIMITER).add(PIPELINE_NAME).add(PLUGIN_NAME)
                    .add(OpenSearchSink.BULKREQUEST_LATENCY).toString());
    MatcherAssert.assertThat(bulkRequestLatencies.size(), equalTo(3));
    // COUNT
    Assert.assertEquals(1.0, bulkRequestLatencies.get(0).getValue(), 0);
  }

  @ParameterizedTest
  @ArgumentsSource(MultipleRecordTypeArgumentProvider.class)
  public void testBulkActionCreate(final Function<String, Record> stringToRecord) throws IOException, InterruptedException {
    final String testIndexAlias = "test-alias";
    final String testTemplateFile = Objects.requireNonNull(
            getClass().getClassLoader().getResource(TEST_TEMPLATE_V1_FILE)).getFile();
    final String testIdField = "someId";
    final String testId = "foo";
    final List<Record<Object>> testRecords = Collections.singletonList(stringToRecord.apply(generateCustomRecordJson(testIdField, testId)));
    final PluginSetting pluginSetting = generatePluginSetting(null, testIndexAlias, testTemplateFile);
    pluginSetting.getSettings().put(IndexConfiguration.DOCUMENT_ID_FIELD, testIdField);
    pluginSetting.getSettings().put(IndexConfiguration.ACTION, BulkAction.CREATE.toString());
    final OpenSearchSink sink = new OpenSearchSink(pluginSetting);
    sink.output(testRecords);
    final List<Map<String, Object>> retSources = getSearchResponseDocSources(testIndexAlias);
    MatcherAssert.assertThat(retSources.size(), equalTo(1));
    MatcherAssert.assertThat(getDocumentCount(testIndexAlias, "_id", testId), equalTo(Integer.valueOf(1)));
    sink.shutdown();

    // verify metrics
    final List<Measurement> bulkRequestLatencies = MetricsTestUtil.getMeasurementList(
            new StringJoiner(MetricNames.DELIMITER).add(PIPELINE_NAME).add(PLUGIN_NAME)
                    .add(OpenSearchSink.BULKREQUEST_LATENCY).toString());
    MatcherAssert.assertThat(bulkRequestLatencies.size(), equalTo(3));
    // COUNT
    Assert.assertEquals(1.0, bulkRequestLatencies.get(0).getValue(), 0);
  }

  @Test
  public void testEventOutput() throws IOException, InterruptedException {

    final Event testEvent = JacksonEvent.builder()
            .withData("{\"log\": \"foobar\"}")
            .withEventType("event")
            .build();

    final List<Record<Object>> testRecords = Collections.singletonList(new Record<>(testEvent));

    final PluginSetting pluginSetting = generatePluginSetting(IndexType.TRACE_ANALYTICS_RAW.getValue(), null, null);
    final OpenSearchSink sink = new OpenSearchSink(pluginSetting);
    sink.output(testRecords);

    final String expIndexAlias = IndexConstants.TYPE_TO_DEFAULT_ALIAS.get(IndexType.TRACE_ANALYTICS_RAW);
    final List<Map<String, Object>> retSources = getSearchResponseDocSources(expIndexAlias);
    final Map<String, Object> expectedContent = new HashMap<>();
    expectedContent.put("log", "foobar");

    MatcherAssert.assertThat(retSources.size(), equalTo(1));
    MatcherAssert.assertThat(retSources.containsAll(Arrays.asList(expectedContent)), equalTo(true));
    MatcherAssert.assertThat(getDocumentCount(expIndexAlias, "log", "foobar"), equalTo(Integer.valueOf(1)));
    sink.shutdown();
  }

  @ParameterizedTest
  @ArgumentsSource(MultipleRecordTypeArgumentProvider.class)
  @Timeout(value = 1, unit = TimeUnit.MINUTES)
  public void testOutputManagementDisabled(final Function<String, Record> stringToRecord) throws IOException, InterruptedException {
    final String testIndexAlias = "test-" + UUID.randomUUID();
    final String roleName = UUID.randomUUID().toString();
    final String username = UUID.randomUUID().toString();
    final String password = UUID.randomUUID().toString();
    final OpenSearchSecurityAccessor securityAccessor = new OpenSearchSecurityAccessor(client);
    securityAccessor.createBulkWritingRole(roleName, testIndexAlias + "*");
    securityAccessor.createUser(username, password, roleName);

    final String testIdField = "someId";
    final String testId = "foo";

    final List<Record<Object>> testRecords = Collections.singletonList(stringToRecord.apply(generateCustomRecordJson(testIdField, testId)));

    final Map<String, Object> metadata = initializeConfigurationMetadata(null, testIndexAlias, null);
    metadata.put(IndexConfiguration.INDEX_TYPE, IndexType.MANAGEMENT_DISABLED.getValue());
    metadata.put(ConnectionConfiguration.USERNAME, username);
    metadata.put(ConnectionConfiguration.PASSWORD, password);
    metadata.put(IndexConfiguration.DOCUMENT_ID_FIELD, testIdField);
    final PluginSetting pluginSetting = generatePluginSettingByMetadata(metadata);
    final OpenSearchSink sink = new OpenSearchSink(pluginSetting);

    final String testTemplateFile = Objects.requireNonNull(
            getClass().getClassLoader().getResource("management-disabled-index-template.json")).getFile();
    createIndexTemplate(testIndexAlias, testIndexAlias + "*", testTemplateFile);
    createIndex(testIndexAlias);

    sink.output(testRecords);
    final List<Map<String, Object>> retSources = getSearchResponseDocSources(testIndexAlias);
    MatcherAssert.assertThat(retSources.size(), equalTo(1));
    MatcherAssert.assertThat(getDocumentCount(testIndexAlias, "_id", testId), equalTo(Integer.valueOf(1)));
    sink.shutdown();

    // verify metrics
    final List<Measurement> bulkRequestLatencies = MetricsTestUtil.getMeasurementList(
            new StringJoiner(MetricNames.DELIMITER).add(PIPELINE_NAME).add(PLUGIN_NAME)
                    .add(OpenSearchSink.BULKREQUEST_LATENCY).toString());
    MatcherAssert.assertThat(bulkRequestLatencies.size(), equalTo(3));
    // COUNT
    Assert.assertEquals(1.0, bulkRequestLatencies.get(0).getValue(), 0);
  }

  private Map<String, Object> initializeConfigurationMetadata (final String indexType, final String indexAlias,
                                                               final String templateFilePath) {
    final Map<String, Object> metadata = new HashMap<>();
    metadata.put(IndexConfiguration.INDEX_TYPE, indexType);
    metadata.put(ConnectionConfiguration.HOSTS, getHosts());
    metadata.put(IndexConfiguration.INDEX_ALIAS, indexAlias);
    metadata.put(IndexConfiguration.TEMPLATE_FILE, templateFilePath);
    final String user = System.getProperty("tests.opensearch.user");
    final String password = System.getProperty("tests.opensearch.password");
    if (user != null) {
      metadata.put(ConnectionConfiguration.USERNAME, user);
      metadata.put(ConnectionConfiguration.PASSWORD, password);
    }
    return metadata;
  }

  private PluginSetting generatePluginSetting(final String indexType, final String indexAlias,
                                              final String templateFilePath) {
    final Map<String, Object> metadata = initializeConfigurationMetadata(indexType, indexAlias, templateFilePath);
    return generatePluginSettingByMetadata(metadata);
  }

  private PluginSetting generatePluginSettingByMetadata(final Map<String, Object> configurationMetadata) {
    final PluginSetting pluginSetting = new PluginSetting(PLUGIN_NAME, configurationMetadata);
    pluginSetting.setPipelineName(PIPELINE_NAME);
    return pluginSetting;
  }

  private String generateCustomRecordJson(final String idField, final String documentId) throws IOException {
    return Strings.toString(
            XContentFactory.jsonBuilder()
                    .startObject()
                    .field(idField, documentId)
                    .endObject()
    );
  }

  private String readDocFromFile(final String filename) throws IOException {
    final StringBuilder jsonBuilder = new StringBuilder();
    try (final InputStream inputStream = Objects.requireNonNull(
            getClass().getClassLoader().getResourceAsStream(filename))) {
      final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
      bufferedReader.lines().forEach(jsonBuilder::append);
    }
    return jsonBuilder.toString();
  }

  private Boolean checkIsWriteIndex(final String responseBody, final String aliasName, final String indexName) throws IOException {
    @SuppressWarnings("unchecked") final Map<String, Object> indexBlob = (Map<String, Object>) createContentParser(XContentType.JSON.xContent(),
            responseBody).map().get(indexName);
    @SuppressWarnings("unchecked") final Map<String, Object> aliasesBlob = (Map<String, Object>) indexBlob.get("aliases");
    @SuppressWarnings("unchecked") final Map<String, Object> aliasBlob = (Map<String, Object>) aliasesBlob.get(aliasName);
    return (Boolean) aliasBlob.get("is_write_index");
  }

  private Integer getDocumentCount(final String index, final String field, final String value) throws IOException, InterruptedException {
    final Request request = new Request(HttpMethod.GET, index + "/_count");
    if (field != null && value != null) {
      final String jsonEntity = Strings.toString(
              XContentFactory.jsonBuilder().startObject()
                      .startObject("query")
                      .startObject("match")
                      .field(field, value)
                      .endObject()
                      .endObject()
                      .endObject()
      );
      request.setJsonEntity(jsonEntity);
    }
    final Response response = client.performRequest(request);
    final String responseBody = EntityUtils.toString(response.getEntity());
    return (Integer) createContentParser(XContentType.JSON.xContent(), responseBody).map().get("count");
  }

  private List<Map<String, Object>> getSearchResponseDocSources(final String index) throws IOException {
    final Request refresh = new Request(HttpMethod.POST, index + "/_refresh");
    client.performRequest(refresh);
    final Request request = new Request(HttpMethod.GET, index + "/_search");
    final Response response = client.performRequest(request);
    final String responseBody = EntityUtils.toString(response.getEntity());

    @SuppressWarnings("unchecked") final List<Object> hits =
            (List<Object>) ((Map<String, Object>) createContentParser(XContentType.JSON.xContent(),
                    responseBody).map().get("hits")).get("hits");
    @SuppressWarnings("unchecked") final List<Map<String, Object>> sources = hits.stream()
            .map(hit -> (Map<String, Object>) ((Map<String, Object>) hit).get("_source"))
            .collect(Collectors.toList());
    return sources;
  }

  private Map<String, Object> getIndexMappings(final String index) throws IOException {
    final Request request = new Request(HttpMethod.GET, index + "/_mappings");
    final Response response = client.performRequest(request);
    final String responseBody = EntityUtils.toString(response.getEntity());

    @SuppressWarnings("unchecked") final Map<String, Object> mappings =
            (Map<String, Object>) ((Map<String, Object>) createContentParser(XContentType.JSON.xContent(),
                    responseBody).map().get(index)).get("mappings");
    return mappings;
  }

  private String getIndexPolicyId(final String index) throws IOException {
    // TODO: replace with new _opensearch API
    final Request request = new Request(HttpMethod.GET, "/_opendistro/_ism/explain/" + index);
    final Response response = client.performRequest(request);
    final String responseBody = EntityUtils.toString(response.getEntity());

    @SuppressWarnings("unchecked") final String policyId = (String) ((Map<String, Object>) createContentParser(XContentType.JSON.xContent(),
            responseBody).map().get(index)).get("index.opendistro.index_state_management.policy_id");
    return policyId;
  }


  @SuppressWarnings("unchecked")
  private void wipeAllOpenSearchIndices() throws IOException {
    final Response response = client.performRequest(new Request("GET", "/*?expand_wildcards=all"));

    final String responseBody = EntityUtils.toString(response.getEntity());
    final Map<String, Object> indexContent = createContentParser(XContentType.JSON.xContent(), responseBody).map();

    final Set<String> indices = indexContent.keySet();

    indices.stream()
            .filter(Objects::nonNull)
            .filter(indexName -> !".opendistro_security".equals(indexName))
            .forEach(indexName -> {
              try {
                client.performRequest(new Request("DELETE", "/" + indexName));
              } catch (final IOException e) {
                throw new RuntimeException(e);
              }
            });
  }

  /**
   * Provides a function for mapping a String to a Record to allow the tests to run
   * against both String and Event models.
   */
  static class MultipleRecordTypeArgumentProvider implements ArgumentsProvider {
    @Override
    public Stream<? extends Arguments> provideArguments(final ExtensionContext context) {
      final ObjectMapper objectMapper = new ObjectMapper();
      final Function<String, Record> stringModel = jsonString -> {
        try {
          // Normalize the JSON string.
          return new Record(objectMapper.writeValueAsString(objectMapper.readValue(jsonString, Map.class)));
        } catch (final JsonProcessingException e) {
          throw new RuntimeException(e);
        }
      };
      final Function<String, Record> eventModel = jsonString -> {
        try {
          return new Record(JacksonEvent.builder().withEventType(EventType.TRACE.toString()).withData(objectMapper.readValue(jsonString, Map.class)).build());
        } catch (final JsonProcessingException e) {
          throw new RuntimeException(e);
        }
      };
      return Stream.of(
              Arguments.of(stringModel),
              Arguments.of(eventModel)
      );
    }
  }

  private void createIndex(final String indexName) throws IOException {
    final Request request = new Request(HttpMethod.PUT, indexName);
    final Response response = client.performRequest(request);
  }

  private void createIndexTemplate(final String templateName, final String indexPattern, final String fileName) throws IOException {
    final ObjectMapper objectMapper = new ObjectMapper();
    final Map<String, Object> templateJson = objectMapper.readValue(new FileInputStream(fileName), Map.class);

    templateJson.put("index_patterns", indexPattern);

    final Request request = new Request(HttpMethod.PUT, "_template/" + templateName);

    final String createTemplateJson = objectMapper.writeValueAsString(templateJson);
    request.setJsonEntity(createTemplateJson);
    final Response response = client.performRequest(request);
  }
}
