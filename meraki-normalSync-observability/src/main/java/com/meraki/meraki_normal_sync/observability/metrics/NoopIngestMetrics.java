package com.meraki.meraki_normal_sync.observability.metrics;

public class NoopIngestMetrics implements IngestMetrics{
    @Override
    public void incConsumed(String topic) { }

    @Override
    public void incProcessed(String topic) { }

    @Override
    public void incDlq(String topic) { }

    @Override
    public void incErrors(String topic) { }

    @Override
    public void recordDbWriteMs(String destTable, long millis) { }
}
