/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.dataprepper.plugins.sink.opensearch;

import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.client.RestClient;
import org.opensearch.common.Strings;
import org.opensearch.common.xcontent.XContentFactory;

import javax.ws.rs.HttpMethod;
import java.io.IOException;

class OpenSearchSecurityAccessor {
    private static final String PLUGINS_SECURITY_API = "_opendistro/_security/api/";
    private RestClient client;

    OpenSearchSecurityAccessor(RestClient client) {
        this.client = client;
    }

    void createBulkWritingRole(String role, String indexPattern) throws IOException {
        createRole(role, indexPattern, "indices:data/write/index", "indices:data/write/bulk*");
    }

    private void createRole(String role, String indexPattern, String... allowedActions) throws IOException {
        final Request request = new Request(HttpMethod.PUT, PLUGINS_SECURITY_API + "roles/" + role);

        final String createRoleJson = Strings.toString(
                XContentFactory.jsonBuilder()
                        .startObject()
                        .startArray("index_permissions")
                        .startObject()
                        .array("index_patterns", new String[]{indexPattern})
                        .array("allowed_actions", allowedActions)
                        .endObject()
                        .endArray()
                        .endObject()
        );
        request.setJsonEntity(createRoleJson);
        final Response response = client.performRequest(request);
    }

    public void createUser(String username, String password, String... roles) throws IOException {
        final Request request = new Request(HttpMethod.PUT, PLUGINS_SECURITY_API + "internalusers/" + username);

        final String createUserJson = Strings.toString(
                XContentFactory.jsonBuilder()
                        .startObject()
                        .field("password", password)
                        .array("opendistro_security_roles", roles)
                        .endObject()
        );
        request.setJsonEntity(createUserJson);
        final Response response = client.performRequest(request);
    }
}
