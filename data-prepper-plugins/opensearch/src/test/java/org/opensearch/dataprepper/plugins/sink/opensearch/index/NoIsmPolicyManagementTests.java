/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.sink.opensearch.index;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.opensearch.client.IndicesClient;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.indices.GetIndexRequest;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.indices.ExistsRequest;
import org.opensearch.client.opensearch.indices.OpenSearchIndicesClient;
import org.opensearch.client.transport.endpoints.BooleanResponse;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class NoIsmPolicyManagementTests {

    private IsmPolicyManagementStrategy ismPolicyManagementStrategy;
    private final String INDEX_ALIAS = "test-alias-abcd";

    @Mock
    private RestHighLevelClient restHighLevelClient;

    @Mock
    private OpenSearchClient openSearchClient;

    @Mock
    OpenSearchIndicesClient openSearchIndicesClient;

    @Before
    public void setup() throws IOException {
        initMocks(this);
        ismPolicyManagementStrategy = new NoIsmPolicyManagement(openSearchClient, restHighLevelClient);
    }

    @Test
    public void constructor_NullRestClient() throws IOException {
        assertThrows(NullPointerException.class, () ->
                new NoIsmPolicyManagement(openSearchClient, null));
    }

    @Test
    public void checkAndCreatePolicy() throws IOException {
        assertEquals(Optional.empty(), ismPolicyManagementStrategy.checkAndCreatePolicy());
    }

    @Test
    public void getIndexPatterns() {
        assertEquals(Collections.singletonList(INDEX_ALIAS), ismPolicyManagementStrategy.getIndexPatterns(INDEX_ALIAS));
    }

    @Test
    public void getIndexPatterns_NullInput_Exception() {
        assertThrows(IllegalArgumentException.class,
                () -> ismPolicyManagementStrategy.getIndexPatterns(null)
        );
    }

    @Test
    public void checkIfIndexExistsOnServer_NullInput_Exception() {
        assertThrows(IllegalArgumentException.class,
                () -> ismPolicyManagementStrategy.checkIfIndexExistsOnServer(null)
        );
    }

    @Test
    public void checkIfIndexExistsOnServer_false() throws IOException {
        when(openSearchClient.indices()).thenReturn(openSearchIndicesClient);
        when(openSearchIndicesClient.exists(any(ExistsRequest.class))).thenReturn(new BooleanResponse(false));
        assertEquals(false, ismPolicyManagementStrategy.checkIfIndexExistsOnServer(INDEX_ALIAS));
    }

    @Test
    public void checkIfIndexExistsOnServer_true() throws IOException {
        when(openSearchClient.indices()).thenReturn(openSearchIndicesClient);
        when(openSearchIndicesClient.exists(any(ExistsRequest.class))).thenReturn(new BooleanResponse(true));
        assertEquals(true, ismPolicyManagementStrategy.checkIfIndexExistsOnServer(INDEX_ALIAS));
    }

    @Test
    public void getCreateIndexRequest_NullInput_Exception() {
        assertThrows(IllegalArgumentException.class,
                () -> ismPolicyManagementStrategy.getCreateIndexRequest(null)
        );
    }

    @Test
    public void getCreateIndexRequest() {
        assertNotNull(ismPolicyManagementStrategy.getCreateIndexRequest(INDEX_ALIAS));
    }

}
