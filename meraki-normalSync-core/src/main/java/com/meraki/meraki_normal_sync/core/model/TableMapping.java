package com.meraki.meraki_normal_sync.core.model;

import java.util.ArrayList;
import java.util.List;

public record TableMapping(String sourceTable,
                           String destTable,
                           List<FieldMapping> fields){}
