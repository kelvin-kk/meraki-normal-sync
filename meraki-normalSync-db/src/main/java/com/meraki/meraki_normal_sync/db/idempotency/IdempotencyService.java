package com.meraki.meraki_normal_sync.db.idempotency;

import org.springframework.jdbc.core.JdbcTemplate;

public class IdempotencyService {
    private final JdbcTemplate jdbc;

    public IdempotencyService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean alreadyProcessed(String topic, int partition, long offset) {
        Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(1) FROM KNOBS_INGEST_IDEMPOTENCY WHERE TOPIC=? AND PARTITION_NO=? AND OFFSET_NO=?",
                Integer.class, topic, partition, offset
        );
        return cnt != null && cnt > 0;
    }

    public void markProcessed(String topic, int partition, long offset) {
        jdbc.update(
                "INSERT INTO KNOBS_INGEST_IDEMPOTENCY(TOPIC, PARTITION_NO, OFFSET_NO) VALUES(?,?,?)",
                topic, partition, offset
        );
    }
}
