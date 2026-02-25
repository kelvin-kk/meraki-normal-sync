package com.meraki.meraki_normal_sync.camel;

import com.meraki.meraki_normal_sync.camel.metrics.TopicMetrics;
import com.meraki.meraki_normal_sync.kafka.consumer.RawKafkaMessage;
import com.meraki.meraki_normal_sync.kafka.consumer.RecordHandler;
import io.micrometer.core.instrument.Timer;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.component.kafka.consumer.KafkaManualCommit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CamelKafkaOrchestratorRoute extends RouteBuilder {
    private final RecordHandler handler;
    private final TopicMetrics metrics;

    @Value("${kafka.topics}")
    private String topicsCsv;

    @Value("${kafka.dlqSuffix:.DLQ}")
    private String dlqSuffix;

    public CamelKafkaOrchestratorRoute(RecordHandler handler, TopicMetrics metrics) {
        this.handler = handler;
        this.metrics = metrics;
    }

    @Override
    public void configure() {

        // Global error handler (route-level). We will apply per-topic route below too.
        // Keeping empty here; per-topic routes are created in addRoutesToCamelContext.
    }

    @Override
    public void addRoutesToCamelContext(org.apache.camel.CamelContext context) throws Exception {
        super.addRoutesToCamelContext(context);

        String[] topics = topicsCsv.split("\\s*,\\s*");

        for (String topic : topics) {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {

                    // Per-topic exception -> DLQ + metrics + commit
                    onException(Exception.class)
                            .handled(true)
                            .process(ex -> {
                                String t = ex.getIn().getHeader(KafkaConstants.TOPIC, String.class);
                                if (t == null) t = topic;

                                metrics.incFailed(t);

                                buildDlqExchange(ex, t);
                                metrics.incDlq(t);
                            })
                            .toD("kafka:${header.dlqTopic}")
                            .process(CamelKafkaOrchestratorRoute.this::manualCommitIfPresent);

                    from("kafka:" + topic
                            + "?brokers={{camel.component.kafka.brokers}}"
                            + "&groupId={{camel.component.kafka.groupId}}"
                            + "&autoOffsetReset={{camel.component.kafka.autoOffsetReset}}"
                            + "&allowManualCommit=true"
                            + "&maxPollRecords={{camel.component.kafka.maxPollRecords}}"
                            + "&consumersCount={{camel.component.kafka.consumersCount}}"
                    )
                            .routeId("camel-kafka-" + topic)
                            .process(ex -> {
                                String t = ex.getIn().getHeader(KafkaConstants.TOPIC, String.class);
                                if (t == null) t = topic;

                                metrics.incConsumed(t);

                                // store start nanos in exchange to stop later (no Timer.Sample)
                                long startNanos = metrics.startTimer();
                                ex.setProperty("metricsStartNanos", startNanos);
                                ex.setProperty("metricsTopic", t);
                            })
                            .process(CamelKafkaOrchestratorRoute.this::handleMessage)
                            .process(ex -> {
                                String t = (String) ex.getProperty("metricsTopic");
                                Long startNanos = (Long) ex.getProperty("metricsStartNanos");

                                if (t != null && startNanos != null) {
                                    metrics.stopTimer(t, startNanos);
                                }
                                if (t != null) {
                                    metrics.incSuccess(t);
                                }
                            })
                            .process(CamelKafkaOrchestratorRoute.this::manualCommitIfPresent);
                }
            });
        }
    }


    private void handleMessage(Exchange ex) throws Exception {
        String topic = ex.getIn().getHeader(KafkaConstants.TOPIC, String.class);
        Integer partition = ex.getIn().getHeader(KafkaConstants.PARTITION, Integer.class);
        Long offset = ex.getIn().getHeader(KafkaConstants.OFFSET, Long.class);
        String key = ex.getIn().getHeader(KafkaConstants.KEY, String.class);
        String value = ex.getIn().getBody(String.class);

        RawKafkaMessage msg = new RawKafkaMessage(
                topic,
                partition == null ? 0 : partition,
                offset == null ? -1 : offset,
                key,
                value
        );

        handler.handle(msg);
    }

    private void buildDlqExchange(Exchange ex, String sourceTopic) {
        String dlqTopic = sourceTopic + dlqSuffix;

        Exception cause = ex.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        String error = (cause == null) ? "UNKNOWN" : cause.getClass().getName() + ": " + cause.getMessage();

        String payload = ex.getIn().getBody(String.class);
        String safePayload = payload == null ? "" : payload;

        String dlqBody =
                "{"
                        + "\"sourceTopic\":\"" + safeJson(sourceTopic) + "\","
                        + "\"dlqTopic\":\"" + safeJson(dlqTopic) + "\","
                        + "\"partition\":" + ex.getIn().getHeader(KafkaConstants.PARTITION, 0) + ","
                        + "\"offset\":" + ex.getIn().getHeader(KafkaConstants.OFFSET, -1) + ","
                        + "\"error\":\"" + safeJson(error) + "\","
                        + "\"payload\":" + toJsonString(safePayload)
                        + "}";

        ex.getIn().setHeader("dlqTopic", dlqTopic);
        ex.getIn().setBody(dlqBody);
    }

    private void manualCommitIfPresent(Exchange ex) {
        KafkaManualCommit manual =
                ex.getIn().getHeader(KafkaConstants.MANUAL_COMMIT, KafkaManualCommit.class);

        if (manual != null) {
            // Camel Kafka manual commit API (Camel 4): commit()
            manual.commit();
        }
    }

    private String safeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String toJsonString(String raw) {
        return "\"" + safeJson(raw) + "\"";
    }

}