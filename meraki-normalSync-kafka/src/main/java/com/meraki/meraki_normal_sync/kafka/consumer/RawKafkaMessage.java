package com.meraki.meraki_normal_sync.kafka.consumer;

public record RawKafkaMessage( String topic,
                               int partition,
                               long offset,
                               String key,
                               String value) {

}