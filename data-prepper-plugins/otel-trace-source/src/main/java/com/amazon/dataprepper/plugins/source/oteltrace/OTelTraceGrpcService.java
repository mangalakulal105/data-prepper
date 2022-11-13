/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.dataprepper.plugins.source.oteltrace;

import com.amazon.dataprepper.metrics.PluginMetrics;
import com.amazon.dataprepper.model.buffer.Buffer;
import com.amazon.dataprepper.model.buffer.SizeOverflowException;
import com.amazon.dataprepper.model.record.Record;
import com.amazon.dataprepper.model.trace.Span;
import com.amazon.dataprepper.plugins.otel.codec.OTelProtoCodec;
import io.grpc.Context;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceResponse;
import io.opentelemetry.proto.collector.trace.v1.TraceServiceGrpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public class OTelTraceGrpcService extends TraceServiceGrpc.TraceServiceImplBase {
    private static final Logger LOG = LoggerFactory.getLogger(OTelTraceGrpcService.class);

    public static final String REQUEST_TIMEOUTS = "requestTimeouts";
    public static final String REQUESTS_RECEIVED = "requestsReceived";
    public static final String BAD_REQUESTS = "badRequests";
    public static final String REQUESTS_TOO_LARGE = "requestsTooLarge";
    public static final String INTERNAL_SERVER_ERROR = "internalServerError";
    public static final String SUCCESS_REQUESTS = "successRequests";
    public static final String PAYLOAD_SIZE = "payloadSize";
    public static final String REQUEST_PROCESS_DURATION = "requestProcessDuration";

    private final int bufferWriteTimeoutInMillis;
    private final OTelProtoCodec.OTelProtoDecoder oTelProtoDecoder;
    private final Buffer<Record<Object>> buffer;

    private final Counter requestTimeoutCounter;
    private final Counter requestsReceivedCounter;
    private final Counter successRequestsCounter;
    private final Counter badRequestsCounter;
    private final Counter requestsTooLargeCounter;
    private final Counter internalServerErrorCounter;
    private final DistributionSummary payloadSizeSummary;
    private final Timer requestProcessDuration;


    public OTelTraceGrpcService(int bufferWriteTimeoutInMillis,
                                final OTelProtoCodec.OTelProtoDecoder oTelProtoDecoder,
                                final Buffer<Record<Object>> buffer,
                                final PluginMetrics pluginMetrics) {
        this.bufferWriteTimeoutInMillis = bufferWriteTimeoutInMillis;
        this.buffer = buffer;
        this.oTelProtoDecoder = oTelProtoDecoder;

        requestTimeoutCounter = pluginMetrics.counter(REQUEST_TIMEOUTS);
        requestsReceivedCounter = pluginMetrics.counter(REQUESTS_RECEIVED);
        badRequestsCounter = pluginMetrics.counter(BAD_REQUESTS);
        requestsTooLargeCounter = pluginMetrics.counter(REQUESTS_TOO_LARGE);
        internalServerErrorCounter = pluginMetrics.counter(INTERNAL_SERVER_ERROR);
        successRequestsCounter = pluginMetrics.counter(SUCCESS_REQUESTS);
        payloadSizeSummary = pluginMetrics.summary(PAYLOAD_SIZE);
        requestProcessDuration = pluginMetrics.timer(REQUEST_PROCESS_DURATION);
    }


    @Override
    public void export(ExportTraceServiceRequest request, StreamObserver<ExportTraceServiceResponse> responseObserver) {
        requestProcessDuration.record(() -> processRequest(request, responseObserver));
    }

    private void processRequest(
            final ExportTraceServiceRequest request, final StreamObserver<ExportTraceServiceResponse> responseObserver) {
        requestsReceivedCounter.increment();
        payloadSizeSummary.record(request.getSerializedSize());

        if (Context.current().isCancelled()) {
            requestTimeoutCounter.increment();
            responseObserver.onError(Status.CANCELLED.withDescription("Cancelled by client").asRuntimeException());
            return;
        }

        Collection<Span> spans;

        try {
            spans = oTelProtoDecoder.parseExportTraceServiceRequest(request);
        } catch (Exception e) {
            LOG.error("Failed to parse the request content [{}] due to:", request, e);
            badRequestsCounter.increment();
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asException());
            return;
        }

        final List<Record<Object>> records = spans.stream().map(span -> new Record<Object>(span)).collect(Collectors.toList());

        try {
            buffer.writeAll(records, bufferWriteTimeoutInMillis);
            successRequestsCounter.increment();
            responseObserver.onNext(ExportTraceServiceResponse.newBuilder().build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            LOG.error("Failed to write the request content [{}] due to:", request, e);
            if (e instanceof TimeoutException) {
                requestTimeoutCounter.increment();
                responseObserver
                        .onError(Status.RESOURCE_EXHAUSTED.withDescription(e.getMessage())
                                .asException());
            } else if (e instanceof SizeOverflowException) {
                requestsTooLargeCounter.increment();
                responseObserver
                        .onError(Status.RESOURCE_EXHAUSTED.withDescription(e.getMessage())
                                .asException());
            } else {
                internalServerErrorCounter.increment();
                responseObserver
                        .onError(Status.INTERNAL.withDescription(e.getMessage())
                                .asException());
            }
        }
    }
}
