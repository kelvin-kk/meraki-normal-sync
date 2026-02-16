package com.meraki.meraki_normal_sync.db.sql;

import com.meraki.meraki_normal_sync.core.model.FieldMapping;
import com.meraki.meraki_normal_sync.core.model.TableMapping;

import java.util.*;
import java.util.stream.Collectors;

public class TypedOracleMergeBuilder {

    /**
     * Cache signature so SQL changes when datatype/length/precision/scale changes.
     */
    public String typeSignature(TableMapping mapping, List<String> cols) {
        Map<String, FieldMapping> byCol = byDestColumn(mapping);

        // Important: TIME_STAMP is never bound; exclude from signature to avoid useless cache misses
        List<String> effectiveCols = cols.stream()
                .filter(c -> !"TIME_STAMP".equalsIgnoreCase(c))
                .toList();

        return effectiveCols.stream()
                .map(c -> {
                    FieldMapping f = byCol.get(c);
                    String dt = (f == null) ? "UNKNOWN" : nvl(f.dataTypeUpper(), "UNKNOWN");
                    return c + ":" + dt
                            + "(" + nvlInt(f == null ? null : f.getLength())
                            + "," + nvlInt(f == null ? null : f.getPrecision())
                            + "," + nvlInt(f == null ? null : f.getScale()) + ")";
                })
                .collect(Collectors.joining("|"));
    }

    /**
     * Option B:
     * - INSERT: do NOT include TIME_STAMP -> DB default SYSTIMESTAMP applies
     * - UPDATE: always set TIME_STAMP = SYSTIMESTAMP
     */
    public String buildMerge(TableMapping mapping, List<String> cols) {
        String table = req(mapping.destTable(), "destTable");

        List<String> pkCols = mapping.fields().stream()
                .filter(FieldMapping::isPrimaryKey)
                .map(FieldMapping::destColumnUpper)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();

        if (pkCols.isEmpty()) {
            throw new IllegalStateException("No primary key columns defined in mapping for destTable=" + table);
        }

        Map<String, FieldMapping> byCol = byDestColumn(mapping);

        // TIME_STAMP should never be bound; exclude from USING/INSERT lists
        List<String> effectiveCols = cols.stream()
                .filter(c -> !"TIME_STAMP".equalsIgnoreCase(c))
                .toList();

        // USING (SELECT CAST(:COL AS TYPE) COL, ... FROM dual) s
        String usingSelect = effectiveCols.stream()
                .map(col -> castExpr(col, byCol.get(col)) + " " + col)
                .collect(Collectors.joining(", "));

        // ON (t.PK = s.PK AND ...)
        String on = pkCols.stream()
                .map(pk -> "t." + pk + " = s." + pk)
                .collect(Collectors.joining(" AND "));

        // UPDATE SET (exclude PKs) + always update TIME_STAMP
        Set<String> pkSet = pkCols.stream().map(s -> s.toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
        List<String> updateCols = effectiveCols.stream()
                .filter(c -> !pkSet.contains(c.toUpperCase(Locale.ROOT)))
                .toList();

        // always update TIME_STAMP
        List<String> updateAssignments = new ArrayList<>();
        for (String c : updateCols) {
            updateAssignments.add("t." + c + " = s." + c);
        }
        updateAssignments.add("t.TIME_STAMP = SYSTIMESTAMP");

        String updateSet = updateAssignments.stream().collect(Collectors.joining(", "));

        // INSERT columns/values (exclude TIME_STAMP so DB default applies)
        String insertCols = String.join(", ", effectiveCols);
        String insertVals = effectiveCols.stream().map(c -> "s." + c).collect(Collectors.joining(", "));

        StringBuilder sql = new StringBuilder();
        sql.append("MERGE INTO ").append(table).append(" t\n")
                .append("USING (SELECT ").append(usingSelect).append(" FROM dual) s\n")
                .append("ON (").append(on).append(")\n")
                .append("WHEN MATCHED THEN UPDATE SET ").append(updateSet).append("\n")
                .append("WHEN NOT MATCHED THEN INSERT (")
                .append(insertCols)
                .append(")\nVALUES (")
                .append(insertVals)
                .append(")");

        return sql.toString();
    }

    // ---------------- helpers ----------------

    private Map<String, FieldMapping> byDestColumn(TableMapping mapping) {
        Map<String, FieldMapping> map = new HashMap<>();
        for (FieldMapping f : mapping.fields()) {
            String col = f.destColumnUpper();
            if (col != null) map.put(col, f);
        }
        return map;
    }

    /**
     * Returns SQL expression that forces Oracle to know the bind type.
     * Uses named params like :ID, :VERSION_NUMBER etc.
     */
    private String castExpr(String col, FieldMapping f) {
        String p = ":" + col;

        // if mapping missing datatype -> safest fallback to VARCHAR2(4000)
        String dt = (f == null) ? null : f.dataTypeUpper();
        if (dt == null || dt.isBlank()) {
            return "CAST(" + p + " AS VARCHAR2(4000))";
        }

        return switch (dt) {
            case "VARCHAR2" -> "CAST(" + p + " AS VARCHAR2(" + reqLen(f, col) + "))";
            case "NUMBER" -> buildNumberCast(p, f);
            case "CLOB" -> "TO_CLOB(" + p + ")";
            case "TIMESTAMP" -> "CAST(" + p + " AS TIMESTAMP)";
            case "DATE" -> "CAST(" + p + " AS DATE)";
            default -> "CAST(" + p + " AS VARCHAR2(4000))";
        };
    }

    private String buildNumberCast(String p, FieldMapping f) {
        Integer prec = f.getPrecision();
        Integer scale = f.getScale();

        if (prec != null && scale != null) {
            return "CAST(" + p + " AS NUMBER(" + prec + "," + scale + "))";
        }
        if (prec != null) {
            return "CAST(" + p + " AS NUMBER(" + prec + "))";
        }
        return "CAST(" + p + " AS NUMBER)";
    }

    private int reqLen(FieldMapping f, String col) {
        Integer len = f.getLength();
        if (len == null || len <= 0) {
            throw new IllegalStateException("Missing <length> for VARCHAR2 column " + col);
        }
        return len;
    }

    private static String nvl(String v, String d) {
        return (v == null || v.isBlank()) ? d : v;
    }

    private static String nvlInt(Integer v) {
        return v == null ? "" : String.valueOf(v);
    }

    private static String req(String v, String name) {
        if (v == null || v.isBlank()) throw new IllegalStateException(name + " is required");
        return v.trim();
    }


}
