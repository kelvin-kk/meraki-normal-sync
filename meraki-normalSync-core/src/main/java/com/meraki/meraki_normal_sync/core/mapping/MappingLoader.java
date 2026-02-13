package com.meraki.meraki_normal_sync.core.mapping;

import com.meraki.meraki_normal_sync.core.model.TableMapping;

import java.util.Optional;

public interface MappingLoader {
    void loadAll();
    Optional<TableMapping> findBySourceTable(String sourceTable);
}
