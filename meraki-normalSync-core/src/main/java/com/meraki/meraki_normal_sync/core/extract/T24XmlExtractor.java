package com.meraki.meraki_normal_sync.core.extract;

import com.meraki.meraki_normal_sync.core.model.FieldMapping;

import java.util.Map;

public interface T24XmlExtractor {
    Map<String, Object> extract(String xmlRecord, Iterable<FieldMapping> mappings);
}
