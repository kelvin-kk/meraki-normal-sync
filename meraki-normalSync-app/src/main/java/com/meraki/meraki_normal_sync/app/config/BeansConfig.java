package com.meraki.meraki_normal_sync.app.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meraki.meraki_normal_sync.app.extract.DefaultT24XmlExtractor;
import com.meraki.meraki_normal_sync.core.extract.T24XmlExtractor;
import com.meraki.meraki_normal_sync.core.mapping.MappingLoader;
import com.meraki.meraki_normal_sync.core.mapping.XmlMappingLoader;
import com.meraki.meraki_normal_sync.db.idempotency.IdempotencyService;
import com.meraki.meraki_normal_sync.db.writer.OracleWriter;
import com.meraki.meraki_normal_sync.kafka.consumer.DefaultRecordHandler;
import com.meraki.meraki_normal_sync.kafka.consumer.KafkaIngestionListener;
import com.meraki.meraki_normal_sync.kafka.consumer.RecordHandler;
import com.meraki.meraki_normal_sync.kafka.dlq.DlqPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.file.Path;

@Configuration
public class BeansConfig {

    @Bean
    public String[] topics(@Value("${kafka.topics}") String topicsCsv) {
        return topicsCsv.split("\\s*,\\s*");
    }

    @Bean
    public MappingLoader mappingLoader(AppProperties props) {
        String dir = props.getMappingsDir().trim();

        Path basePath = dir.startsWith("file:")
                ? Path.of(java.net.URI.create(dir))
                : Path.of(dir);

        var loader = new XmlMappingLoader(basePath);
        loader.loadAll();
        return loader;
    }

    @Bean
    public T24XmlExtractor t24XmlExtractor() {
        return new DefaultT24XmlExtractor();
    }

    @Bean
    public OracleWriter oracleWriter(NamedParameterJdbcTemplate jdbc) {
        return new OracleWriter(jdbc);
    }

    @Bean
    public IdempotencyService idempotencyService(JdbcTemplate jdbc) {
        return new IdempotencyService(jdbc);
    }

    @Bean
    public DlqPublisher dlqPublisher(KafkaTemplate<String, String> template,
                                     @Value("${kafka.dlqSuffix:.DLQ}") String suffix) {
        return new DlqPublisher(template, suffix);
    }

    @Bean
    public RecordHandler recordHandler(
            MappingLoader mappingLoader,
            T24XmlExtractor extractor,
            OracleWriter writer,
            IdempotencyService idem,
            AppProperties props,
            ObjectMapper objectMapper
    ) {
        return new DefaultRecordHandler(
                mappingLoader,
                extractor,
                writer,
                idem,
                props.isEnableIdempotency(),
                objectMapper
        );
    }

    @Bean
    public KafkaIngestionListener kafkaIngestionListener(RecordHandler handler, DlqPublisher dlq) {
        return new KafkaIngestionListener(handler, dlq);
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}