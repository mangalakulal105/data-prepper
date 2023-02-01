/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.sink.opensearch.index;

import com.google.common.collect.ImmutableSet;
import org.opensearch.OpenSearchException;
import org.opensearch.action.admin.cluster.settings.ClusterGetSettingsRequest;
import org.opensearch.action.admin.cluster.settings.ClusterGetSettingsResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.indices.CreateIndexRequest;
import org.opensearch.client.indices.GetIndexTemplatesRequest;
import org.opensearch.client.indices.GetIndexTemplatesResponse;
import org.opensearch.client.indices.IndexTemplateMetadata;
import org.opensearch.client.indices.IndexTemplatesExistRequest;
import org.opensearch.client.indices.PutIndexTemplateRequest;
import org.opensearch.dataprepper.plugins.sink.opensearch.OpenSearchSinkConfiguration;
import org.opensearch.dataprepper.model.plugin.InvalidPluginConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkNotNull;

public abstract class AbstractIndexManager implements IndexManager {

    public static final String INDEX_ALIAS_USED_AS_INDEX_ERROR
            = "Invalid alias name [%s], an index exists with the same name as the alias";
    public static final String INVALID_INDEX_ALIAS_ERROR
            = "type=invalid_index_name_exception";
    private static final String TIME_PATTERN_STARTING_SYMBOLS = "%{";
    protected RestHighLevelClient restHighLevelClient;
    protected OpenSearchSinkConfiguration openSearchSinkConfiguration;
    protected IsmPolicyManagementStrategy ismPolicyManagementStrategy;
    protected String indexPrefix;

    private static final Logger LOG = LoggerFactory.getLogger(AbstractIndexManager.class);

    //For matching a string that begins with a "%{" and ends with a "}".
    //For a string like "data-prepper-%{yyyy-MM-dd}", "%{yyyy-MM-dd}" is matched.
    private static final String TIME_PATTERN_REGULAR_EXPRESSION  = "%\\{.*?\\}";

    //For matching a string enclosed by "%{" and "}".
    //For a string like "data-prepper-%{yyyy-MM}", "yyyy-MM" is matched.
    private static final String TIME_PATTERN_INTERNAL_EXTRACTOR_REGULAR_EXPRESSION  = "%\\{(.*?)\\}";

    private Optional<DateTimeFormatter> indexTimeSuffixFormatter;
    private static final ZoneId UTC_ZONE_ID = ZoneId.of(TimeZone.getTimeZone("UTC").getID());

    protected AbstractIndexManager(final RestHighLevelClient restHighLevelClient, final OpenSearchSinkConfiguration openSearchSinkConfiguration, String indexAlias){
        checkNotNull(restHighLevelClient);
        checkNotNull(openSearchSinkConfiguration);
        this.restHighLevelClient = restHighLevelClient;
        this.openSearchSinkConfiguration = openSearchSinkConfiguration;
        if (indexAlias == null) {
            indexAlias = openSearchSinkConfiguration.getIndexConfiguration().getIndexAlias();
        }

        initializeIndexPrefixAndSuffix(indexAlias);
    }

    public static DateTimeFormatter getDatePatternFormatter(final String indexAlias) {
        final Pattern pattern = Pattern.compile(TIME_PATTERN_INTERNAL_EXTRACTOR_REGULAR_EXPRESSION);
        final Matcher timePatternMatcher = pattern.matcher(indexAlias);
        if (timePatternMatcher.find()) {
            final String timePattern = timePatternMatcher.group(1);
            if (timePatternMatcher.find()) { // check if there is a one more match.
                throw new IllegalArgumentException("An index only allows one date-time pattern.");
            }
            if(timePattern.contains(TIME_PATTERN_STARTING_SYMBOLS)){ //check if it is a nested pattern such as "data-prepper-%{%{yyyy.MM.dd}}"
                throw new IllegalArgumentException("An index doesn't allow nested date-time patterns.");
            }
            validateTimePatternIsAtTheEnd(indexAlias, timePattern);
            validateNoSpecialCharsInTimePattern(timePattern);
            validateTimePatternGranularity(timePattern);
            return DateTimeFormatter.ofPattern(timePattern);
        }
        return null;
    }

    public static String getIndexAliasWithDate(final String indexAlias) {
        DateTimeFormatter dateFormatter = getDatePatternFormatter(indexAlias);
        String suffix = (dateFormatter != null) ? dateFormatter.format(getCurrentUtcTime()) : "";
        return indexAlias.replaceAll(TIME_PATTERN_REGULAR_EXPRESSION, "") + suffix;
    }

