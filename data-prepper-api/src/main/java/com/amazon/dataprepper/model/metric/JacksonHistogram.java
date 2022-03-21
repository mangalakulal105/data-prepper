package com.amazon.dataprepper.model.metric;

import com.amazon.dataprepper.model.event.EventType;
import com.amazon.dataprepper.model.trace.JacksonSpan;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A Jackson implementation for {@link Histogram}.
 *
 * @since 1.4
 */
public class JacksonHistogram extends JacksonMetric implements Histogram {

    private static final String SUM_KEY = "sum";
    private static final String AGGREGATION_TEMPORALITY_KEY = "aggregationTemporality";
    private static final String BUCKET_COUNTS_KEY = "bucketCounts";
    private static final String EXPLICIT_BOUNDS_COUNT_KEY = "explicitBoundsCount";
    private static final String BUCKETS_KEY = "buckets";

    private static final List<String> REQUIRED_KEYS = new ArrayList<>();
    private static final List<String> REQUIRED_NON_EMPTY_KEYS = Arrays.asList(NAME_KEY, KIND_KEY, TIME_KEY);
    private static final List<String> REQUIRED_NON_NULL_KEYS = Collections.singletonList(SUM_KEY);


    protected JacksonHistogram(JacksonHistogram.Builder builder) {
        super(builder);

        checkArgument(this.getMetadata().getEventType().equals(EventType.METRIC.toString()), "eventType must be of type Metric");
    }

    public static JacksonHistogram.Builder builder() {
        return new JacksonHistogram.Builder();
    }

    @Override
    public Double getSum() {
        return this.get(SUM_KEY, Double.class);
    }

    @Override
    public String getAggregationTemporality() {
        return this.get(AGGREGATION_TEMPORALITY_KEY, String.class);
    }

    @Override
    public Integer getBucketCount() {
        return this.get(BUCKET_COUNTS_KEY, Integer.class);
    }

    @Override
    public Integer getExplicitBoundsCount() {
        return this.get(EXPLICIT_BOUNDS_COUNT_KEY, Integer.class);
    }

    @Override
    public List<Bucket> getBuckets() {
        return this.get(BUCKETS_KEY, List.class);
    }

    /**
     * Builder for creating JacksonHistogram
     *
     * @since 1.4
     */
    public static class Builder extends JacksonMetric.Builder<JacksonHistogram.Builder> {

        @Override
        public JacksonHistogram.Builder getThis() {
            return this;
        }

        /**
         * Sets the sum of the histogram
         * @param sum the sum of the histogram
         * @return the builder
         * @since 1.4
         */
        public JacksonHistogram.Builder withSum(double sum) {
            data.put(SUM_KEY, sum);
            return this;
        }

        /**
         * Sets the number of buckets for this histogram
         * @param bucketCount the number of buckets
         * @return the builder
         * @since 1.4
         */
        public JacksonHistogram.Builder withBucketCount(int bucketCount) {
            data.put(BUCKET_COUNTS_KEY, bucketCount);
            return this;
        }

        /**
         * Sets the bounds count for this histogram
         * @param explicitBoundsCount the count for the bounds
         * @return the builder
         * @since 1.4
         */
        public JacksonHistogram.Builder withExplicitBoundsCount(int explicitBoundsCount) {
            data.put(EXPLICIT_BOUNDS_COUNT_KEY, explicitBoundsCount);
            return this;
        }

        /**
         * Sets the aggregation temporality for this histogram
         * @param aggregationTemporality the aggregation temporality
         * @return the builder
         * @since 1.4
         */
        public  JacksonHistogram.Builder withAggregationTemporality(String aggregationTemporality) {
            data.put(AGGREGATION_TEMPORALITY_KEY, aggregationTemporality);
            return this;
        }

        /**
         * Sets the buckets for this histogram
         * @param buckets a list of buckets
         * @return the builder
         * @since 1.4
         */
        public JacksonHistogram.Builder withBuckets(List<Bucket> buckets) {
            data.put(BUCKETS_KEY, buckets);
            return this;
        }

        /**
         * Sets all attributes by copying over those from another histogram
         * @param histogram the histogram to copy
         * @return the builder
         * @since 1.4
         */
        public JacksonHistogram.Builder fromHistogram(final Histogram histogram) {
            this.withName(histogram.getName())
                    .withServiceName(histogram.getServiceName())
                    .withEventKind(histogram.getKind())
                    .withStartTime(histogram.getStartTime())
                    .withTime(histogram.getTime())
                    .withAttributes(histogram.getAttributes())
                    .withSum(histogram.getSum())
                    .withUnit(histogram.getUnit())
                    .withDescription(histogram.getDescription())
                    .build();
            return this;
        }

        /**
         * Returns a newly created {@link JacksonSpan}
         * @return a JacksonSpan
         * @since 1.4
         */
        public JacksonHistogram build() {
            this.withData(data);
            this.withEventKind(KIND.HISTOGRAM.toString());
            this.withEventType(EventType.METRIC.toString());
            checkAndSetDefaultValues();
            new ParameterValidator().validate(REQUIRED_KEYS, REQUIRED_NON_EMPTY_KEYS, REQUIRED_NON_NULL_KEYS, data);

            return new JacksonHistogram(this);
        }

        private void checkAndSetDefaultValues() {
            data.computeIfAbsent(ATTRIBUTES_KEY, k -> new HashMap<>());
        }

    }

    public static class Bucket {

        private final double lowerBound;
        private final double upperBound;
        private final long count;

        public Bucket(double lowerBound, double upperBound, long count) {
            this.lowerBound = lowerBound;
            this.upperBound = upperBound;
            this.count = count;
        }

        public double getLowerBound() {
            return lowerBound;
        }

        public double getUpperBound() {
            return upperBound;
        }

        public long getCount() {
            return count;
        }

    }
}
