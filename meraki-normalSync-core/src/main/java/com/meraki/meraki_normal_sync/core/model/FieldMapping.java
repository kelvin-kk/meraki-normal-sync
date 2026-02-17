package com.meraki.meraki_normal_sync.core.model;

import java.util.Locale;
import java.util.Objects;

public class FieldMapping {

    private String fieldName;
    private int fieldNumber;
    private int fieldPos;

    private String destColumn;
    private boolean primaryKey;
    private String dataType;     // VARCHAR2, NUMBER, CLOB, DATE, TIMESTAMP...
    private Integer length;      // for VARCHAR2
    private Integer precision;   // for NUMBER(p,s)
    private Integer scale;// for NUMBER(p,s)
    private boolean multiValue;

    private String transform;    // optional

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

    public Integer getLength() { return length; }
    public void setLength(Integer length) { this.length = length; }

    public Integer getPrecision() { return precision; }
    public void setPrecision(Integer precision) { this.precision = precision; }

    public Integer getScale() { return scale; }
    public void setScale(Integer scale) { this.scale = scale; }

    public String getTransform() { return transform; }
    public void setTransform(String transform) { this.transform = transform; }

    public String destColumnUpper() {
        return destColumn == null ? null : destColumn.trim().toUpperCase(Locale.ROOT);
    }

    public String dataTypeUpper() {
        return dataType == null ? null : dataType.trim().toUpperCase(Locale.ROOT);
    }

    public void validateBasic() {
        Objects.requireNonNull(fieldName, "fieldName is required");
        Objects.requireNonNull(destColumn, "destColumn is required");
        // fieldNumber/fieldPos are validated by loader rules (RECID uses -1/-1)
    }

    public boolean isMultiValue() { return multiValue; }
    public void setMultiValue(boolean multiValue) { this.multiValue = multiValue; }

}
