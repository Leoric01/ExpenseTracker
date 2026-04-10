package org.leoric.expensetracker.exchangerate.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(MarketDataProperties.class)
public class MarketDataConfig {

	@Bean
	public RestClient frankfurterRestClient(MarketDataProperties props) {
		return RestClient.builder()
				.baseUrl(props.getFrankfurter().getBaseUrl())
				.build();
	}

	@Bean
	public RestClient coinGeckoRestClient(MarketDataProperties props) {
		var builder = RestClient.builder()
				.baseUrl(props.getCoingecko().getBaseUrl());

		if (props.getCoingecko().getApiKey() != null && !props.getCoingecko().getApiKey().isBlank()) {
			builder.defaultHeader("x-cg-demo-api-key", props.getCoingecko().getApiKey());
		}

		return builder.build();
	}
}