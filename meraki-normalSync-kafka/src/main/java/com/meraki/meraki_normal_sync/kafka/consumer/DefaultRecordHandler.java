package com.meraki.meraki_normal_sync.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meraki.meraki_normal_sync.core.extract.T24XmlExtractor;
import com.meraki.meraki_normal_sync.core.mapping.MappingLoader;
import com.meraki.meraki_normal_sync.db.idempotency.IdempotencyService;
import com.meraki.meraki_normal_sync.db.writer.OracleWriter;

public class DefaultRecordHandler implements RecordHandler{
    private final MappingLoader mappingLoader;
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
        this.mappingLoader = mappingLoader;
        this.extractor = extractor;
        this.writer = writer;
        this.idempotency = idempotency;
        this.enableIdempotency = enableIdempotency;
        this.objectMapper = objectMapper;
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
        var mappingOpt = mappingLoader.findBySourceTable(env.table);
        if (mappingOpt.isEmpty()) {
            throw new IllegalStateException("No mapping file for source table: " + env.table);
        }

        String op = (env.op_type == null) ? "" : env.op_type.trim().toUpperCase();

        // 3) Handle deletes (for now skip)
        if ("D".equals(op)) {
            if (enableIdempotency) idempotency.markProcessed(msg.topic(), msg.partition(), msg.offset());
            return;
        }

        String xml = (env.after == null) ? null : env.after.XMLRECORD;

        if (xml == null || xml.isBlank()) {
            // You can decide to ignore or DLQ; we DLQ by throwing
            throw new IllegalStateException("Missing after.XMLRECORD for table=" + env.table + " op_type=" + op);
        }

        var mapping = mappingOpt.get();

        // 4) For each destination table in mapping: extract values + MERGE
        for (var target : mapping.getTargets()) {
            var values = extractor.extract(xml, target.getFields());
            var pkCols = target.primaryKeys();

            // validate PK values exist
            for (var pk : pkCols) {
                if (!values.containsKey(pk) || values.get(pk) == null) {
                    throw new IllegalStateException("Missing PK [" + pk + "] for table " + target.getDestTable());
                }
            }

            writer.merge(target.getDestTable(), pkCols, values);
        }

        if (enableIdempotency) {
            idempotency.markProcessed(msg.topic(), msg.partition(), msg.offset());
        }
    }

}
