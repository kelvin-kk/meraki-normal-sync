package com.meraki.meraki_normal_sync.db.config;

import com.meraki.meraki_normal_sync.db.sql.SqlCache;
import com.meraki.meraki_normal_sync.db.sql.TypedOracleMergeBuilder;
import com.meraki.meraki_normal_sync.db.writer.OracleWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class DbAutoConfiguration {
    @Bean
    public SqlCache sqlCache() {
        return new SqlCache();
    }

    @Bean
    public TypedOracleMergeBuilder typedOracleMergeBuilder() {
        return new TypedOracleMergeBuilder();
    }

    @Bean
    public OracleWriter oracleWriter(NamedParameterJdbcTemplate jdbc,
                                     TypedOracleMergeBuilder builder,
                                     SqlCache cache) {
        return new OracleWriter(jdbc, builder, cache);
    }
}
