/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.dataprepper.plugins.codec.parquet;


import org.apache.parquet.io.PositionOutputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class S3OutputStream extends PositionOutputStream {

    /**
     * Default chunk size is 10MB
     */
    protected static final int BUFFER_SIZE = 10000000;

    /**
     * The bucket-name on Amazon S3
     */
    private final String bucket;

    /**
     * The path (key) name within the bucket
     */
    private final String path;

    /**
     * The temporary buffer used for storing the chunks
     */
    private final byte[] buf;

    private final S3Client s3Client;
    /**
     * Collection of the etags for the parts that have been uploaded
     */
    private final List<String> etags;
    /**
     * The position in the buffer
     */
    private int position;
    /**
     * The unique id for this upload
     */
    private String uploadId;
    /**
     * indicates whether the stream is still open / valid
     */
    private boolean open;

    /**
     * Creates a new S3 OutputStream
     *
     * @param s3Client the AmazonS3 client
     * @param bucket   name of the bucket
     * @param path     path within the bucket
     */
    public S3OutputStream(S3Client s3Client, String bucket, String path) {
        this.s3Client = s3Client;
        this.bucket = bucket;
        this.path = path;
        buf = new byte[BUFFER_SIZE];
        position = 0;
        etags = new ArrayList<>();
        open = true;
    }

    @Override
    public void write(int b) {
        assertOpen();
        if (position >= buf.length) {
            flushBufferAndRewind();
        }
        buf[position++] = (byte) b;
    }


    /**
     * Write an array to the S3 output stream.
     *
     * @param b the byte-array to append
     */
    @Override
    public void write(byte[] b) {
        write(b, 0, b.length);
    }


    /**
     * Writes an array to the S3 Output Stream
     *
     * @param byteArray the array to write
     * @param o         the offset into the array
     * @param l         the number of bytes to write
     */
    @Override
    public void write(byte[] byteArray, int o, int l) {
        assertOpen();
        int ofs = o;
        int len = l;
        int size;
        while (len > (size = buf.length - position)) {
            System.arraycopy(byteArray, ofs, buf, position, size);
            position += size;
            flushBufferAndRewind();
            ofs += size;
            len -= size;
        }
        System.arraycopy(byteArray, ofs, buf, position, len);
        position += len;
    }

    /**
     * Flushes the buffer by uploading a part to S3.
     */
    @Override
    public synchronized void flush() {
        assertOpen();
    }

    @Override
    public void close() {
        if (open) {
            open = false;
            if (uploadId != null) {
                if (position > 0) {
                    uploadPart();
                }

                CompletedPart[] completedParts = new CompletedPart[etags.size()];
                for (int i = 0; i < etags.size(); i++) {
                    completedParts[i] = CompletedPart.builder()
                            .eTag(etags.get(i))
                            .partNumber(i + 1)
                            .build();
                }

                CompletedMultipartUpload completedMultipartUpload = CompletedMultipartUpload.builder()
                        .parts(completedParts)
                        .build();
                CompleteMultipartUploadRequest completeMultipartUploadRequest = CompleteMultipartUploadRequest.builder()
                        .bucket(bucket)
                        .key(path)
                        .uploadId(uploadId)
                        .multipartUpload(completedMultipartUpload)
                        .build();
                s3Client.completeMultipartUpload(completeMultipartUploadRequest);
            } else {
                PutObjectRequest putRequest = PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(path)
                        .contentLength((long) position)
                        .build();

                RequestBody requestBody = RequestBody.fromInputStream(new ByteArrayInputStream(buf, 0, position),
                        position);
                s3Client.putObject(putRequest, requestBody);
            }
        }
    }

    private void assertOpen() {
        if (!open) {
            throw new IllegalStateException("Closed");
        }
    }

    protected void flushBufferAndRewind() {
        if (uploadId == null) {
            CreateMultipartUploadRequest uploadRequest = CreateMultipartUploadRequest.builder()
                    .bucket(bucket)
                    .key(path)
                    .build();
            CreateMultipartUploadResponse multipartUpload = s3Client.createMultipartUpload(uploadRequest);
            uploadId = multipartUpload.uploadId();
        }
        uploadPart();
        position = 0;
    }

    protected void uploadPart() {
        UploadPartRequest uploadRequest = UploadPartRequest.builder()
                .bucket(bucket)
                .key(path)
                .uploadId(uploadId)
                .partNumber(etags.size() + 1)
                .contentLength((long) position)
                .build();
        RequestBody requestBody = RequestBody.fromInputStream(new ByteArrayInputStream(buf, 0, position),
                position);
        UploadPartResponse uploadPartResponse = s3Client.uploadPart(uploadRequest, requestBody);
        etags.add(uploadPartResponse.eTag());
    }

    @Override
    public long getPos() throws IOException {
        return position;
    }
}

