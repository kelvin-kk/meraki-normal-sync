package com.meraki.meraki_normal_sync.observability.metrics;

public interface IngestMetrics {
    void incConsumed(String topic);

    void incProcessed(String topic);

    void incDlq(String topic);

    void incErrors(String topic);

    void recordDbWriteMs(String destTable, long millis);
}
