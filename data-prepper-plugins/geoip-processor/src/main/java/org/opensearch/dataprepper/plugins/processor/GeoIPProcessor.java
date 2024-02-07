/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.processor;

import io.micrometer.core.instrument.Counter;
import org.opensearch.dataprepper.expression.ExpressionEvaluator;
import org.opensearch.dataprepper.metrics.PluginMetrics;
import org.opensearch.dataprepper.model.annotations.DataPrepperPlugin;
import org.opensearch.dataprepper.model.annotations.DataPrepperPluginConstructor;
import org.opensearch.dataprepper.model.event.Event;
import org.opensearch.dataprepper.model.processor.AbstractProcessor;
import org.opensearch.dataprepper.model.processor.Processor;
import org.opensearch.dataprepper.model.record.Record;
import org.opensearch.dataprepper.plugins.processor.configuration.EntryConfig;
import org.opensearch.dataprepper.plugins.processor.databaseenrich.GeoIPDatabase;
import org.opensearch.dataprepper.plugins.processor.databaseenrich.GeoIPDatabaseReader;
import org.opensearch.dataprepper.plugins.processor.databaseenrich.GeoIPField;
import org.opensearch.dataprepper.plugins.processor.exception.EnrichFailedException;
import org.opensearch.dataprepper.plugins.processor.extension.GeoIPProcessorService;
import org.opensearch.dataprepper.plugins.processor.extension.GeoIpConfigSupplier;
import org.opensearch.dataprepper.plugins.processor.utils.IPValidationCheck;
import org.opensearch.dataprepper.logging.DataPrepperMarkers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Implementation class of geoIP-processor plugin. It is responsible for enrichment of
 * attributes for the public IPs. Supports both IPV4 and IPV6
 */
@DataPrepperPlugin(name = "geoip", pluginType = Processor.class, pluginConfigurationType = GeoIPProcessorConfig.class)
public class GeoIPProcessor extends AbstractProcessor<Record<Event>, Record<Event>> {

  private static final Logger LOG = LoggerFactory.getLogger(GeoIPProcessor.class);
  static final String GEO_IP_EVENTS_PROCESSED = "eventsProcessed";
  static final String GEO_IP_EVENTS_FAILED_LOOKUP = "eventsFailedLookup";
  static final String GEO_IP_EVENTS_FAILED_ENGINE_EXCEPTION = "eventsFailedEngineException";
  static final List<String> DATABASE_EXPIRED_TAGS = List.of("database_expired");
  private final Counter geoIpEventsProcessed;
  private final Counter geoIpEventsFailedLookup;
  private final Counter geoIpEventsFailedEngineException;
  private final GeoIPProcessorConfig geoIPProcessorConfig;
  private final List<String> tagsOnFailure;
  private final GeoIPProcessorService geoIPProcessorService;
  private final ExpressionEvaluator expressionEvaluator;
  private final Map<EntryConfig, Set<GeoIPDatabase>> entryDatabaseMap;

  /**
   * GeoIPProcessor constructor for initialization of required attributes
   * @param pluginMetrics pluginMetrics
   * @param geoIPProcessorConfig geoIPProcessorConfig
   * @param geoIpConfigSupplier geoIpConfigSupplier
   */
  @DataPrepperPluginConstructor
  public GeoIPProcessor(final PluginMetrics pluginMetrics,
                        final GeoIPProcessorConfig geoIPProcessorConfig,
                        final GeoIpConfigSupplier geoIpConfigSupplier,
                        final ExpressionEvaluator expressionEvaluator) {
    super(pluginMetrics);
    this.geoIPProcessorConfig = geoIPProcessorConfig;
    this.geoIPProcessorService = geoIpConfigSupplier.getGeoIPProcessorService();
    this.tagsOnFailure = geoIPProcessorConfig.getTagsOnFailure();
    this.expressionEvaluator = expressionEvaluator;
    this.geoIpEventsProcessed = pluginMetrics.counter(GEO_IP_EVENTS_PROCESSED);
    this.geoIpEventsFailedLookup = pluginMetrics.counter(GEO_IP_EVENTS_FAILED_LOOKUP);
    //TODO: Use the exception metric for exceptions from service
    this.geoIpEventsFailedEngineException = pluginMetrics.counter(GEO_IP_EVENTS_FAILED_ENGINE_EXCEPTION);

    this.entryDatabaseMap = getDatabasesRequired();
  }

