package com.meraki.meraki_normal_sync.observability.logging;

import org.slf4j.Logger;

import java.util.Map;
import java.util.StringJoiner;


public class IngestLog {
    private IngestLog() {}

    public static void info(Logger log, String message, Map<String, Object> fields) {
        log.info(format(message, fields));
    }

    public static void warn(Logger log, String message, Map<String, Object> fields) {
        log.warn(format(message, fields));
    }

    public static void error(Logger log, String message, Map<String, Object> fields, Throwable t) {
        log.error(format(message, fields), t);
    }

    private static String format(String message, Map<String, Object> fields) {
        if (fields == null || fields.isEmpty()) return message;

        StringJoiner sj = new StringJoiner(" ");
        sj.add(message);
        fields.forEach((k, v) -> sj.add(k + "=" + safe(v)));
        return sj.toString();
    }

    private static String safe(Object v) {
        if (v == null) return "null";
        String s = String.valueOf(v);
        // avoid huge logs
        return s.length() > 500 ? s.substring(0, 500) + "..." : s;
    }
}
