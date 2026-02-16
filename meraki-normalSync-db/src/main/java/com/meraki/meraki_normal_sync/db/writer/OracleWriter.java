package com.meraki.meraki_normal_sync.db.writer;

import com.meraki.meraki_normal_sync.core.model.FieldMapping;
import com.meraki.meraki_normal_sync.core.model.TableMapping;
import com.meraki.meraki_normal_sync.db.sql.SqlCache;
import com.meraki.meraki_normal_sync.db.sql.TypedOracleMergeBuilder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.Types;
import java.util.*;
import java.util.stream.Collectors;

public class OracleWriter {
    private final NamedParameterJdbcTemplate jdbc;
    private final TypedOracleMergeBuilder mergeBuilder;
    private final SqlCache sqlCache;

    public OracleWriter(NamedParameterJdbcTemplate jdbc,
                        TypedOracleMergeBuilder mergeBuilder,
                        SqlCache sqlCache) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.mergeBuilder = Objects.requireNonNull(mergeBuilder, "mergeBuilder");
        this.sqlCache = Objects.requireNonNull(sqlCache, "sqlCache");
    }

    public int merge(TableMapping mapping, Map<String, Object> values) {
        if (mapping == null) throw new IllegalArgumentException("mapping is required");
        if (values == null || values.isEmpty()) return 0;

        // Build destColumn -> FieldMapping for typing + validation
        Map<String, FieldMapping> byCol = mapping.fields().stream()
                .filter(Objects::nonNull)
                .filter(f -> f.destColumnUpper() != null)
                .collect(Collectors.toMap(
                        FieldMapping::destColumnUpper,
                        f -> f,
                        (a, b) -> a, // keep first if duplicates
                        LinkedHashMap::new
                ));

        // Normalize keys to UPPERCASE + drop unknown columns
        Map<String, Object> norm = new HashMap<>();
        for (Map.Entry<String, Object> e : values.entrySet()) {
            if (e.getKey() == null) continue;
            String k = e.getKey().trim().toUpperCase(Locale.ROOT);
            if (byCol.containsKey(k)) {
                norm.put(k, e.getValue());
            }
        }

        // IMPORTANT: Let DB default SYSTIMESTAMP work (do not bind it)
        norm.remove("TIME_STAMP");

        if (norm.isEmpty()) return 0;

        // PK columns from mapping
        List<String> pkCols = mapping.fields().stream()
                .filter(FieldMapping::isPrimaryKey)
                .map(FieldMapping::destColumnUpper)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();

        if (pkCols.isEmpty()) {
            throw new IllegalStateException("No PK columns defined (isPrimaryKey=true) for destTable=" + mapping.destTable());
        }

        // Validate PK values exist and are not blank
        for (String pk : pkCols) {
            Object v = norm.get(pk);
            if (v == null) {
                throw new IllegalStateException("Missing PK value for [" + pk + "] destTable=" + mapping.destTable());
            }
            if (v instanceof String s && s.isBlank()) {
                throw new IllegalStateException("Blank PK value for [" + pk + "] destTable=" + mapping.destTable());
            }
        }

        // Stable column ordering for cache + SQL generation
        List<String> cols = new ArrayList<>(norm.keySet());
        cols.sort(String::compareTo);

        // Cache key must include types (you already do this ✅)
        String typeSig = mergeBuilder.typeSignature(mapping, cols);

        String cacheKey = mapping.destTable().trim().toUpperCase(Locale.ROOT)
                + "|PK=" + String.join(",", pkCols)
                + "|COLS=" + String.join(",", cols)
                + "|TYPES=" + typeSig;

        String sql = sqlCache.getOrPut(cacheKey, () -> mergeBuilder.buildMerge(mapping, cols));

        // Build typed parameter source to avoid ORA-17023 and driver guessing issues
        MapSqlParameterSource ps = new MapSqlParameterSource();
        for (String c : cols) {
            FieldMapping fm = byCol.get(c);
            Object v = norm.get(c);

            // If mapping doesn't specify datatype, fall back to default binding
            Integer sqlType = (fm == null) ? null : toSqlType(fm.dataTypeUpper());

            if (sqlType == null) {
                ps.addValue(c, v);
            } else {
                // When null, providing sqlType is critical for Oracle JDBC
                ps.addValue(c, v, sqlType);
            }
        }

        return jdbc.update(sql, ps);
    }

    /**
     * Maps Oracle type names in mapping to java.sql.Types for binding.
     * Keep it conservative: only map what we explicitly handle.
     */
    private Integer toSqlType(String oracleTypeUpper) {
        if (oracleTypeUpper == null || oracleTypeUpper.isBlank()) return null;

        // normalize like VARCHAR2(40) -> VARCHAR2
        String t = oracleTypeUpper.trim();
        int idx = t.indexOf('(');
        if (idx > 0) t = t.substring(0, idx).trim();

        return switch (t) {
            case "VARCHAR2", "VARCHAR", "CHAR", "NCHAR", "NVARCHAR2" -> Types.VARCHAR;
            case "NUMBER", "INTEGER", "FLOAT", "DECIMAL" -> Types.NUMERIC;
            case "DATE" -> Types.DATE;
            case "TIMESTAMP" -> Types.TIMESTAMP;
            case "CLOB", "NCLOB" -> Types.CLOB;
            case "BLOB" -> Types.BLOB;
            default -> null; // unknown type -> let driver bind normally
        };
    }
}
