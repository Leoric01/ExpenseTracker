package org.leoric.expensetracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
		Jwt jwt,
		Cors cors
) {
	public record Jwt(
			String secretKey,
			long expirationMs
	) {
	}

	public record Cors(
			List<String> allowedOrigins,
			List<String> allowedHeaders,
			List<String> allowedMethods
	) {
		public Cors {
			allowedOrigins = allowedOrigins != null ? allowedOrigins : new ArrayList<>();
			allowedHeaders = allowedHeaders != null ? allowedHeaders : new ArrayList<>();
			allowedMethods = allowedMethods != null ? allowedMethods : new ArrayList<>();
		}
	}
}