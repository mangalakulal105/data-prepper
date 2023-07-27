package org.opensearch.dataprepper.plugins.sink.opensearch.bulk;

import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.dataprepper.plugins.sink.opensearch.DistributionVersion;
import org.opensearch.dataprepper.plugins.sink.opensearch.index.IndexConfiguration;

public class BulkApiWrapperFactory {
    public static BulkApiWrapper getWrapper(final IndexConfiguration indexConfiguration,
                                            final OpenSearchClient openSearchClient) {
        if (DistributionVersion.ES6.equals(indexConfiguration.getDistributionVersion())) {
            return new Es6BulkApiWrapper(openSearchClient);
        } else {
            return new OpenSearchDefaultBulkApiWrapper(openSearchClient);
        }
    }
}
