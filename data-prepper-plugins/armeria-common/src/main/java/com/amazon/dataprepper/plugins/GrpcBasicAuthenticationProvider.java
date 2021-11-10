package com.amazon.dataprepper.plugins;

import com.amazon.dataprepper.armeria.authentication.GrpcAuthenticationProvider;
import com.amazon.dataprepper.armeria.authentication.HttpBasicAuthenticationConfig;
import com.amazon.dataprepper.model.annotations.DataPrepperPlugin;
import com.amazon.dataprepper.model.annotations.DataPrepperPluginConstructor;
import io.grpc.Metadata;
import io.grpc.Metadata.Key;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;

/**
 * The plugin for gRPC Basic authentication of Armeria servers.
 *
 * @since 1.2
 */
@DataPrepperPlugin(name = "http_basic",
        pluginType = GrpcAuthenticationProvider.class,
        pluginConfigurationType = HttpBasicAuthenticationConfig.class)
public class GrpcBasicAuthenticationProvider implements GrpcAuthenticationProvider {
    private final HttpBasicAuthenticationConfig httpBasicAuthenticationConfig;
    private final ServerInterceptor authenticationInterceptor;
    private final String base64EncodedCredentialsFromConfig;
    private static final String AUTH_HEADER = "authorization";
    private static final String BASIC = "basic";
    private static final int CREDENTIAL_START_INDEX = 5;

    @DataPrepperPluginConstructor
    public GrpcBasicAuthenticationProvider(final HttpBasicAuthenticationConfig httpBasicAuthenticationConfig) {
        Objects.requireNonNull(httpBasicAuthenticationConfig);
        Objects.requireNonNull(httpBasicAuthenticationConfig.getUsername());
        Objects.requireNonNull(httpBasicAuthenticationConfig.getPassword());
        this.httpBasicAuthenticationConfig = httpBasicAuthenticationConfig;
        this.authenticationInterceptor = new GrpcBasicAuthenticationInterceptor();
        this.base64EncodedCredentialsFromConfig = Base64.getEncoder()
                .encodeToString(String.format("%s:%s", httpBasicAuthenticationConfig.getUsername(), httpBasicAuthenticationConfig.getPassword()).getBytes(StandardCharsets.UTF_8));
    }

    public ServerInterceptor getAuthenticationInterceptor() {
        return authenticationInterceptor;
    }

    private class GrpcBasicAuthenticationInterceptor implements ServerInterceptor {
        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
            final String authorization = headers.get(Key.of(AUTH_HEADER, ASCII_STRING_MARSHALLER));
            if (authorization != null && authorization.toLowerCase().startsWith(BASIC)) {
                final String base64EncodedCredentialsFromRequestHeader = authorization.substring(CREDENTIAL_START_INDEX).trim();
                if (!base64EncodedCredentialsFromConfig.equals(base64EncodedCredentialsFromRequestHeader)) {
                    call.close(Status.UNAUTHENTICATED.withDescription("Invalid username or password\n"), headers);
                }
            } else {
                call.close(Status.UNAUTHENTICATED.withDescription("Invalid or no authorization provided\n"), headers);
            }
            return next.startCall(call, headers);
        }
    }
}

