/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.sink.opensearch.index;

import org.opensearch.client.RestHighLevelClient;
import org.opensearch.dataprepper.plugins.sink.opensearch.OpenSearchSinkConfiguration;

import java.util.Optional;

public class IndexManagerFactory {

    public final IndexManager getDynamicIndexManager(final IndexType indexType,
                                        final RestHighLevelClient restHighLevelClient,
                                        final OpenSearchSinkConfiguration openSearchSinkConfiguration,
                                        final String indexAlias) {
        switch (indexType) {
            case TRACE_ANALYTICS_RAW:
                return new TraceAnalyticsRawIndexManager(restHighLevelClient, openSearchSinkConfiguration, indexAlias);
            case TRACE_ANALYTICS_SERVICE_MAP:
                return new TraceAnalyticsServiceMapIndexManager(restHighLevelClient, openSearchSinkConfiguration, indexAlias);
            case MANAGEMENT_DISABLED:
                return new ManagementDisabledIndexManager(restHighLevelClient, openSearchSinkConfiguration, indexAlias);
            default:
                return new DefaultIndexManager(restHighLevelClient, openSearchSinkConfiguration, indexAlias);
        }
    }

    public final IndexManager getIndexManager(final IndexType indexType,
                                        final RestHighLevelClient restHighLevelClient,
                                        final OpenSearchSinkConfiguration openSearchSinkConfiguration) {
        return getDynamicIndexManager(indexType, restHighLevelClient, openSearchSinkConfiguration, null);
    }

    private static class DefaultIndexManager extends IndexManager {

        private static final String POLICY_NAME_SUFFIX = "-policy";

        public DefaultIndexManager(final RestHighLevelClient restHighLevelClient,
                                   final OpenSearchSinkConfiguration openSearchSinkConfiguration,
                                   final String indexAlias) {
            super(restHighLevelClient, openSearchSinkConfiguration, indexAlias);
            final Optional<String> ismPolicyFile = openSearchSinkConfiguration.getIndexConfiguration().getIsmPolicyFile();
            if (ismPolicyFile.isPresent()) {
                final String indexPolicyName = getIndexPolicyName();
                this.ismPolicyManagementStrategy = new IsmPolicyManagement(restHighLevelClient, indexPolicyName, ismPolicyFile.get());
            } else {
                //Policy file doesn't exist
                this.ismPolicyManagementStrategy = new NoIsmPolicyManagement(restHighLevelClient);
            }
        }

        private String getIndexPolicyName() {
            //If index prefix has a ending dash, then remove it to avoid two consecutive dashes.
            return indexPrefix.replaceAll("-$", "") + POLICY_NAME_SUFFIX;
        }
    }

    private static class TraceAnalyticsRawIndexManager extends IndexManager {
        public TraceAnalyticsRawIndexManager(final RestHighLevelClient restHighLevelClient,
                                             final OpenSearchSinkConfiguration openSearchSinkConfiguration,
                                                    final String indexAlias) {
            super(restHighLevelClient, openSearchSinkConfiguration, indexAlias);
            this.ismPolicyManagementStrategy = new IsmPolicyManagement(
                    restHighLevelClient,
                    IndexConstants.RAW_ISM_POLICY,
                    IndexConstants.RAW_ISM_FILE_WITH_ISM_TEMPLATE,
                    IndexConstants.RAW_ISM_FILE_NO_ISM_TEMPLATE);
        }

    }

    private static class TraceAnalyticsServiceMapIndexManager extends IndexManager {

        public TraceAnalyticsServiceMapIndexManager(final RestHighLevelClient restHighLevelClient,
                                                    final OpenSearchSinkConfiguration openSearchSinkConfiguration,
                                                    final String indexAlias) {
            super(restHighLevelClient, openSearchSinkConfiguration, indexAlias);
            this.ismPolicyManagementStrategy = new NoIsmPolicyManagement(restHighLevelClient);
        }

    }

    private class ManagementDisabledIndexManager extends IndexManager {
        protected ManagementDisabledIndexManager(final RestHighLevelClient restHighLevelClient, final OpenSearchSinkConfiguration openSearchSinkConfiguration, final String indexAlias) {
            super(restHighLevelClient, openSearchSinkConfiguration, indexAlias);
        }

        @Override
        public void setupIndex() {

        }
    }
}
