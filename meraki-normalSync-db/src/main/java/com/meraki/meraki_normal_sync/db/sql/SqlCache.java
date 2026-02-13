package com.meraki.meraki_normal_sync.db.sql;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class SqlCache {
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String getOrPut(String key, Supplier<String> supplier) {
        return cache.computeIfAbsent(key, k -> supplier.get());
    }
}
