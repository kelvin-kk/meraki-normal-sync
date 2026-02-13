package com.meraki.meraki_normal_sync.core.model;

import java.util.ArrayList;
import java.util.List;

public class TargetMapping {
    private String destTable;
    private final List<FieldMapping> fields = new ArrayList<>();

    public String getDestTable() { return destTable; }
    public void setDestTable(String destTable) { this.destTable = destTable; }

    public List<FieldMapping> getFields() { return fields; }

    public List<String> primaryKeys() {
        return fields.stream()
                .filter(FieldMapping::isPrimaryKey)
                .map(FieldMapping::getDestColumn)
                .toList();
    }
}
