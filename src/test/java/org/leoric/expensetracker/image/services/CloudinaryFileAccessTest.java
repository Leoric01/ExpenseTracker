package org.leoric.expensetracker.image.services;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import org.junit.jupiter.api.Test;
import org.leoric.expensetracker.config.CloudinaryConfig.CloudinaryProperties;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CloudinaryFileAccessTest {
	@Test
	void validatesAccountAndResourceTypeBeforeDeleting() throws Exception {
		var properties = new CloudinaryProperties();
		properties.setCloudName("example");
		properties.setApiKey("key");
		properties.setApiSecret("secret");
		var cloud = mock(Cloudinary.class);
		var uploader = mock(Uploader.class);
		when(cloud.uploader()).thenReturn(uploader);
		when(uploader.destroy(anyString(), any(Map.class))).thenReturn(Map.of("result", "ok"));
		var access = new CloudinaryFileAccess(cloud, properties);
		assertThat(access.delete("https://res.cloudinary.com/example/image/upload/v12/expense-tracker/todo/photo.png")).isTrue();
		verify(uploader).destroy(eq("expense-tracker/todo/photo"), argThat(options -> "image".equals(options.get("resource_type"))));
		assertThat(access.delete("https://res.cloudinary.com/example/raw/upload/v12/expense-tracker/attachments/receipt.pdf")).isTrue();
		verify(uploader).destroy(eq("expense-tracker/attachments/receipt.pdf"), argThat(options -> "raw".equals(options.get("resource_type"))));
		assertThat(access.sameFile("https://res.cloudinary.com/example/image/upload/v12/expense-tracker/todo/photo.png",
				"https://res.cloudinary.com/example/image/upload/v13/expense-tracker/todo/photo.jpg")).isTrue();
		assertThatThrownBy(() -> access.delete("https://res.cloudinary.com/other/image/upload/expense-tracker/todo/photo.png"))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> access.download("https://res.cloudinary.com.evil.example/example/image/upload/expense-tracker/todo/photo.png"))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> access.download("http://127.0.0.1/example/image/upload/expense-tracker/todo/photo.png"))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
