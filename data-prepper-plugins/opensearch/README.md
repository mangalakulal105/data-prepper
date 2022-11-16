# OpenSearch sink

This is the Data Prepper OpenSearch sink plugin that sends records to Elasticsearch cluster via REST client. You can use the sink to send data to Amazon Elasticsearch Service or Opendistro for Elasticsearch.

## Usages

The OpenSearch sink should be configured as part of Data Prepper pipeline yaml file.

### Raw span trace analytics

```
pipeline:
  ...
  sink:
    opensearch:
      hosts: ["https://localhost:9200"]
      cert: path/to/cert
      username: YOUR_USERNAME_HERE
      password: YOUR_PASSWORD_HERE
      trace_analytics_raw: true
      dlq_file: /your/local/dlq-file
      bulk_size: 4
```

The OpenSearch sink will reserve `otel-v1-apm-span-*` as index pattern and `otel-v1-apm-span` as index alias for record ingestion.

### </a>Service map trace analytics

```
pipeline:
  ...
  sink:
    opensearch:
      hosts: ["https://localhost:9200"]
      cert: path/to/cert
      username: YOUR_USERNAME_HERE
      password: YOUR_PASSWORD_HERE
      trace_analytics_service_map: true
      dlq_file: /your/local/dlq-file
      bulk_size: 4
```

The OpenSearch sink will reserve `otel-v1-apm-service-map` as index for record ingestion.

### Amazon Elasticsearch Service

The OpenSearch sink can also be configured for Amazon Elasticsearch Service domain. See [security](security.md) for details.

```
pipeline:
  ...
  sink:
    opensearch:
      hosts: ["https://your-amazon-elasticssearch-service-endpoint"]
      aws_sigv4: true
      cert: path/to/cert
      insecure: false
      trace_analytics_service_map: true
      bulk_size: 4
```

## Configuration

- `hosts`: A list of IP addresses of elasticsearch nodes.

- `cert`(optional): CA certificate that is pem encoded. Accepts both .pem or .crt. This enables the client to trust the CA that has signed the certificate that ODFE is using.
Default is null.

- `aws_sigv4`: A boolean flag to sign the HTTP request with AWS credentials. Only applies to Amazon Elasticsearch Service. See [security](security.md) for details. Default to `false`.

- `aws_region`: A String represents the region of Amazon Elasticsearch Service domain, e.g. us-west-2. Only applies to Amazon Elasticsearch Service. Defaults to `us-east-1`.

- `aws_sts_role_arn`: A IAM role arn which the sink plugin will assume to sign request to Amazon Elasticsearch. If not provided the plugin will use the default credentials.

- `insecure`: A boolean flag to turn off SSL certificate verification. If set to true, CA certificate verification will be turned off and insecure HTTP requests will be sent. Default to `false`.

- `socket_timeout`(optional): An integer value indicates the timeout in milliseconds for waiting for data (or, put differently, a maximum period inactivity between two consecutive data packets). A timeout value of zero is interpreted as an infinite timeout. If this timeout value is either negative or not set, the underlying Apache HttpClient would rely on operating system settings for managing socket timeouts.

- `connect_timeout`(optional): An integer value indicates the timeout in milliseconds used when requesting a connection from the connection manager. A timeout value of zero is interpreted as an infinite timeout. If this timeout value is either negative or not set, the underlying Apache HttpClient would rely on operating system settings for managing connection timeouts.

