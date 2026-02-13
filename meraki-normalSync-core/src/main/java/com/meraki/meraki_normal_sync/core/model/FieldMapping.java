package com.meraki.meraki_normal_sync.core.model;

public class FieldMapping {

    private String fieldName;
    private int fieldNumber;
    private int fieldPos;

    private String destColumn;
    private boolean primaryKey;
    private String dataType;
    private String transform;

    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }

    public int getFieldNumber() { return fieldNumber; }
    public void setFieldNumber(int fieldNumber) { this.fieldNumber = fieldNumber; }

    public int getFieldPos() { return fieldPos; }
    public void setFieldPos(int fieldPos) { this.fieldPos = fieldPos; }

    public String getDestColumn() { return destColumn; }
    public void setDestColumn(String destColumn) { this.destColumn = destColumn; }

    public boolean isPrimaryKey() { return primaryKey; }
    public void setPrimaryKey(boolean primaryKey) { this.primaryKey = primaryKey; }

    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }

    public String getTransform() { return transform; }
    public void setTransform(String transform) { this.transform = transform; }

}
