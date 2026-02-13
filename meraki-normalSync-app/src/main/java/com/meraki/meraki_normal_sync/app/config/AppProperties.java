package com.meraki.meraki_normal_sync.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {
    /**
     * External folder containing mapping XML files (hot-changeable).
     * Example: /opt/normal-sync/mappings
     */
    private String mappingsDir = "./mappings";

    /**
     * If true, we store (topic,partition,offset) to prevent reprocessing.
     */
    private boolean enableIdempotency = true;

    public String getMappingsDir() { return mappingsDir; }
    public void setMappingsDir(String mappingsDir) { this.mappingsDir = mappingsDir; }

    public boolean isEnableIdempotency() { return enableIdempotency; }
    public void setEnableIdempotency(boolean enableIdempotency) { this.enableIdempotency = enableIdempotency; }
}