- `username`(optional): A String of username used in the [internal users](https://opendistro.github.io/for-elasticsearch-docs/docs/security/access-control/users-roles) of ODFE cluster. Default is null.

- `password`(optional): A String of password used in the [internal users](https://opendistro.github.io/for-elasticsearch-docs/docs/security/access-control/users-roles) of ODFE cluster. Default is null.

- `proxy`(optional): A String of the address of a forward HTTP proxy. The format is like "<host-name-or-ip>:\<port\>". Examples: "example.com:8100", "http://example.com:8100", "112.112.112.112:8100". Note: port number cannot be omitted.

- `index_type` (optional): a String from the list [`custom`, `trace-analytics-raw`, `trace-analytics-service-map`], which represents an index type. Defaults to `custom`. This index_type instructs Sink plugin what type of data it is handling. 

```
    APM trace analytics raw span data type example:
    {
    "traceId":"bQ/2NNEmtuwsGAOR5ntCNw==",
    "spanId":"mnO/qUT5ye4=",
    "name":"io.opentelemetry.auto.servlet-3.0",
    "kind":"SERVER",
    "status":{},
    "startTime":"2020-08-20T05:40:46.041011600Z",
    "endTime":"2020-08-20T05:40:46.089556800Z",
    ...
    }

    APM trace analytics service map data type example:
    {
      "hashId": "aQ/2NNEmtuwsGAOR5ntCNwk=",
      "serviceName": "Payment",
      "kind": "Client",
      "target":
      {
        "domain": "Purchase",
        "resource": "Buy"
      },
      "destination":
      {
        "domain": "Purchase",
        "resource": "Buy"
      },
      "traceGroupName": "MakePayement.auto"
    }
```
- `trace_analytics_raw`(optional, deprecated in favor of `index_type`): A boolean flag indicates APM trace analytics raw span data type.
Default value is false. Set it to true for [Raw span trace analytics](#raw_span_trace_analytics). Set it to false for [Service map trace analytics](#service_map_trace_analytics).

- `trace_analytics_service_map`(optional, deprecated in favor of `index_type`): A boolean flag indicates APM trace analytics service map data type.
Default value is false. Set it to true for [Service map trace analytics](#service_map_trace_analytics). Set it to false for [Raw span trace analytics](#raw_span_trace_analytics).

- <a name="index"></a>`index`: A String used as index name for custom data type. Applicable and required only If index_type is explicitly `custom` or defaults to be `custom` while both `trace_analytics_raw` and `trace_analytics_service_map` are set to false.

- <a name="template_file"></a>`template_file`(optional): A json file path to be read as index template for custom data ingestion. The json file content should be the json value of
`"template"` key in the json content of elasticsearch [Index templates API](https://www.elastic.co/guide/en/elasticsearch/reference/7.8/index-templates.html), 
e.g. [otel-v1-apm-span-index-template.json](https://github.com/opensearch-project/data-prepper/blob/main/data-prepper-plugins/opensearch/src/main/resources/otel-v1-apm-span-index-template.json)

- `number_of_shards` (optional): The number of primary shards that an index should have on the destination OpenSearch server. This parameter is effective only when `template_file` is either explicitly provided in Sink configuration or built-in. If this parameter is set, it would override the value in index template file. OpenSearch documentation has [more about this parameter](https://opensearch.org/docs/latest/opensearch/rest-api/index-apis/create-index/).

- `number_of_replicas` (optional): The number of replica shards each primary shard should have on the destination OpenSearch server. For example, if you have 4 primary shards and set number_of_replicas to 3, the index has 12 replica shards. This parameter is effective only when `template_file` is either explicitly provided in Sink configuration or built-in. If this parameter is set, it would override the value in index template file. OpenSearch documentation has [more about this parameter](https://opensearch.org/docs/latest/opensearch/rest-api/index-apis/create-index/).

- `dlq_file`(optional): A String of absolute file path for DLQ failed output records. Defaults to null.
If not provided, failed records will be written into the default data-prepper log file (`logs/Data-Prepper.log`).

- `bulk_size` (optional): A long of bulk size in bulk requests in MB. Default to 5 MB. If set to be less than 0,
all the records received from the upstream prepper at a time will be sent as a single bulk request.
If a single record turns out to be larger than the set bulk size, it will be sent as a bulk request of a single document.

- `ism_policy_file` (optional): A String of absolute file path for an ISM (Index State Management) policy JSON file. This policy file is effective only when there is no built-in policy file for the index type. For example, `custom` index type is currently the only one without a built-in policy file, thus it would use the policy file here if it's provided through this parameter. OpenSearch documentation has more about [ISM policies.](https://opensearch.org/docs/latest/im-plugin/ism/policies/)

## Metrics

Besides common metrics in [AbstractSink](https://github.com/opensearch-project/data-prepper/blob/main/data-prepper-api/src/main/java/com/amazon/dataprepper/model/sink/AbstractSink.java), OpenSearch sink introduces the following custom metrics.

### Timer

- `bulkRequestLatency`: measures latency of sending each bulk request including retries.

### Counter

- `bulkRequestErrors`: measures number of errors encountered in sending bulk requests.
- `documentsSuccess`: measures number of documents successfully sent to ES by bulk requests including retries.
- `documentsSuccessFirstAttempt`: measures number of documents successfully sent to ES by bulk requests on first attempt.
- `documentErrors`: measures number of documents failed to be sent by bulk requests.

### Distribution Summary
- `bulkRequestSizeBytes`: measures the distribution of bulk request's payload sizes in bytes.

## Developer Guide

This plugin is compatible with Java 8. See

- [CONTRIBUTING](https://github.com/opensearch-project/data-prepper/blob/main/CONTRIBUTING.md) 
- [monitoring](https://github.com/opensearch-project/data-prepper/blob/main/docs/monitoring.md)