    private void initializeIndexPrefixAndSuffix(final String indexAlias){
        DateTimeFormatter dateFormatter = getDatePatternFormatter(indexAlias);
        if (dateFormatter != null) {
            indexTimeSuffixFormatter = Optional.of(dateFormatter);
        } else {
            indexTimeSuffixFormatter = Optional.empty();
        }

        indexPrefix = indexAlias.replaceAll(TIME_PATTERN_REGULAR_EXPRESSION, "");
    }

    /*
      Data Prepper only allows time pattern as a suffix.
     */
    private static void validateTimePatternIsAtTheEnd(final String indexAlias, final String timePattern) {
        if (!indexAlias.endsWith(timePattern + "}")) {
            throw new IllegalArgumentException("Time pattern can only be a suffix of an index.");
        }
    }

    /*
     * Special characters can cause failures in creating indexes.
     * */
    private static final Set<Character> INVALID_CHARS = ImmutableSet.of('#', '\\', '/', '*', '?', '"', '<', '>', '|', ',', ':');

    private static void validateNoSpecialCharsInTimePattern(final String timePattern) {
        final boolean containsInvalidCharacter = timePattern.chars()
                .mapToObj(c -> (char) c)
                .anyMatch(character -> INVALID_CHARS.contains(character));
        if (containsInvalidCharacter) {
            throw new IllegalArgumentException("Index time pattern contains one or multiple special characters: " + INVALID_CHARS);
        }
    }

    /*
     * Data Prepper doesn't support creating indexes with time patterns that are too granular, e.g. minute, second, millisecond, nanosecond.
     * https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html
     * */
    private static final Set<Character> UNSUPPORTED_TIME_GRANULARITY_CHARS = ImmutableSet.of('m', 's', 'S', 'A', 'n', 'N');

    private static void validateTimePatternGranularity(final String timePattern) {
        final boolean containsUnsupportedTimeSymbol = timePattern.chars()
                .mapToObj(c -> (char) c)
                .anyMatch(character -> UNSUPPORTED_TIME_GRANULARITY_CHARS.contains(character));
        if (containsUnsupportedTimeSymbol) {
            throw new IllegalArgumentException("Index time pattern contains time patterns that are less than one hour: "
                    + UNSUPPORTED_TIME_GRANULARITY_CHARS);
        }
    }

    public String getIndexName(final String dynamicIndexAlias) throws IOException {
        if (indexTimeSuffixFormatter.isPresent()) {
            final String formattedTimeString = indexTimeSuffixFormatter.get()
                    .format(getCurrentUtcTime());
            return indexPrefix + formattedTimeString;
        } else {
            return indexPrefix;
        }
    }

    public static ZonedDateTime getCurrentUtcTime() {
        return LocalDateTime.now().atZone(ZoneId.systemDefault()).withZoneSameInstant(UTC_ZONE_ID);
    }

    final boolean checkISMEnabled() throws IOException {
        final ClusterGetSettingsRequest request = new ClusterGetSettingsRequest();
        request.includeDefaults(true);
        final ClusterGetSettingsResponse response = restHighLevelClient.cluster().getSettings(request, RequestOptions.DEFAULT);
        final String enabled = response.getSetting(IndexConstants.ISM_ENABLED_SETTING);
        return enabled != null && enabled.equals("true");
    }

    /**
     * Setups anything required for the index.
     *
     * @throws IOException
     */
    @Override
    public void setupIndex() throws IOException {
        checkAndCreateIndexTemplate();
        checkAndCreateIndex();
    }

    private void checkAndCreateIndexTemplate() throws IOException {
        final boolean isISMEnabled = checkISMEnabled();
        final Optional<String> policyIdOptional = isISMEnabled ?
                ismPolicyManagementStrategy.checkAndCreatePolicy() :
                Optional.empty();
        if (!openSearchSinkConfiguration.getIndexConfiguration().getIndexTemplate().isEmpty()) {
            checkAndCreateIndexTemplate(isISMEnabled, policyIdOptional.orElse(null));
        }
    }

    final void checkAndCreateIndexTemplate(final boolean isISMEnabled, final String ismPolicyId) throws IOException {
        //If index prefix has a ending dash, then remove it to avoid two consecutive dashes.
        final String indexPrefixWithoutTrailingDash = indexPrefix.replaceAll("-$", "");
        final String indexTemplateName = indexPrefixWithoutTrailingDash  + "-index-template";

        // Check existing index template version - only overwrite if version is less than or does not exist
        if (!shouldCreateIndexTemplate(indexTemplateName)) {
            return;
        }

        final PutIndexTemplateRequest putIndexTemplateRequest = new PutIndexTemplateRequest(indexTemplateName);

        putIndexTemplateRequest.patterns(ismPolicyManagementStrategy.getIndexPatterns(indexPrefixWithoutTrailingDash));

        if (isISMEnabled) {
            attachPolicy(openSearchSinkConfiguration.getIndexConfiguration(), ismPolicyId, indexPrefixWithoutTrailingDash);
        }

        putIndexTemplateRequest.source(openSearchSinkConfiguration.getIndexConfiguration().getIndexTemplate());
        restHighLevelClient.indices().putTemplate(putIndexTemplateRequest, RequestOptions.DEFAULT);
    }

