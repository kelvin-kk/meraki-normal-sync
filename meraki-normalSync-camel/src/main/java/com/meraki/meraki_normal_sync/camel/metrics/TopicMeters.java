package com.meraki.meraki_normal_sync.camel.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.TimeUnit;

public class TopicMeters {
    public final Counter consumed;
    public final Counter success;
    public final Counter failed;
    public final Counter dlq;
    public final Timer processingTime;

    public TopicMeters(MeterRegistry registry, String topic) {

        this.consumed = Counter.builder("meraki_kafka_consumed_total")
                .description("Total Kafka records consumed")
                .tag("topic", topic)
                .register(registry);

        this.success = Counter.builder("meraki_kafka_success_total")
                .description("Total Kafka records successfully processed")
                .tag("topic", topic)
                .register(registry);

        this.failed = Counter.builder("meraki_kafka_failed_total")
                .description("Total Kafka records failed during processing")
                .tag("topic", topic)
                .register(registry);

        this.dlq = Counter.builder("meraki_kafka_dlq_total")
                .description("Total Kafka records sent to DLQ")
                .tag("topic", topic)
                .register(registry);

        this.processingTime = Timer.builder("meraki_kafka_processing_time")
                .description("Processing time per record")
                .tag("topic", topic)
                .publishPercentileHistogram(true)
                .register(registry);
    }

    public void recordNanos(long nanos) {
        this.processingTime.record(nanos, TimeUnit.NANOSECONDS);
    }
}
