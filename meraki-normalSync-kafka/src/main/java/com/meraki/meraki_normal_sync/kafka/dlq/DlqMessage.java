package com.meraki.meraki_normal_sync.kafka.dlq;

public record DlqMessage(String sourceTopic,
                         int partition,
                         long offset,
                         String key,
                         String payload,
                         String error) {
}
