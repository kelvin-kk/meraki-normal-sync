package com.meraki.meraki_normal_sync.camel.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

@Component
public class TopicMetrics {

    private final MeterRegistry registry;
    private final ConcurrentMap<String, TopicMeters> meters = new ConcurrentHashMap<>();

    public TopicMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    private TopicMeters topic(String topic) {
        String t = (topic == null || topic.isBlank()) ? "UNKNOWN" : topic.trim();
        return meters.computeIfAbsent(t, k -> new TopicMeters(registry, k));
    }

    // ---- counters ----
    public void incConsumed(String topic) { topic(topic).consumed.increment(); }
    public void incSuccess(String topic)  { topic(topic).success.increment(); }
    public void incFailed(String topic)   { topic(topic).failed.increment(); }
    public void incDlq(String topic)      { topic(topic).dlq.increment(); }

    // ---- timer (nanoTime based; no Timer.Sample) ----
    public long startTimer() {
        return System.nanoTime();
    }

    public void stopTimer(String topic, long startNanos) {
        long elapsedNanos = System.nanoTime() - startNanos;
        topic(topic).processingTime.record(elapsedNanos, TimeUnit.NANOSECONDS);
    }
}
