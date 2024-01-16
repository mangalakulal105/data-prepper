/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.dataprepper.plugins.sink.prometheus.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.apache.commons.lang3.StringUtils;
import org.opensearch.dataprepper.model.configuration.PluginModel;
import org.opensearch.dataprepper.plugins.sink.prometheus.util.PrometheusSinkUtil;

import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class PrometheusSinkConfiguration {

    private static final int DEFAULT_UPLOAD_RETRIES = 5;

    static final boolean DEFAULT_INSECURE = false;

    private static final String S3_PREFIX = "s3://";

    static final String SSL_KEY_CERT_FILE = "sslKeyCertChainFile";
    static final String SSL_KEY_FILE = "sslKeyFile";
    static final String SSL = "ssl";
    static final String AWS_REGION = "awsRegion";

    public static final String STS_REGION = "sts_region";

    public static final String STS_ROLE_ARN = "sts_role_arn";
    static final boolean DEFAULT_USE_ACM_CERT_FOR_SSL = false;
    public static final int DEFAULT_ACM_CERT_ISSUE_TIME_OUT_MILLIS = 120000;
    public static final String SSL_IS_ENABLED = "%s is enabled";

    public static final Duration DEFAULT_HTTP_RETRY_INTERVAL = Duration.ofSeconds(30);

    private static final String HTTPS = "https";

    private static final String HTTP = "http";

    private static final String AWS_HOST_AMAZONAWS_COM = "amazonaws.com";

    private static final String AWS_HOST_API_AWS = "api.aws";

    private static final String AWS_HOST_ON_AWS = "on.aws";

    private static final String DEFAULT_ENCODING = "snappy";

    private static final String DEFAULT_CONTENT_TYPE = "application/x-protobuf";

    private static final String DEFAULT_REMOTE_WRITE_VERSION = "0.1.0";


    @NotNull
    @JsonProperty("url")
    private String url;

    @JsonProperty("http_method")
    private HTTPMethodOptions httpMethod = HTTPMethodOptions.POST;

    @JsonProperty("proxy")
    private String proxy;

    @JsonProperty("auth_type")
    private AuthTypeOptions authType = AuthTypeOptions.UNAUTHENTICATED;

    @JsonProperty("authentication")
    private AuthenticationOptions authentication;

    @JsonProperty("ssl_certificate_file")
    private String sslCertificateFile;

    @JsonProperty("ssl_key_file")
    private String sslKeyFile;

    @JsonProperty("max_retries")
    private int maxUploadRetries = DEFAULT_UPLOAD_RETRIES;

    @JsonProperty("aws")
    @Valid
    private AwsAuthenticationOptions awsAuthenticationOptions;

    @JsonProperty("custom_header")
    private Map<String, List<String>> customHeaderOptions;

    @JsonProperty("dlq_file")
    private String dlqFile;

    @JsonProperty("dlq")
    private PluginModel dlq;

    @JsonProperty("use_acm_cert_for_ssl")
    private boolean useAcmCertForSSL = DEFAULT_USE_ACM_CERT_FOR_SSL;

    @JsonProperty("acm_private_key_password")
    private String acmPrivateKeyPassword;

    @JsonProperty("acm_certificate_arn")
    private String acmCertificateArn;

    @JsonProperty("acm_cert_issue_time_out_millis")
    private long acmCertIssueTimeOutMillis = DEFAULT_ACM_CERT_ISSUE_TIME_OUT_MILLIS;

    @JsonProperty("insecure")
    private boolean insecure = DEFAULT_INSECURE;

    @JsonProperty("insecure_skip_verify")
    private boolean insecureSkipVerify = DEFAULT_INSECURE;

    @JsonProperty("http_retry_interval")
    private Duration httpRetryInterval = DEFAULT_HTTP_RETRY_INTERVAL;

    @JsonProperty("encoding")
    private String encoding = DEFAULT_ENCODING;

    @JsonProperty("content_type")
    private String contentType = DEFAULT_CONTENT_TYPE;

    @JsonProperty("remote_write_version")
    private String remoteWriteVersion = DEFAULT_REMOTE_WRITE_VERSION;

    @JsonProperty("request_timout")
    private Duration requestTimout;

    private boolean sslCertAndKeyFileInS3;

    public String getUrl() {
        return url;
    }

    public boolean isInsecureSkipVerify() {
        return insecureSkipVerify; }

    public Duration getHttpRetryInterval() {
        return httpRetryInterval;
    }

    public String getAcmPrivateKeyPassword() {
        return acmPrivateKeyPassword;
    }

    public boolean isSslCertAndKeyFileInS3() {
        return sslCertAndKeyFileInS3;
    }

    public long getAcmCertIssueTimeOutMillis() {
        return acmCertIssueTimeOutMillis;
    }

    public boolean useAcmCertForSSL() {
        return useAcmCertForSSL;
    }

    public void validateAndInitializeCertAndKeyFileInS3() {
        boolean certAndKeyFileInS3 = false;
        if (useAcmCertForSSL) {
            validateSSLArgument(String.format(SSL_IS_ENABLED, useAcmCertForSSL), acmCertificateArn, acmCertificateArn);
            validateSSLArgument(String.format(SSL_IS_ENABLED, useAcmCertForSSL), awsAuthenticationOptions.getAwsRegion().toString(), AWS_REGION);
        } else if(!insecureSkipVerify) {
            validateSSLCertificateFiles();
            certAndKeyFileInS3 = isSSLCertificateLocatedInS3();
            if (certAndKeyFileInS3) {
                validateSSLArgument("The certificate and key files are located in S3", awsAuthenticationOptions.getAwsRegion().toString(), AWS_REGION);
            }
        }
        sslCertAndKeyFileInS3 = certAndKeyFileInS3;
    }
    private void validateSSLArgument(final String sslTypeMessage, final String argument, final String argumentName) {
        if (StringUtils.isEmpty(argument)) {
            throw new IllegalArgumentException(String.format("%s, %s can not be empty or null", sslTypeMessage, argumentName));
        }
    }

    private void validateSSLCertificateFiles() {
        validateSSLArgument(String.format(SSL_IS_ENABLED, SSL), sslCertificateFile, SSL_KEY_CERT_FILE);
        validateSSLArgument(String.format(SSL_IS_ENABLED, SSL), sslKeyFile, SSL_KEY_FILE);
    }

    private boolean isSSLCertificateLocatedInS3() {
        return sslCertificateFile.toLowerCase().startsWith(S3_PREFIX) &&
                sslKeyFile.toLowerCase().startsWith(S3_PREFIX);
    }

    public String getAcmCertificateArn() {
        return acmCertificateArn;
    }

    public HTTPMethodOptions getHttpMethod() {
        return httpMethod;
    }

    public String getProxy() {
        return proxy;
    }

    public AuthTypeOptions getAuthType() {
        return authType;
    }

    public AuthenticationOptions getAuthentication() {
        return authentication;
    }

    public String getSslCertificateFile() {
        return sslCertificateFile;
    }

    public String getSslKeyFile() {
        return sslKeyFile;
    }

    public int getMaxUploadRetries() {
        return maxUploadRetries;
    }

    public Map<String, List<String>> getCustomHeaderOptions() {
        return customHeaderOptions;
    }

    public AwsAuthenticationOptions getAwsAuthenticationOptions() {
        return awsAuthenticationOptions;
    }

    public String getDlqFile() {
        return dlqFile;
    }

    public PluginModel getDlq() {
        return dlq;
    }

    public boolean isValidAWSUrl() {
        URL parsedUrl = PrometheusSinkUtil.getURLByUrlString(url);
        if(parsedUrl.getProtocol().equals(HTTPS) && (parsedUrl.getHost().contains(AWS_HOST_AMAZONAWS_COM) ||parsedUrl.getHost().contains(AWS_HOST_API_AWS) || parsedUrl.getHost().contains(AWS_HOST_ON_AWS))){
            return true;
        }
        return false;
    }

    public String getDlqStsRoleARN(){
        return Objects.nonNull(getDlqPluginSetting().get(STS_ROLE_ARN)) ?
                String.valueOf(getDlqPluginSetting().get(STS_ROLE_ARN)) :
                awsAuthenticationOptions.getAwsStsRoleArn();
    }

    public String getDlqStsRegion(){
        return Objects.nonNull(getDlqPluginSetting().get(STS_REGION)) ?
                String.valueOf(getDlqPluginSetting().get(STS_REGION)) :
                awsAuthenticationOptions.getAwsRegion().toString();
    }

    public  Map<String, Object> getDlqPluginSetting(){
        return dlq != null ? dlq.getPluginSettings() : Map.of();
    }

    public String getEncoding() {
        return encoding;
    }

    public String getContentType() {
        return contentType;
    }

    public String getRemoteWriteVersion() {
        return remoteWriteVersion;
    }

    public boolean isInsecure() {
        return insecure;
    }

    public Duration getRequestTimout() {
        return requestTimout;
    }

    public boolean isHttpUrl() {
        URL parsedUrl = PrometheusSinkUtil.getURLByUrlString(url);
        return parsedUrl.getProtocol().equals(HTTP);
    }
}