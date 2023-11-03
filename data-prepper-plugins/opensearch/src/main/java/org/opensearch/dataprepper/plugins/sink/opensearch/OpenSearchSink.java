/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.sink.opensearch;

import org.opensearch.dataprepper.plugins.sink.SinkResponseLatency;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linecorp.armeria.client.retry.Backoff;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Timer;
import org.apache.commons.lang3.StringUtils;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.CreateOperation;
import org.opensearch.client.opensearch.core.bulk.DeleteOperation;
import org.opensearch.client.opensearch.core.bulk.IndexOperation;
import org.opensearch.client.opensearch.core.bulk.UpdateOperation;
import org.opensearch.client.transport.TransportOptions;
import org.opensearch.common.unit.ByteSizeUnit;
import org.opensearch.dataprepper.aws.api.AwsCredentialsSupplier;
import org.opensearch.dataprepper.expression.ExpressionEvaluationException;
import org.opensearch.dataprepper.expression.ExpressionEvaluator;
import org.opensearch.dataprepper.metrics.MetricNames;
import org.opensearch.dataprepper.model.annotations.DataPrepperPlugin;
import org.opensearch.dataprepper.model.annotations.DataPrepperPluginConstructor;
import org.opensearch.dataprepper.model.configuration.PluginModel;
import org.opensearch.dataprepper.model.configuration.PluginSetting;
import org.opensearch.dataprepper.model.event.Event;
import org.opensearch.dataprepper.model.event.exceptions.EventKeyNotFoundException;
import org.opensearch.dataprepper.model.failures.DlqObject;
import org.opensearch.dataprepper.model.opensearch.OpenSearchBulkActions;
import org.opensearch.dataprepper.model.plugin.InvalidPluginConfigurationException;
import org.opensearch.dataprepper.model.plugin.PluginFactory;
import org.opensearch.dataprepper.model.record.Record;
import org.opensearch.dataprepper.model.sink.AbstractSink;
import org.opensearch.dataprepper.model.sink.Sink;
import org.opensearch.dataprepper.model.sink.SinkContext;
import org.opensearch.dataprepper.plugins.dlq.DlqProvider;
import org.opensearch.dataprepper.plugins.dlq.DlqWriter;
import org.opensearch.dataprepper.plugins.sink.opensearch.bulk.AccumulatingBulkRequest;
import org.opensearch.dataprepper.plugins.sink.opensearch.bulk.BulkApiWrapper;
import org.opensearch.dataprepper.plugins.sink.opensearch.bulk.BulkApiWrapperFactory;
import org.opensearch.dataprepper.plugins.sink.opensearch.bulk.BulkOperationWriter;
import org.opensearch.dataprepper.plugins.sink.opensearch.bulk.JavaClientAccumulatingCompressedBulkRequest;
import org.opensearch.dataprepper.plugins.sink.opensearch.bulk.JavaClientAccumulatingUncompressedBulkRequest;
import org.opensearch.dataprepper.plugins.sink.opensearch.bulk.SerializedJson;
import org.opensearch.dataprepper.plugins.sink.opensearch.dlq.FailedBulkOperation;
import org.opensearch.dataprepper.plugins.sink.opensearch.dlq.FailedBulkOperationConverter;
import org.opensearch.dataprepper.plugins.sink.opensearch.dlq.FailedDlqData;
import org.opensearch.dataprepper.plugins.sink.opensearch.index.ClusterSettingsParser;
import org.opensearch.dataprepper.plugins.sink.opensearch.index.DocumentBuilder;
import org.opensearch.dataprepper.plugins.sink.opensearch.index.IndexManager;
import org.opensearch.dataprepper.plugins.sink.opensearch.index.IndexManagerFactory;
import org.opensearch.dataprepper.plugins.sink.opensearch.index.IndexTemplateAPIWrapper;
import org.opensearch.dataprepper.plugins.sink.opensearch.index.IndexTemplateAPIWrapperFactory;
import org.opensearch.dataprepper.plugins.sink.opensearch.index.IndexType;
import org.opensearch.dataprepper.plugins.sink.opensearch.index.TemplateStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.opensearchserverless.OpenSearchServerlessClient;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.time.Duration;
import com.google.common.util.concurrent.AtomicDouble;

