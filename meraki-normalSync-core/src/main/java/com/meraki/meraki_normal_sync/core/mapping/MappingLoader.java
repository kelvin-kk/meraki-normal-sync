package com.meraki.meraki_normal_sync.core.mapping;

import com.meraki.meraki_normal_sync.core.model.TableMapping;

import java.util.Map;
import java.util.Optional;

public interface MappingLoader {
    Map<String, TableMapping> loadAll();
}
