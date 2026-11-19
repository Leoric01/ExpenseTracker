package org.leoric.expensetracker.image.services;

import com.cloudinary.Cloudinary;
import com.cloudinary.api.exceptions.NotFound;
import com.cloudinary.utils.ObjectUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.leoric.expensetracker.image.models.ImageStorageProvider;
import org.leoric.expensetracker.auth.repositories.RoleRepository;
import org.leoric.expensetracker.auth.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named = "RUN_REAL_CLOUDINARY_TEST", matches = "true")
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:realCloudVacation;MODE=MariaDB;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class RealCloudinaryVacationIntegrationTest {
	private static final Path DIRECTORY = directory();
	private static final byte[] PNG = Base64.getDecoder().decode(
			"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/l9sAAAAASUVORK5CYII=");
	@Autowired MockMvc mvc;
	@Autowired Cloudinary cloudinary;
	@Autowired CloudinaryFileAccess cloud;
	@Autowired UserRepository users;
	@Autowired RoleRepository roles;
	@MockitoSpyBean ImageServiceImpl images;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("app.image-storage.local-directory", () -> DIRECTORY.toString());
		registry.add("cloudinary.cloud-name", () -> System.getenv("CLOUDINARY_CLOUD_NAME"));
		registry.add("cloudinary.api-key", () -> System.getenv("CLOUDINARY_API_KEY"));
		registry.add("cloudinary.api-secret", () -> System.getenv("CLOUDINARY_API_SECRET"));
	}

	private static Path directory() {
		try { return Files.createTempDirectory("real-cloud-vacation-test-"); }
		catch (java.io.IOException e) { throw new ExceptionInInitializerError(e); }
	}

	@AfterAll
	static void removeDirectory() throws Exception {
		try (var files = Files.walk(DIRECTORY)) {
			for (var file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
		}
	}

	@Test
	void uploadsReplacesAndMigratesOnlySyntheticPhotos() throws Exception {
		assertThat(cloud.isConfigured()).isTrue();
		List<String> uploaded = new ArrayList<>();
		doAnswer(invocation -> {
			if (invocation.getArgument(3) == ImageStorageProvider.CLOUDINARY && uploaded.size() >= 3)
				throw new IllegalStateException("Test upload limit reached");
			String url = (String) invocation.callRealMethod();
			if (invocation.getArgument(3) == ImageStorageProvider.CLOUDINARY) uploaded.add(url);
			return url;
		}).when(images).uploadTo(any(org.springframework.web.multipart.MultipartFile.class), anyString(), anyBoolean(), any());
		try {
			String email = UUID.randomUUID() + "@test.invalid";
			mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
					.content("{\"firstName\":\"Test\",\"lastName\":\"Cloud\",\"email\":\"" + email + "\",\"password\":\"password123\"}"))
					.andExpect(status().isCreated());
			var admin = users.findByEmail(email).orElseThrow();
			admin.setRoles(new ArrayList<>(List.of(roles.findByName("ADMIN").orElseThrow())));
			users.saveAndFlush(admin);
			String token = field(mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
					.content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
					.andExpect(status().isOk()).andReturn(), "token");
			String tracker = field(mvc.perform(post("/api/expense-trackers").header("Authorization", "Bearer " + token)
					.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Isolated cloud test\"}"))
					.andExpect(status().isCreated()).andReturn(), "id");
			String base = "/api/vacations/" + tracker;
			String place = field(mvc.perform(post(base + "/places").header("Authorization", "Bearer " + token)
					.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Test ship\",\"kind\":\"Ship\",\"recommendation\":\"NORMAL\"}"))
					.andExpect(status().isCreated()).andReturn(), "id");
			String vacation = field(mvc.perform(post(base).header("Authorization", "Bearer " + token)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"name\":\"Test vacation\",\"status\":\"PLANNED\",\"focuses\":[],\"recommendation\":\"NORMAL\",\"ratings\":{}}"))
					.andExpect(status().isCreated()).andReturn(), "id");
			String trip = base + "/" + vacation;
			String visit = field(mvc.perform(post(trip + "/visits").header("Authorization", "Bearer " + token)
					.contentType(MediaType.APPLICATION_JSON).content("{\"placeId\":\"" + place + "\",\"ratings\":{}}"))
					.andExpect(status().isCreated()).andReturn(), "id");
			String localUrl = field(mvc.perform(multipart(trip + "/photos").file(image()).header("Authorization", "Bearer " + token))
					.andExpect(status().isCreated()).andReturn(), "imageUrl");
			change(token, "CLOUDINARY", true).andExpect(status().isOk()).andExpect(jsonPath("$.migrated", is(1)))
					.andExpect(jsonPath("$.pendingCleanup", is(0))).andExpect(jsonPath("$.problems.length()", is(0)));
			assertThat(uploaded).hasSize(1);
			assertThat(cloud.download(uploaded.getFirst()).contentType()).isEqualTo("image/png");
			mvc.perform(get(localUrl)).andExpect(status().isNotFound());
			var photo = mvc.perform(multipart(trip + "/photos?visitId=" + visit).file(image())
					.header("Authorization", "Bearer " + token)).andExpect(status().isCreated()).andReturn();
			String photoId = field(photo, "id");
			assertThat(uploaded).hasSize(2);
			assertThat(cloud.download(field(photo, "imageUrl")).bytes()).isNotEmpty();
			var replacement = mvc.perform(multipart(trip + "/photos?visitId=" + visit).file(image())
					.header("Authorization", "Bearer " + token)).andExpect(status().isCreated()).andReturn();
			assertThat(uploaded).hasSize(3);
			String replacementUrl = field(replacement, "imageUrl");
			assertThat(replacementUrl).isNotEqualTo(field(photo, "imageUrl"));
			mvc.perform(delete(trip + "/photos/" + photoId).header("Authorization", "Bearer " + token))
					.andExpect(status().isOk()).andExpect(jsonPath("$.pendingCleanup", is(0)));
			assertAbsent(uploaded.get(1));
			assertThat(cloud.download(replacementUrl).bytes()).isNotEmpty();
			change(token, "LOCAL", true).andExpect(status().isOk()).andExpect(jsonPath("$.migrated", is(2)))
					.andExpect(jsonPath("$.pendingCleanup", is(0))).andExpect(jsonPath("$.problems.length()", is(0)));
			assertAbsent(uploaded.getFirst());
			assertAbsent(replacementUrl);
			String newLocal = field(mvc.perform(get(trip).header("Authorization", "Bearer " + token))
					.andExpect(status().isOk()).andReturn(), "imageUrl");
			mvc.perform(get(newLocal)).andExpect(status().isOk());
		} finally {
			for (String url : uploaded) {
				assertThat(cloud.delete(url)).as("test image cleanup").isTrue();
				assertAbsent(url);
			}
		}
	}

	private void assertAbsent(String url) {
		String publicId = url.replaceFirst("^.*/upload/(?:v\\d+/)?", "").replaceFirst("\\.[^.]+$", "");
		assertThatThrownBy(() -> cloudinary.api().resource(publicId, ObjectUtils.emptyMap())).isInstanceOf(NotFound.class);
	}
	private org.springframework.test.web.servlet.ResultActions change(String token, String provider, boolean migrate) throws Exception {
		return mvc.perform(put("/profile/image-storage").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"provider\":\"" + provider + "\",\"migrate\":" + migrate + "}"));
	}
	private MockMultipartFile image() { return new MockMultipartFile("file", "test.png", "image/png", PNG); }
	private String field(MvcResult result, String name) throws Exception {
		var matcher = Pattern.compile("\"" + name + "\"\\s*:\\s*\"([^\"]+)\"").matcher(result.getResponse().getContentAsString());
		if (!matcher.find()) throw new IllegalStateException("Missing response field: " + name);
		return matcher.group(1);
	}
}
