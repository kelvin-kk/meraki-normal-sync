package com.meraki.meraki_normal_sync.kafka.consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

public class OggEnvelope {
    public String table;          // e.g. T24.FBNK_CUSTOMER
    public String op_type;        // I/U/D
    public String op_ts;
    public String current_ts;
    public String pos;
    public List<String> primary_keys;
    public Map<String, Object> tokens;
    public Row before;
    public Row after;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Row {
        public String RECID;
        public String XMLRECORD;
    }
}
