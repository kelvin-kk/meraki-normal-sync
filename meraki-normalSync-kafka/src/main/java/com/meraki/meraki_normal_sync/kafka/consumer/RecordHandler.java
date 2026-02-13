package com.meraki.meraki_normal_sync.kafka.consumer;

public interface RecordHandler {
    void handle(RawKafkaMessage msg) throws Exception;
}
