package com.meraki.meraki_normal_sync.core.extract.transform;

import java.math.BigDecimal;

public class ValueTransformers {
    private ValueTransformers() {}

    public static Object apply(String transform, String raw) {
        if (raw == null) return null;
        if (transform == null || transform.isBlank()) return raw;

        return switch (transform) {
            case "trim" -> raw.trim();
            case "upper" -> raw.trim().toUpperCase();
            case "lower" -> raw.trim().toLowerCase();
            case "toDecimal" -> new BigDecimal(raw.trim());
            default -> raw;
        };
    }
}