@DataPrepperPlugin(name = "opensearch", pluginType = Sink.class)
public class OpenSearchSink extends AbstractSink<Record<Event>> {
  public static final String BULKREQUEST_LATENCY = "bulkRequestLatency";
  public static final String BULKREQUEST_ERRORS = "bulkRequestErrors";
  public static final String INVALID_ACTION_ERRORS = "invalidActionErrors";
  public static final String BULKREQUEST_SIZE_BYTES = "bulkRequestSizeBytes";
  public static final String DYNAMIC_INDEX_DROPPED_EVENTS = "dynamicIndexDroppedEvents";
  public static final String LATENCY_THRESHOLD_MS="latency_threshold_ms";
  public static final String HIGH_LATENCY_RESPONSES_THRESHOLD="high_latency_responses_threshold";
  public static final int MAXIMUM_DELAY_MS = 60000; // one minute
  public static final int INITIAL_DELAY_MS = 1000; // one second

  private static final Logger LOG = LoggerFactory.getLogger(OpenSearchSink.class);
  private static final int INITIALIZE_RETRY_WAIT_TIME_MS = 5000;
  private final AwsCredentialsSupplier awsCredentialsSupplier;

  private DlqWriter dlqWriter;
  private BufferedWriter dlqFileWriter;
  private final OpenSearchSinkConfiguration openSearchSinkConfig;
  private final IndexManagerFactory indexManagerFactory;
  private RestHighLevelClient restHighLevelClient;
  private IndexManager indexManager;
  private Supplier<AccumulatingBulkRequest> bulkRequestSupplier;
  private BulkRetryStrategy bulkRetryStrategy;
  private BulkApiWrapper bulkApiWrapper;
  private final long bulkSize;
  private final long flushTimeout;
  private final IndexType indexType;
  private final String documentIdField;
  private final String documentId;
  private final String routingField;
  private final String action;
  private final List<Map<String, Object>> actions;
  private final String documentRootKey;
  private String configuredIndexAlias;
  private final ReentrantLock lock;

  private final Timer bulkRequestTimer;
  private final Counter bulkRequestErrorsCounter;
  private final Counter invalidActionErrorsCounter;
  private final Counter dynamicIndexDroppedEvents;
  private final DistributionSummary bulkRequestSizeBytesSummary;
  private OpenSearchClient openSearchClient;
  private ObjectMapper objectMapper;
  private volatile boolean initialized;
  private PluginSetting pluginSetting;
  private final SinkContext sinkContext;
  private final ExpressionEvaluator expressionEvaluator;
  private SinkResponseLatency responseLatency;
  private AtomicDouble responseLatencyAlertRatio;  
  private Backoff backoff;
  private int backoff_attempt;

  private FailedBulkOperationConverter failedBulkOperationConverter;

  private DlqProvider dlqProvider;
  private final ConcurrentHashMap<Long, AccumulatingBulkRequest<BulkOperationWrapper, BulkRequest>> bulkRequestMap;
  private final ConcurrentHashMap<Long, Long> lastFlushTimeMap;

