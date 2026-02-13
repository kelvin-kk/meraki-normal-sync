package com.meraki.meraki_normal_sync.db.sql;

import java.util.List;
import java.util.stream.Collectors;

public class SqlBuilder {
    public String cacheKey(String table, List<String> pkCols, List<String> allCols) {
        return table + "|PK=" + String.join(",", pkCols) + "|COLS=" + String.join(",", allCols);
    }

    public String buildMerge(String table, List<String> pkCols, List<String> allCols) {
        if (pkCols == null || pkCols.isEmpty()) {
            throw new IllegalArgumentException("PK columns required for MERGE: " + table);
        }
        if (allCols == null || allCols.isEmpty()) {
            throw new IllegalArgumentException("No columns provided for MERGE: " + table);
        }

        // USING (SELECT :COL COL, :COL2 COL2 FROM dual)
        String usingSelect = allCols.stream()
                .map(c -> ":" + c + " " + c)
                .collect(Collectors.joining(", "));

        // ON (t.PK = s.PK AND ...)
        String onClause = pkCols.stream()
                .map(pk -> "t." + pk + " = s." + pk)
                .collect(Collectors.joining(" AND "));

        // UPDATE SET t.COL = s.COL (excluding PKs)
        List<String> updateCols = allCols.stream()
                .filter(c -> !pkCols.contains(c))
                .toList();

        String updateSet = updateCols.isEmpty()
                ? "t." + pkCols.get(0) + " = t." + pkCols.get(0) // no-op update if only PK exists
                : updateCols.stream().map(c -> "t." + c + " = s." + c).collect(Collectors.joining(", "));

        // INSERT (COLS...) VALUES (s.COLS...)
        String insertCols = String.join(", ", allCols);
        String insertVals = allCols.stream().map(c -> "s." + c).collect(Collectors.joining(", "));

        return """
            MERGE INTO %s t
            USING (SELECT %s FROM dual) s
            ON (%s)
            WHEN MATCHED THEN UPDATE SET %s
            WHEN NOT MATCHED THEN INSERT (%s)
            VALUES (%s)
            """.formatted(table, usingSelect, onClause, updateSet, insertCols, insertVals);
    }
}