  private Map<EntryConfig, Set<GeoIPDatabase>> getDatabasesRequired() {
    final Map<EntryConfig, Set<GeoIPDatabase>> entryConfigSetMap = new HashMap<>();

    for (EntryConfig entryConfig: geoIPProcessorConfig.getEntries()) {
      final Set<GeoIPDatabase> databaseTypes = new HashSet<>();
      final List<String> fields = entryConfig.getFields();
      if (fields.isEmpty()) {
        databaseTypes.addAll(Set.of(GeoIPDatabase.values()));
      } else {
        for (final String field : fields) {
          final Optional<Set<GeoIPDatabase>> geoIPDatabases = GeoIPField.getGeoLite2Databases(field);
          geoIPDatabases.ifPresent(databaseTypes::addAll);
        }
      }
      entryConfigSetMap.put(entryConfig, databaseTypes);
    }
    return entryConfigSetMap;
  }

  /**
   * Get the enriched data from the maxmind database
   * @param records Input records
   * @return collection of record events
   */
  @Override
  public Collection<Record<Event>> doExecute(final Collection<Record<Event>> records) {
    Map<String, Object> geoData;
    final GeoIPDatabaseReader geoIPDatabaseReader = geoIPProcessorService.getGeoIPDatabaseReader();
    geoIPDatabaseReader.retain();
    final boolean databasesExpired = geoIPDatabaseReader.areDatabasesExpired();

    for (final Record<Event> eventRecord : records) {
      final Event event = eventRecord.getData();
      if (databasesExpired) {
        event.getMetadata().addTags(DATABASE_EXPIRED_TAGS);
      }

      final String whenCondition = geoIPProcessorConfig.getWhenCondition();

      if (whenCondition != null && !expressionEvaluator.evaluateConditional(whenCondition, event)) {
        continue;
      }

      boolean isEventFailedLookup = false;
      geoIpEventsProcessed.increment();

      for (final EntryConfig entry : geoIPProcessorConfig.getEntries()) {
        final String source = entry.getSource();
        final List<String> attributes = entry.getFields();
        final String ipAddress = event.get(source, String.class);

        //Lookup from DB
        if (ipAddress != null && !ipAddress.isEmpty()) {
          try {
            if (IPValidationCheck.isPublicIpAddress(ipAddress)) {
              geoData = geoIPDatabaseReader.getGeoData(InetAddress.getByName(ipAddress), attributes, entryDatabaseMap.get(entry));
              if (geoData.isEmpty()) {
                isEventFailedLookup = true;
              }else {
                eventRecord.getData().put(entry.getTarget(), geoData);
              }
            } else {
              isEventFailedLookup = true;
            }
          } catch (final UnknownHostException | EnrichFailedException ex) {
            isEventFailedLookup = true;
            LOG.error(DataPrepperMarkers.EVENT, "Failed to get Geo data for event: [{}] for the IP address [{}]. Caused by:{}"
                    , event, ipAddress, ex.getMessage());
          }
        } else {
          //No Enrichment.
          isEventFailedLookup = true;
        }
      }

      if (isEventFailedLookup) {
        geoIpEventsFailedLookup.increment();
        event.getMetadata().addTags(tagsOnFailure);
      }
    }
    geoIPDatabaseReader.close();
    return records;
  }

  @Override
  public void prepareForShutdown() {
    geoIPProcessorService.shutdown();
  }

  @Override
  public boolean isReadyForShutdown() {
    return true;
  }

  @Override
  public void shutdown() {

  }
}