package com.meraki.meraki_normal_sync.db.writer;

import com.meraki.meraki_normal_sync.db.sql.SqlBuilder;
import com.meraki.meraki_normal_sync.db.sql.SqlCache;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class OracleWriter {
    private final NamedParameterJdbcTemplate jdbc;
    private final SqlBuilder sqlBuilder = new SqlBuilder();
    private final SqlCache sqlCache = new SqlCache();

    public OracleWriter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void merge(String table, List<String> pkCols, Map<String, Object> values) {
        if (values == null || values.isEmpty()) return;

        // stable ordering for cacheKey + SQL generation
        List<String> cols = new ArrayList<>(values.keySet());
        cols.sort(String::compareTo);

        String key = sqlBuilder.cacheKey(table, pkCols, cols);
        String sql = sqlCache.getOrPut(key, () -> sqlBuilder.buildMerge(table, pkCols, cols));

        jdbc.update(sql, values);
    }
}
