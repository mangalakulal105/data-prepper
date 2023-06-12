/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.dataprepper.plugins.source.opensearch.worker.client.model;

public class CreatePointInTimeResponse {

    private final String pitId;
    private final Long pitCreationTime;
    private final Long keepAlive;

    public String getPitId() {
        return pitId;
    }

    public Long getPitCreationTime() { return pitCreationTime; }

    public Long getKeepAlive() { return keepAlive; }

    private CreatePointInTimeResponse(final CreatePointInTimeResponse.Builder builder) {
        this.pitId = builder.pitId;
        this.pitCreationTime = builder.pitCreationTime;
        this.keepAlive = builder.keepAlive;
    }

    public static CreatePointInTimeResponse.Builder builder() {
        return new CreatePointInTimeResponse.Builder();
    }

    public static class Builder {

        private String pitId;
        private Long pitCreationTime;
        private Long keepAlive;

        public Builder() {

        }

        public CreatePointInTimeResponse.Builder withPitId(final String pitId) {
            this.pitId = pitId;
            return this;
        }

        public CreatePointInTimeResponse.Builder withCreationTime(final Long pitCreationTime) {
            this.pitCreationTime = pitCreationTime;
            return this;
        }

        public CreatePointInTimeResponse.Builder withKeepAlive(final Long keepAlive) {
            this.keepAlive = keepAlive;
            return this;
        }

        public CreatePointInTimeResponse build() {
            return new CreatePointInTimeResponse(this);
        }
    }
}
