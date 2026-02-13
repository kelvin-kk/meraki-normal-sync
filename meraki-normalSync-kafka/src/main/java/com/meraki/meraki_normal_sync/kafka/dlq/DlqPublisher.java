package com.meraki.meraki_normal_sync.kafka.dlq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;

public class DlqPublisher {
    private final KafkaTemplate<String, String> template;
    private final String dlqSuffix;
    private final ObjectMapper mapper = new ObjectMapper();

    public DlqPublisher(KafkaTemplate<String, String> template, String dlqSuffix) {
        this.template = template;
        this.dlqSuffix = dlqSuffix;
    }

    public void publish(String topic, String key, DlqMessage msg) {
        try {
            String dlqTopic = topic + dlqSuffix;
            String json = mapper.writeValueAsString(msg);
            template.send(dlqTopic, key, json);
        } catch (Exception e) {
            // Last resort: nothing else we can do
            System.err.println("DLQ publish failed: " + e.getMessage());
        }
    }
}
