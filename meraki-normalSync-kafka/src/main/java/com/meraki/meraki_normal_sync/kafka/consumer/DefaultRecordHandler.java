package com.meraki.meraki_normal_sync.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meraki.meraki_normal_sync.core.extract.T24XmlExtractor;
import com.meraki.meraki_normal_sync.core.mapping.MappingLoader;
import com.meraki.meraki_normal_sync.core.model.FieldMapping;
import com.meraki.meraki_normal_sync.core.model.TableMapping;
import com.meraki.meraki_normal_sync.db.idempotency.IdempotencyService;
import com.meraki.meraki_normal_sync.db.writer.OracleWriter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultRecordHandler implements RecordHandler{
    private final Map<String, TableMapping> mappingsBySource; // cached at startup
    private final T24XmlExtractor extractor;
    private final OracleWriter writer;
    private final IdempotencyService idempotency;
    private final boolean enableIdempotency;
    private final ObjectMapper objectMapper;

    public DefaultRecordHandler(
            MappingLoader mappingLoader,
            T24XmlExtractor extractor,
            OracleWriter writer,
            IdempotencyService idempotency,
            boolean enableIdempotency,
            ObjectMapper objectMapper
    ) {
        this.extractor = extractor;
        this.writer = writer;
        this.idempotency = idempotency;
        this.enableIdempotency = enableIdempotency;
        this.objectMapper = objectMapper;

        // Load mappings once during startup
        Map<String, TableMapping> loaded = mappingLoader.loadAll();

        // Normalize keys (sourceTable) to UPPER for safer lookup
        Map<String, TableMapping> normalized = new ConcurrentHashMap<>();
        for (Map.Entry<String, TableMapping> e : loaded.entrySet()) {
            if (e.getKey() == null) continue;
            normalized.put(norm(e.getKey()), e.getValue());
        }
        this.mappingsBySource = Collections.unmodifiableMap(normalized);
    }

    @Override
    public void handle(RawKafkaMessage msg) throws Exception {

        if (enableIdempotency && idempotency.alreadyProcessed(msg.topic(), msg.partition(), msg.offset())) {
            return;
        }

        // 1) Parse OGG envelope
        OggEnvelope env = objectMapper.readValue(msg.value(), OggEnvelope.class);

        if (env.table == null || env.table.isBlank()) {
            throw new IllegalStateException("OGG payload missing 'table'");
        }

        // 2) Resolve mapping by env.table (e.g. T24.FBNK_CUSTOMER)
        TableMapping mapping = mappingsBySource.get(norm(env.table));
        if (mapping == null) {
            throw new IllegalStateException("No mapping file for source table: " + env.table);
        }

        String op = (env.op_type == null) ? "" : env.op_type.trim().toUpperCase(Locale.ROOT);

        // 3) Handle deletes (skip for now)
        if ("D".equals(op)) {
            if (enableIdempotency) idempotency.markProcessed(msg.topic(), msg.partition(), msg.offset());
            return;
        }

        String xml = (env.after == null) ? null : env.after.XMLRECORD;
        if (xml == null || xml.isBlank()) {
            throw new IllegalStateException("Missing after.XMLRECORD for table=" + env.table + " op_type=" + op);
        }

        // 4) Extract values using the mapping fields
        Map<String, Object> values = extractor.extract(xml, mapping.fields());

        // 5) Primary keys from mapping
        List<String> pkCols = mapping.fields().stream()
                .filter(FieldMapping::isPrimaryKey)
                .map(FieldMapping::destColumnUpper)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        // validate PK values exist
        for (String pk : pkCols) {
            Object v = values.get(pk);
            if (v == null || (v instanceof String s && s.isBlank())) {
                throw new IllegalStateException("Missing PK [" + pk + "] for table " + mapping.destTable());
            }
        }

        // 6) MERGE into destination table
        writer.merge(mapping, values);

        if (enableIdempotency) {
            idempotency.markProcessed(msg.topic(), msg.partition(), msg.offset());
        }
    }

    private String norm(String s) {
        return s == null ? null : s.trim().toUpperCase(Locale.ROOT);
    }

}
