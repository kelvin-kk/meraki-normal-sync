package com.meraki.meraki_normal_sync.core.model;

import java.util.ArrayList;
import java.util.List;

public class TableMapping {
    private String name;
    private String sourceTable;
    private final List<TargetMapping> targets = new ArrayList<>();

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSourceTable() { return sourceTable; }
    public void setSourceTable(String sourceTable) { this.sourceTable = sourceTable; }

    public List<TargetMapping> getTargets() { return targets; }
}
