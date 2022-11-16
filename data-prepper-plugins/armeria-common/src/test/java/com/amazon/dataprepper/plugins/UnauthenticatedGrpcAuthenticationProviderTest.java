/*
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  The OpenSearch Contributors require contributions made to
 *  this file be licensed under the Apache-2.0 license or a
 *  compatible open source license.
 *
 *  Modifications Copyright OpenSearch Contributors. See
 *  GitHub history for details.
 */

package com.amazon.dataprepper.plugins;

import com.amazon.dataprepper.armeria.authentication.GrpcAuthenticationProvider;
import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.common.RequestHeaders;
import com.linecorp.armeria.common.auth.BasicToken;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.server.grpc.GrpcService;
import com.linecorp.armeria.server.grpc.GrpcServiceBuilder;
import com.linecorp.armeria.testing.junit5.server.ServerExtension;
import io.grpc.ServerInterceptors;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.UUID;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

class UnauthenticatedGrpcAuthenticationProviderTest {
    private static GrpcAuthenticationProvider grpcAuthenticationProvider;

    @RegisterExtension
    static ServerExtension server = new ServerExtension() {
        @Override
        protected void configure(final ServerBuilder sb) {
            grpcAuthenticationProvider = new UnauthenticatedGrpcAuthenticationProvider();

            final GrpcServiceBuilder grpcServiceBuilder = GrpcService
                    .builder()
                    .enableUnframedRequests(true)
                    .addService(ServerInterceptors.intercept(new SampleHealthGrpcService(), grpcAuthenticationProvider.getAuthenticationInterceptor()));
            sb.service(grpcServiceBuilder.build());
        }
    };

    private static class SampleHealthGrpcService extends HealthGrpc.HealthImplBase {

        @Override
        public void check(final HealthCheckRequest request, final StreamObserver<HealthCheckResponse> responseObserver) {
            responseObserver.onNext(
                    HealthCheckResponse.newBuilder().setStatus(HealthCheckResponse.ServingStatus.SERVING).build());
            responseObserver.onCompleted();
        }
    }

    @Test
    void httpRequest_without_authentication_responds_OK() {
        final WebClient client = WebClient.of(server.httpUri());

        HttpRequest request = HttpRequest.of(RequestHeaders.builder()
                .method(HttpMethod.POST)
                .path("/grpc.health.v1.Health/Check")
                .contentType(MediaType.JSON_UTF_8)
                .build());

        final AggregatedHttpResponse httpResponse = client.execute(request).aggregate().join();

        // TODO: Figure out how to get SampleHealthGrpcService to return a status of 200
        assertThat(httpResponse.status(), equalTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void httpRequest_with_BasicAuthentication_responds_OK() {
        final WebClient client = WebClient.builder(server.httpUri())
                .auth(BasicToken.of(UUID.randomUUID().toString(), UUID.randomUUID().toString()))
                .build();

        HttpRequest request = HttpRequest.of(RequestHeaders.builder()
                .method(HttpMethod.POST)
                .path("/grpc.health.v1.Health/Check")
                .contentType(MediaType.JSON_UTF_8)
                .build());

        final AggregatedHttpResponse httpResponse = client.execute(request).aggregate().join();

        // TODO: Figure out how to get SampleHealthGrpcService to return a status of 200
        assertThat(httpResponse.status(), equalTo(HttpStatus.SERVICE_UNAVAILABLE));
    }
}
