package com.meraki.meraki_normal_sync.kafka.consumer;

import com.meraki.meraki_normal_sync.kafka.dlq.DlqMessage;
import com.meraki.meraki_normal_sync.kafka.dlq.DlqPublisher;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;

public class KafkaIngestionListener {
    private final RecordHandler handler;
    private final DlqPublisher dlq;

    public KafkaIngestionListener(RecordHandler handler, DlqPublisher dlq) {
        this.handler = handler;
        this.dlq = dlq;
    }

    @KafkaListener(
            topics = "#{topics}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment ack) {

        RawKafkaMessage msg = new RawKafkaMessage(
                record.topic(),
                record.partition(),
                record.offset(),
                record.key(),
                record.value()
        );

        try {
            handler.handle(msg);
            ack.acknowledge();
        } catch (Exception e) {
            dlq.publish(
                    record.topic(),
                    record.key(),
                    new DlqMessage(record.topic(), record.partition(), record.offset(), record.key(), record.value(), e.getMessage())
            );
            // acknowledge to avoid poison-pill retry loops
            ack.acknowledge();
        }
    }
}
