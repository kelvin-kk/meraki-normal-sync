package com.meraki;

import com.meraki.meraki_normal_sync.app.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.kafka.annotation.EnableKafka;

@EnableKafka
@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class MerakiNormalSyncApplication {

	public static void main(String[] args) {
		SpringApplication.run(MerakiNormalSyncApplication.class, args);
	}

}