    final Optional<String> checkAndCreatePolicy() throws IOException {
        return ismPolicyManagementStrategy.checkAndCreatePolicy();
    }

    public void checkAndCreateIndex() throws IOException {
        // Check if index name exists
        final String indexAlias = getIndexName(null);
        final boolean indexExists = ismPolicyManagementStrategy.checkIfIndexExistsOnServer(indexAlias);

        if (!indexExists) {
            final CreateIndexRequest createIndexRequest = ismPolicyManagementStrategy.getCreateIndexRequest(indexAlias);
            try {
                restHighLevelClient.indices().create(createIndexRequest, RequestOptions.DEFAULT);
            } catch (OpenSearchException e) {
                if (e.getMessage().contains("resource_already_exists_exception")) {
                    // Do nothing - likely caused by a race condition where the resource was created
                    // by another host before this host's restClient made its request
                } else if (e.getMessage().contains(INVALID_INDEX_ALIAS_ERROR)) {
                    throw new InvalidPluginConfigurationException(String.format("Invalid characters in the index name %s", indexAlias));
                } else if (e.getMessage().contains(String.format(INDEX_ALIAS_USED_AS_INDEX_ERROR, indexAlias))) {
                    throw new InvalidPluginConfigurationException(
                            String.format("An index exists with the same name as the reserved index alias name [%s], please delete or migrate the existing index",
                                    indexAlias));
                } else {
                    throw new IOException(e);
                }
            }
        }
    }

    private Optional<IndexTemplateMetadata> getIndexTemplateMetadata(final String indexTemplateName) throws IOException {
        final IndexTemplatesExistRequest existsRequest = new IndexTemplatesExistRequest(indexTemplateName);
        final boolean exists = restHighLevelClient.indices().existsTemplate(existsRequest, RequestOptions.DEFAULT);
        if (!exists) {
            return Optional.empty();
        }

        final GetIndexTemplatesRequest request = new GetIndexTemplatesRequest(indexTemplateName);
        final GetIndexTemplatesResponse response = restHighLevelClient.indices().getIndexTemplate(request, RequestOptions.DEFAULT);

        if (response.getIndexTemplates().size() == 1) {
            return Optional.of(response.getIndexTemplates().get(0));
        } else {
            throw new RuntimeException(String.format("Found multiple index templates (%s) result when querying for %s",
                    response.getIndexTemplates().size(),
                    indexTemplateName));
        }
    }

    private boolean shouldCreateIndexTemplate(final String indexTemplateName) throws IOException {
        final Optional<IndexTemplateMetadata> indexTemplateMetadataOptional = getIndexTemplateMetadata(indexTemplateName);
        if (indexTemplateMetadataOptional.isPresent()) {
            final Integer existingTemplateVersion = indexTemplateMetadataOptional.get().version();
            LOG.info("Found version {} for existing index template {}", existingTemplateVersion, indexTemplateName);

            final int newTemplateVersion = (int) openSearchSinkConfiguration.getIndexConfiguration().getIndexTemplate().getOrDefault("version", 0);

            if (existingTemplateVersion != null && existingTemplateVersion >= newTemplateVersion) {
                LOG.info("Index template {} should not be updated, current version {} >= existing version {}",
                        indexTemplateName,
                        existingTemplateVersion,
                        newTemplateVersion);
                return false;

            } else {
                LOG.info("Index template {} should be updated from version {} to version {}",
                        indexTemplateName,
                        existingTemplateVersion,
                        newTemplateVersion);
                return true;
            }
        } else {
            LOG.info("Index template {} does not exist and should be created", indexTemplateName);
            return true;
        }
    }

    //To suppress warnings on casting index template settings to Map<String, Object>
    @SuppressWarnings("unchecked")
    private void attachPolicy(
            final IndexConfiguration configuration, final String ismPolicyId, final String rolloverAlias) {
        configuration.getIndexTemplate().putIfAbsent("settings", new HashMap<>());
        if (ismPolicyId != null) {
            ((Map<String, Object>) configuration.getIndexTemplate().get("settings"))
                    .put(IndexConstants.ISM_POLICY_ID_SETTING, ismPolicyId);
        }
        ((Map<String, Object>) configuration.getIndexTemplate().get("settings"))
                .put(IndexConstants.ISM_ROLLOVER_ALIAS_SETTING, rolloverAlias);
    }

}