  @DataPrepperPluginConstructor
  public OpenSearchSink(final PluginSetting pluginSetting,
                        final PluginFactory pluginFactory,
                        final SinkContext sinkContext,
                        final ExpressionEvaluator expressionEvaluator,
                        final AwsCredentialsSupplier awsCredentialsSupplier) {
    super(pluginSetting, Integer.MAX_VALUE, INITIALIZE_RETRY_WAIT_TIME_MS);
    this.awsCredentialsSupplier = awsCredentialsSupplier;
    this.sinkContext = sinkContext != null ? sinkContext : new SinkContext(null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    this.responseLatencyAlertRatio = responseLatencyAlertRatio;
    this.expressionEvaluator = expressionEvaluator;
    bulkRequestTimer = pluginMetrics.timer(BULKREQUEST_LATENCY);
    bulkRequestErrorsCounter = pluginMetrics.counter(BULKREQUEST_ERRORS);
    invalidActionErrorsCounter = pluginMetrics.counter(INVALID_ACTION_ERRORS);
    dynamicIndexDroppedEvents = pluginMetrics.counter(DYNAMIC_INDEX_DROPPED_EVENTS);
    bulkRequestSizeBytesSummary = pluginMetrics.summary(BULKREQUEST_SIZE_BYTES);

    final int latencyThresholdMs = (int) pluginSetting.getAttributeFromSettings(LATENCY_THRESHOLD_MS);
    final int highLatencyResponsesThreshold = (int) pluginSetting.getAttributeFromSettings(HIGH_LATENCY_RESPONSES_THRESHOLD);
    this.responseLatency = new SinkResponseLatency((value) -> 
        {
            responseLatencyAlertRatio.set((Double)value);
        }, Duration.ofMillis(latencyThresholdMs), highLatencyResponsesThreshold);
    this.openSearchSinkConfig = OpenSearchSinkConfiguration.readESConfig(pluginSetting, expressionEvaluator);
    this.bulkSize = ByteSizeUnit.MB.toBytes(openSearchSinkConfig.getIndexConfiguration().getBulkSize());
    this.flushTimeout = openSearchSinkConfig.getIndexConfiguration().getFlushTimeout();
    this.indexType = openSearchSinkConfig.getIndexConfiguration().getIndexType();
    this.documentIdField = openSearchSinkConfig.getIndexConfiguration().getDocumentIdField();
    this.documentId = openSearchSinkConfig.getIndexConfiguration().getDocumentId();
    this.routingField = openSearchSinkConfig.getIndexConfiguration().getRoutingField();
    this.action = openSearchSinkConfig.getIndexConfiguration().getAction();
    this.actions = openSearchSinkConfig.getIndexConfiguration().getActions();
    this.documentRootKey = openSearchSinkConfig.getIndexConfiguration().getDocumentRootKey();
    this.indexManagerFactory = new IndexManagerFactory(new ClusterSettingsParser());
    this.failedBulkOperationConverter = new FailedBulkOperationConverter(pluginSetting.getPipelineName(), pluginSetting.getName(),
        pluginSetting.getName());
    this.initialized = false;
    this.lock = new ReentrantLock(true);
    this.pluginSetting = pluginSetting;
    this.bulkRequestMap = new ConcurrentHashMap<>();
    this.lastFlushTimeMap = new ConcurrentHashMap<>();

    final Optional<PluginModel> dlqConfig = openSearchSinkConfig.getRetryConfiguration().getDlq();
    if (dlqConfig.isPresent()) {
      final PluginSetting dlqPluginSetting = new PluginSetting(dlqConfig.get().getPluginName(), dlqConfig.get().getPluginSettings());
      dlqPluginSetting.setPipelineName(pluginSetting.getPipelineName());
      dlqProvider = pluginFactory.loadPlugin(DlqProvider.class, dlqPluginSetting);
    }
  }
  public void updateResponseLatency(long latency) {
    responseLatency.update(latency);
  }

  @Override
  public void doInitialize() {
    try {
        doInitializeInternal();
    } catch (IOException e) {
        LOG.warn("Failed to initialize OpenSearch sink, retrying: {} ", e.getMessage());
        closeFiles();
    } catch (InvalidPluginConfigurationException e) {
        LOG.error("Failed to initialize OpenSearch sink due to a configuration error.", e);
        this.shutdown();
        throw new RuntimeException(e.getMessage(), e);
    } catch (IllegalArgumentException e) {
        LOG.error("Failed to initialize OpenSearch sink due to a configuration error.", e);
        this.shutdown();
        throw e;
    } catch (Exception e) {
        LOG.warn("Failed to initialize OpenSearch sink with a retryable exception. ", e);
        closeFiles();
    }
  }

  private void doInitializeInternal() throws IOException {
    LOG.info("Initializing OpenSearch sink");
    restHighLevelClient = openSearchSinkConfig.getConnectionConfiguration().createClient(awsCredentialsSupplier);
    openSearchClient = openSearchSinkConfig.getConnectionConfiguration().createOpenSearchClient(restHighLevelClient, awsCredentialsSupplier);
    configuredIndexAlias = openSearchSinkConfig.getIndexConfiguration().getIndexAlias();
    final IndexTemplateAPIWrapper indexTemplateAPIWrapper = IndexTemplateAPIWrapperFactory.getWrapper(
            openSearchSinkConfig.getIndexConfiguration(), openSearchClient);
    final TemplateStrategy templateStrategy = openSearchSinkConfig.getIndexConfiguration().getTemplateType()
            .createTemplateStrategy(indexTemplateAPIWrapper);
    indexManager = indexManagerFactory.getIndexManager(indexType, openSearchClient, restHighLevelClient,
            openSearchSinkConfig, templateStrategy, configuredIndexAlias);
    final String dlqFile = openSearchSinkConfig.getRetryConfiguration().getDlqFile();
    if (dlqFile != null) {
      dlqFileWriter = Files.newBufferedWriter(Paths.get(dlqFile), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    } else if (dlqProvider != null) {
      Optional<DlqWriter> potentialDlq = dlqProvider.getDlqWriter(new StringJoiner(MetricNames.DELIMITER)
          .add(pluginSetting.getPipelineName())
          .add(pluginSetting.getName()).toString());
      dlqWriter = potentialDlq.isPresent() ? potentialDlq.get() : null;
    }
    indexManager.setupIndex();

    final boolean isEstimateBulkSizeUsingCompression = openSearchSinkConfig.getIndexConfiguration().isEstimateBulkSizeUsingCompression();
    final boolean isRequestCompressionEnabled = openSearchSinkConfig.getConnectionConfiguration().isRequestCompressionEnabled();
    if (isEstimateBulkSizeUsingCompression && isRequestCompressionEnabled) {
      final int maxLocalCompressionsForEstimation = openSearchSinkConfig.getIndexConfiguration().getMaxLocalCompressionsForEstimation();
      bulkRequestSupplier = () -> new JavaClientAccumulatingCompressedBulkRequest(new BulkRequest.Builder(), bulkSize, maxLocalCompressionsForEstimation);
    } else if (isEstimateBulkSizeUsingCompression) {
      LOG.warn("Estimate bulk request size using compression was enabled but request compression is disabled. " +
              "Estimating bulk request size without compression.");
      bulkRequestSupplier = () -> new JavaClientAccumulatingUncompressedBulkRequest(new BulkRequest.Builder());
    } else {
      bulkRequestSupplier = () -> new JavaClientAccumulatingUncompressedBulkRequest(new BulkRequest.Builder());
    }

    final int maxRetries = openSearchSinkConfig.getRetryConfiguration().getMaxRetries();
    final OpenSearchClient filteringOpenSearchClient = openSearchClient.withTransportOptions(
            TransportOptions.builder()
                    .setParameter("filter_path", "errors,took,items.*.error,items.*.status,items.*._index,items.*._id")
                    .build());
    bulkApiWrapper = BulkApiWrapperFactory.getWrapper(openSearchSinkConfig.getIndexConfiguration(), filteringOpenSearchClient);
    bulkRetryStrategy = new BulkRetryStrategy(this,
            bulkRequest -> bulkApiWrapper.bulk(bulkRequest.getRequest()),
            this::logFailureForBulkRequests,
            pluginMetrics,
            maxRetries,
            bulkRequestSupplier,
            pluginSetting);

    // Attempt to update the serverless network policy if required argument are given.
    maybeUpdateServerlessNetworkPolicy();

    objectMapper = new ObjectMapper();
    this.initialized = true;
    LOG.info("Initialized OpenSearch sink");
  }

  double getInvalidActionErrorsCount() {
    return invalidActionErrorsCounter.count();
  }

  @Override
  public boolean isReady() {
    return initialized;
  }

  private BulkOperation getBulkOperationForAction(final String action, final SerializedJson document, final String indexName, final JsonNode jsonNode) {
    BulkOperation bulkOperation;
    final Optional<String> docId = document.getDocumentId();
    final Optional<String> routing = document.getRoutingField();

    if (StringUtils.equals(action, OpenSearchBulkActions.CREATE.toString())) {
       final CreateOperation.Builder<Object> createOperationBuilder =
         new CreateOperation.Builder<>()
             .index(indexName)
             .document(document);
       docId.ifPresent(createOperationBuilder::id);
       routing.ifPresent(createOperationBuilder::routing);
       bulkOperation = new BulkOperation.Builder()
                           .create(createOperationBuilder.build())
                           .build();
       return bulkOperation;
    }
    if (StringUtils.equals(action, OpenSearchBulkActions.UPDATE.toString()) ||
        StringUtils.equals(action, OpenSearchBulkActions.UPSERT.toString())) {
          final UpdateOperation.Builder<Object> updateOperationBuilder = (action.toLowerCase() == OpenSearchBulkActions.UPSERT.toString()) ?
              new UpdateOperation.Builder<>()
                  .index(indexName)
                  .document(jsonNode)
                  .upsert(jsonNode) :
              new UpdateOperation.Builder<>()
                  .index(indexName)
                  .document(jsonNode);
          docId.ifPresent(updateOperationBuilder::id);
          routing.ifPresent(updateOperationBuilder::routing);
          bulkOperation = new BulkOperation.Builder()
                              .update(updateOperationBuilder.build())
                              .build();
          return bulkOperation;
    }
    if (StringUtils.equals(action, OpenSearchBulkActions.DELETE.toString())) {
      final DeleteOperation.Builder deleteOperationBuilder =
        new DeleteOperation.Builder().index(indexName);
      docId.ifPresent(deleteOperationBuilder::id);
      routing.ifPresent(deleteOperationBuilder::routing);
      bulkOperation = new BulkOperation.Builder()
                          .delete(deleteOperationBuilder.build())
                          .build();
      return bulkOperation;
    }
    // Default to "index"
    final IndexOperation.Builder<Object> indexOperationBuilder =
      new IndexOperation.Builder<>().index(indexName).document(document);
    docId.ifPresent(indexOperationBuilder::id);
    routing.ifPresent(indexOperationBuilder::routing);
    bulkOperation = new BulkOperation.Builder()
                        .index(indexOperationBuilder.build())
                        .build();
    return bulkOperation;
  }

  @Override
  public void doOutput(final Collection<Record<Event>> records) {
    final long threadId = Thread.currentThread().getId();
    if (!bulkRequestMap.containsKey(threadId)) {
      bulkRequestMap.put(threadId, bulkRequestSupplier.get());
    }
    if (!lastFlushTimeMap.containsKey(threadId)) {
      lastFlushTimeMap.put(threadId, System.currentTimeMillis());
    }

    if (responseLatencyAlertRatio.get() > 1.0) {
        if (backoff == null) {
            int initialDelay = (int) (INITIAL_DELAY_MS * responseLatencyAlertRatio.get());
            if (initialDelay >= MAXIMUM_DELAY_MS) {
                initialDelay = MAXIMUM_DELAY_MS;
            }
            backoff = Backoff.exponential(initialDelay, MAXIMUM_DELAY_MS).withMaxAttempts(Integer.MAX_VALUE);
            backoff_attempt = 0;
        }
        final long delayMillis = backoff.nextDelayMillis(backoff_attempt++);
        try {
            Thread.sleep(delayMillis);
        } catch (Exception e) {}
    } else if (backoff != null) {
        backoff = null;
        backoff_attempt = 0;
    }
        

    AccumulatingBulkRequest<BulkOperationWrapper, BulkRequest> bulkRequest = bulkRequestMap.get(threadId);
    long lastFlushTime = lastFlushTimeMap.get(threadId);

    for (final Record<Event> record : records) {
      final Event event = record.getData();
      final SerializedJson document = getDocument(event);
      String indexName = configuredIndexAlias;
      try {
          indexName = indexManager.getIndexName(event.formatString(indexName, expressionEvaluator));
      } catch (IOException | EventKeyNotFoundException e) {
          LOG.error("There was an exception when constructing the index name. Check the dlq if configured to see details about the affected Event: {}", e.getMessage());
          dynamicIndexDroppedEvents.increment();
          logFailureForDlqObjects(List.of(DlqObject.builder()
                  .withEventHandle(event.getEventHandle())
                  .withFailedData(FailedDlqData.builder().withDocument(event.toJsonString()).withIndex(indexName).withMessage(e.getMessage()).build())
                  .withPluginName(pluginSetting.getName())
                  .withPipelineName(pluginSetting.getPipelineName())
                  .withPluginId(pluginSetting.getName())
                  .build()), e);
          continue;
      }

      String eventAction = action;
      if (actions != null) {
        for (final Map<String, Object> actionEntry: actions) {
            final String condition = (String)actionEntry.get("when");
            eventAction = (String)actionEntry.get("type");
            if (condition != null &&
                expressionEvaluator.evaluateConditional(condition, event)) {
                    break;
            }
        }
      }
      if (eventAction.contains("${")) {
          eventAction = event.formatString(eventAction, expressionEvaluator);
      }
      if (OpenSearchBulkActions.fromOptionValue(eventAction) == null) {
        LOG.error("Unknown action {}, skipping the event", eventAction);
        invalidActionErrorsCounter.increment();
        continue;
      }

      SerializedJson serializedJsonNode = null;
      if (StringUtils.equals(action, OpenSearchBulkActions.UPDATE.toString()) ||
          StringUtils.equals(action, OpenSearchBulkActions.UPSERT.toString()) ||
          StringUtils.equals(action, OpenSearchBulkActions.DELETE.toString())) {
            serializedJsonNode = SerializedJson.fromJsonNode(event.getJsonNode(), document);
      }
      BulkOperation bulkOperation = getBulkOperationForAction(eventAction, document, indexName, event.getJsonNode());

      BulkOperationWrapper bulkOperationWrapper = new BulkOperationWrapper(bulkOperation, event.getEventHandle(), serializedJsonNode);
      final long estimatedBytesBeforeAdd = bulkRequest.estimateSizeInBytesWithDocument(bulkOperationWrapper);
      if (bulkSize >= 0 && estimatedBytesBeforeAdd >= bulkSize && bulkRequest.getOperationsCount() > 0) {
        flushBatch(bulkRequest);
        lastFlushTime = System.currentTimeMillis();
        bulkRequest = bulkRequestSupplier.get();
      }
      bulkRequest.addOperation(bulkOperationWrapper);
    }

    // Flush the remaining requests if flush timeout expired
    if (System.currentTimeMillis() - lastFlushTime > flushTimeout && bulkRequest.getOperationsCount() > 0) {
      flushBatch(bulkRequest);
      lastFlushTime = System.currentTimeMillis();
      bulkRequest = bulkRequestSupplier.get();
    }

    bulkRequestMap.put(threadId, bulkRequest);
    lastFlushTimeMap.put(threadId, lastFlushTime);
  }

  private SerializedJson getDocument(final Event event) {

    String docId = null;

    if (Objects.nonNull(documentIdField)) {
      docId = event.get(documentIdField, String.class);
    } else if (Objects.nonNull(documentId)) {
      try {
        docId = event.formatString(documentId, expressionEvaluator);
      } catch (final ExpressionEvaluationException | EventKeyNotFoundException e) {
        LOG.error("Unable to construct document_id with format {}, the document_id will be generated by OpenSearch", documentId, e);
      }
    }

    String routing = (routingField != null) ? event.get(routingField, String.class) : null;

    final String document = DocumentBuilder.build(event, documentRootKey, sinkContext.getTagsTargetKey(), sinkContext.getIncludeKeys(), sinkContext.getExcludeKeys());

    return SerializedJson.fromStringAndOptionals(document, docId, routing);
  }

  private void flushBatch(AccumulatingBulkRequest accumulatingBulkRequest) {
    bulkRequestTimer.record(() -> {
      try {
        LOG.debug("Sending data to OpenSearch");
        bulkRetryStrategy.execute(accumulatingBulkRequest);
        bulkRequestSizeBytesSummary.record(accumulatingBulkRequest.getEstimatedSizeInBytes());
      } catch (final InterruptedException e) {
        LOG.error("Unexpected Interrupt:", e);
        bulkRequestErrorsCounter.increment();
        Thread.currentThread().interrupt();
      }
    });
  }

  private void logFailureForBulkRequests(final List<FailedBulkOperation> failedBulkOperations, final Throwable failure) {

    final List<DlqObject> dlqObjects = failedBulkOperations.stream()
        .map(failedBulkOperationConverter::convertToDlqObject)
        .collect(Collectors.toList());

    logFailureForDlqObjects(dlqObjects, failure);
  }

  private void logFailureForDlqObjects(final List<DlqObject> dlqObjects, final Throwable failure) {
    if (dlqFileWriter != null) {
      dlqObjects.forEach(dlqObject -> {
        final FailedDlqData failedDlqData = (FailedDlqData) dlqObject.getFailedData();
        final String message = failure == null ? failedDlqData.getMessage() : failure.getMessage();
        try {
          dlqFileWriter.write(String.format("{\"Document\": [%s], \"failure\": %s}\n",
                  BulkOperationWriter.dlqObjectToString(dlqObject), message));
          dlqObject.releaseEventHandle(true);
        } catch (final IOException e) {
        LOG.error("Failed to write a document to the DLQ", e);
          dlqObject.releaseEventHandle(false);
        }
      });
    } else if (dlqWriter != null) {
      try {
        dlqWriter.write(dlqObjects, pluginSetting.getPipelineName(), pluginSetting.getName());
        dlqObjects.forEach((dlqObject) -> {
          dlqObject.releaseEventHandle(true);
        });
      } catch (final IOException e) {
        dlqObjects.forEach(dlqObject -> {
          LOG.error("Failed to write a document to the DLQ", e);
          dlqObject.releaseEventHandle(false);
        });
      }
    } else {
      dlqObjects.forEach(dlqObject -> {

        final FailedDlqData failedDlqData = (FailedDlqData) dlqObject.getFailedData();

        final String message = failure == null ? failedDlqData.getMessage() : failure.getMessage();
        LOG.warn("Document failed to write to OpenSearch with error code {}. Configure a DLQ to save failed documents. Error: {}", failedDlqData.getStatus(), message);
        dlqObject.releaseEventHandle(false);
      });
    }
  }

  private void closeFiles() {
    // Close the client. This closes the low-level client which will close it for both high-level clients.
    if (restHighLevelClient != null) {
      try {
        restHighLevelClient.close();
      } catch (final IOException e) {
        throw new RuntimeException(e.getMessage(), e);
      }
    }
    if (dlqWriter != null) {
      try {
        dlqWriter.close();
      } catch (final IOException e) {
        LOG.error(e.getMessage(), e);
      }
    }
    if (dlqFileWriter != null) {
      try {
        dlqFileWriter.close();
      } catch (final IOException e) {
        LOG.error(e.getMessage(), e);
      }
    }
  }

  @Override
  public void shutdown() {
    super.shutdown();
    closeFiles();
  }

  private void maybeUpdateServerlessNetworkPolicy() {
    final ConnectionConfiguration connectionConfiguration = openSearchSinkConfig.getConnectionConfiguration();
    LOG.info(connectionConfiguration.toString());
    if (connectionConfiguration.isServerless() &&
        !StringUtils.isBlank(connectionConfiguration.getServerlessNetworkPolicyName()) &&
        !StringUtils.isBlank(connectionConfiguration.getServerlessCollectionName()) &&
        !StringUtils.isBlank(connectionConfiguration.getServerlessVpceId())
    ) {
      final OpenSearchServerlessClient openSearchServerlessClient = connectionConfiguration.createOpenSearchServerlessClient(awsCredentialsSupplier);
      final ServerlessNetworkPolicyUpdater networkPolicyUpdater = new ServerlessNetworkPolicyUpdater(openSearchServerlessClient);
      networkPolicyUpdater.updateNetworkPolicy(
          connectionConfiguration.getServerlessNetworkPolicyName(),
          connectionConfiguration.getServerlessCollectionName(),
          connectionConfiguration.getServerlessVpceId());
    }
  }
}
