package org.leoric.expensetracker.config;

import com.cloudinary.Cloudinary;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class CloudinaryConfig {

	@Bean
	@ConfigurationProperties(prefix = "cloudinary")
	public CloudinaryProperties cloudinaryProperties() {
		return new CloudinaryProperties();
	}

	@Bean
	public Cloudinary cloudinary(CloudinaryProperties props) {
		return new Cloudinary(Map.of(
				"cloud_name", props.getCloudName() != null ? props.getCloudName() : "",
				"api_key", props.getApiKey() != null ? props.getApiKey() : "",
				"api_secret", props.getApiSecret() != null ? props.getApiSecret() : "",
				"secure", true
		));
	}

	@Getter
	@Setter
	public static class CloudinaryProperties {
		private String cloudName;
		private String apiKey;
		private String apiSecret;
	}
}