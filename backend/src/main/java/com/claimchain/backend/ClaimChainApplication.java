package com.claimchain.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ClaimChainApplication {

	public static void main(String[] args) {
		SpringApplication.run(ClaimChainApplication.class, args);
	}

}
