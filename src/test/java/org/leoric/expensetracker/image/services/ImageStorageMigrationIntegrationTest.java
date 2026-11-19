package org.leoric.expensetracker.image.services;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.leoric.expensetracker.auth.repositories.RoleRepository;
import org.leoric.expensetracker.auth.repositories.UserRepository;
import org.leoric.expensetracker.asset.models.Asset;
import org.leoric.expensetracker.asset.models.constants.AssetType;
import org.leoric.expensetracker.asset.models.constants.MarketDataSource;
import org.leoric.expensetracker.asset.repositories.AssetRepository;
import org.leoric.expensetracker.expensetracker.repositories.ExpenseTrackerRepository;
import org.leoric.expensetracker.image.repositories.ImageStorageSettingRepository;
import org.leoric.expensetracker.image.repositories.PendingImageCleanupRepository;
import org.leoric.expensetracker.transaction.models.Transaction;
import org.leoric.expensetracker.transaction.models.TransactionAttachment;
import org.leoric.expensetracker.transaction.models.constants.TransactionStatus;
import org.leoric.expensetracker.transaction.models.constants.TransactionType;
import org.leoric.expensetracker.transaction.repositories.TransactionAttachmentRepository;
import org.leoric.expensetracker.transaction.repositories.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:imageMigration;MODE=MariaDB;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ImageStorageMigrationIntegrationTest {
	private static final Path DIRECTORY = createDirectory();
	private static final byte[] PNG = Base64.getDecoder().decode(
			"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/l9sAAAAASUVORK5CYII=");
	private static final String CLOUD_URL = "https://res.cloudinary.com/test/image/upload/v123/expense-tracker/workout/fake.png";
	private static final String PLAN = """
			{"title":"Workout","weekLabel":"Current","days":[{"id":"monday","label":"Po","name":"Push",
			"focus":"Body","exercises":[{"id":"pushups","name":"Pushups","sets":"2"}]}]}
			""";

	@Autowired private MockMvc mvc;
	@Autowired private ImageStorageSettingRepository settings;
	@Autowired private PendingImageCleanupRepository cleanup;
	@Autowired private ImageUrlResolver resolver;
	@Autowired private UserRepository users;
	@Autowired private RoleRepository roles;
	@Autowired private ExpenseTrackerRepository trackers;
	@Autowired private TransactionRepository transactions;
	@Autowired private TransactionAttachmentRepository attachments;
	@Autowired private LocalFileStorage local;
	@Autowired private AssetRepository assets;
	@MockitoBean private Cloudinary cloudinary;
	@MockitoBean private CloudinaryFileAccess cloud;
	private Uploader uploader;
	private String token;
	private UUID trackerId;
	private String email;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("app.image-storage.local-directory", () -> DIRECTORY.toString());
	}

	private static Path createDirectory() {
		try {
			return Files.createTempDirectory("image-migration-test-");
		} catch (IOException e) {
			throw new ExceptionInInitializerError(e);
		}
	}

	@AfterAll
	static void removeDirectory() throws IOException {
		try (var paths = Files.walk(DIRECTORY)) {
			for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
		}
	}

	@BeforeEach
	void setup() throws Exception {
		settings.deleteAll();
		cleanup.deleteAll();
		uploader = mock(Uploader.class);
		when(cloudinary.uploader()).thenReturn(uploader);
		when(uploader.upload(any(byte[].class), any(Map.class))).thenReturn(Map.of("secure_url", CLOUD_URL));
		when(cloud.isConfigured()).thenReturn(true);
		when(cloud.download(CLOUD_URL)).thenReturn(new StoredFileData("image/png", PNG));
		when(cloud.delete(CLOUD_URL)).thenReturn(true);
		email = UUID.randomUUID() + "@test.com";
		mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
						.content("{\"firstName\":\"Test\",\"lastName\":\"Storage\",\"email\":\"" + email + "\",\"password\":\"password123\"}"))
				.andExpect(status().isCreated());
		var admin = users.findByEmail(email).orElseThrow();
		admin.setRoles(new java.util.ArrayList<>(java.util.List.of(roles.findByName("ADMIN").orElseThrow())));
		users.saveAndFlush(admin);
		MvcResult login = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
				.andExpect(status().isOk()).andReturn();
		token = field(login, "token");
		MvcResult tracker = mvc.perform(post("/api/expense-trackers").header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Storage tracker\"}"))
				.andExpect(status().isCreated()).andReturn();
		trackerId = UUID.fromString(field(tracker, "id"));
	}

	@Test
	void migratesVacationAndVisitPhotosBothWays() throws Exception {
		String base = "/api/vacations/" + trackerId;
		String placeId = field(mvc.perform(post(base + "/places").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Ship\",\"kind\":\"Ship\",\"recommendation\":\"NORMAL\"}"))
				.andExpect(status().isCreated()).andReturn(), "id");
		String id = field(mvc.perform(post(base).header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Cruise\",\"status\":\"COMPLETED\",\"focuses\":[],\"recommendation\":\"NORMAL\",\"ratings\":{}}"))
				.andExpect(status().isCreated()).andReturn(), "id");
		String vacation = base + "/" + id;
		String visitId = field(mvc.perform(post(vacation + "/visits").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"placeId\":\"" + placeId + "\",\"ratings\":{}}"))
				.andExpect(status().isCreated()).andReturn(), "id");
		String first = field(mvc.perform(multipart(vacation + "/photos")
				.file(new MockMultipartFile("file", "a.png", "image/png", PNG)).header("Authorization", "Bearer " + token))
				.andExpect(status().isCreated()).andReturn(), "imageUrl");
		String second = field(mvc.perform(multipart(vacation + "/photos?visitId=" + visitId)
				.file(new MockMultipartFile("file", "b.png", "image/png", PNG)).header("Authorization", "Bearer " + token))
				.andExpect(status().isCreated()).andReturn(), "imageUrl");
		String cloudTrip = "https://res.cloudinary.com/test/image/upload/v123/expense-tracker/vacations/one.png";
		String cloudVisit = "https://res.cloudinary.com/test/image/upload/v123/expense-tracker/vacations/two.png";
		when(uploader.upload(any(byte[].class), any(Map.class))).thenReturn(Map.of("secure_url", cloudTrip), Map.of("secure_url", cloudVisit));
		when(cloud.download(cloudTrip)).thenReturn(new StoredFileData("image/png", PNG));
		when(cloud.download(cloudVisit)).thenReturn(new StoredFileData("image/png", PNG));
		when(cloud.delete(cloudTrip)).thenReturn(true);
		when(cloud.delete(cloudVisit)).thenReturn(true);
		change("CLOUDINARY", true).andExpect(status().isOk()).andExpect(jsonPath("$.migrated", is(2)));
		mvc.perform(get(first)).andExpect(status().isNotFound());
		mvc.perform(get(second)).andExpect(status().isNotFound());
		String migratedDetail = mvc.perform(get(vacation).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(migratedDetail).contains(cloudTrip, cloudVisit);
		change("LOCAL", true).andExpect(status().isOk()).andExpect(jsonPath("$.migrated", is(2)));
		MvcResult detail = mvc.perform(get(vacation).header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andReturn();
		String localUrl = field(detail, "imageUrl");
		assertThat(localUrl).startsWith("/images/expense-tracker/vacations/");
		mvc.perform(get(localUrl)).andExpect(status().isOk()).andExpect(content().bytes(PNG));
		verify(cloud).delete(cloudTrip);
		verify(cloud).delete(cloudVisit);
	}

	@Test
	void migratesBothRestaurantAndVisitItemPhotosInBothDirections() throws Exception {
		if (!assets.existsByCodeIgnoreCase("CZK")) {
			assets.saveAndFlush(Asset.builder().code("CZK").name("Czech koruna").assetType(AssetType.FIAT)
					.scale(2).marketDataSource(MarketDataSource.NONE).build());
		}
		String base = "/api/restaurants/" + trackerId;
		String restaurantId = field(mvc.perform(post(base).header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Bistro\",\"recommendation\":\"NORMAL\",\"services\":[]}"))
				.andExpect(status().isCreated()).andReturn(), "id");
		String restaurant = base + "/" + restaurantId;
		String dishId = field(mvc.perform(post(restaurant + "/dishes").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Meal\",\"recommendation\":\"NORMAL\"}"))
				.andExpect(status().isCreated()).andReturn(), "id");
		String visitId = field(mvc.perform(post(restaurant + "/visits").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"currencyCode\":\"CZK\"}"))
				.andExpect(status().isCreated()).andReturn(), "id");
		String itemId = field(mvc.perform(post(restaurant + "/visits/" + visitId + "/items")
				.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"dishId\":\"" + dishId + "\"}")).andExpect(status().isCreated()).andReturn(), "id");
		String first = field(mvc.perform(multipart(restaurant + "/photos")
				.file(new MockMultipartFile("file", "a.png", "image/png", PNG)).header("Authorization", "Bearer " + token))
				.andExpect(status().isCreated()).andReturn(), "imageUrl");
		String second = field(mvc.perform(multipart(restaurant + "/visits/" + visitId + "/items/" + itemId + "/photos")
				.file(new MockMultipartFile("file", "b.png", "image/png", PNG)).header("Authorization", "Bearer " + token))
				.andExpect(status().isCreated()).andReturn(), "imageUrl");
		String cloudRestaurant = "https://res.cloudinary.com/test/image/upload/v123/expense-tracker/restaurants/one.png";
		String cloudItem = "https://res.cloudinary.com/test/image/upload/v123/expense-tracker/restaurants/two.png";
		when(uploader.upload(any(byte[].class), any(Map.class)))
				.thenReturn(Map.of("secure_url", cloudRestaurant), Map.of("secure_url", cloudItem));
		when(cloud.download(cloudRestaurant)).thenReturn(new StoredFileData("image/png", PNG));
		when(cloud.download(cloudItem)).thenReturn(new StoredFileData("image/png", PNG));
		when(cloud.delete(cloudRestaurant)).thenReturn(true);
		when(cloud.delete(cloudItem)).thenReturn(true);
		change("CLOUDINARY", true).andExpect(status().isOk()).andExpect(jsonPath("$.migrated", is(2)));
		mvc.perform(get(first)).andExpect(status().isNotFound());
		mvc.perform(get(second)).andExpect(status().isNotFound());
		mvc.perform(get(restaurant).header("Authorization", "Bearer " + token))
				.andExpect(jsonPath("$.restaurant.photos[0].imageUrl", is(cloudRestaurant)))
				.andExpect(jsonPath("$.visits[0].items[0].photos[0].imageUrl", is(cloudItem)));
		change("LOCAL", true).andExpect(status().isOk()).andExpect(jsonPath("$.migrated", is(2)));
		MvcResult detail = mvc.perform(get(restaurant).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andReturn();
		String localUrl = field(detail, "imageUrl");
		assertThat(localUrl).startsWith("/images/expense-tracker/restaurants/");
		mvc.perform(get(localUrl)).andExpect(status().isOk()).andExpect(content().bytes(PNG));
		verify(cloud).delete(cloudRestaurant);
		verify(cloud).delete(cloudItem);
	}

	@Test
	void switchesWithoutMigrationAndMovesSharedReferencesBothWaysWithRetry() throws Exception {
		String localUrl = uploadWorkoutImage();
		String todoBody = "{\"title\":\"Shared image\",\"imageUrl\":\"" + localUrl + "\"}";
		MvcResult todo = mvc.perform(post("/api/todo/{trackerId}", trackerId)
						.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(todoBody))
				.andExpect(status().isCreated()).andReturn();
		String todoId = field(todo, "id");
		change("CLOUDINARY", false).andExpect(status().isOk())
				.andExpect(jsonPath("$.provider", is("CLOUDINARY"))).andExpect(jsonPath("$.localFiles", is(1)));
		mvc.perform(get(localUrl)).andExpect(status().isOk());
		change("CLOUDINARY", true).andExpect(status().isOk())
				.andExpect(jsonPath("$.migrated", is(1))).andExpect(jsonPath("$.pendingCleanup", is(0)));
		verify(uploader, times(1)).upload(any(byte[].class), any(Map.class));
		mvc.perform(get(localUrl)).andExpect(status().isNotFound());
		mvc.perform(get("/api/todo/{trackerId}/{todoId}", trackerId, todoId)
						.header("Authorization", "Bearer " + token))
				.andExpect(jsonPath("$.imageUrl", is(CLOUD_URL)));
		mvc.perform(patch("/api/todo/{trackerId}/{todoId}", trackerId, todoId)
						.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(todoBody))
				.andExpect(status().isOk()).andExpect(jsonPath("$.imageUrl", is(CLOUD_URL)));

		when(cloud.delete(CLOUD_URL)).thenReturn(false);
		change("LOCAL", true).andExpect(status().isOk()).andExpect(jsonPath("$.migrated", is(1)))
				.andExpect(jsonPath("$.pendingCleanup", is(1))).andExpect(jsonPath("$.problems.length()", is(1)));
		MvcResult plan = mvc.perform(get("/api/workout/{trackerId}", trackerId)
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andReturn();
		String newLocalUrl = field(plan, "imageUrl");
		assertThat(newLocalUrl).isNotEqualTo(localUrl).startsWith("/images/");
		assertThat(resolver.resolve(localUrl)).isEqualTo(newLocalUrl);
		mvc.perform(get(newLocalUrl)).andExpect(status().isOk());
		when(cloud.delete(CLOUD_URL)).thenReturn(true);
		change("LOCAL", true).andExpect(status().isOk()).andExpect(jsonPath("$.pendingCleanup", is(0)));
	}

	@Test
	void failedUploadLeavesSourceAndReferencesIntact() throws Exception {
		String localUrl = uploadWorkoutImage();
		when(uploader.upload(any(byte[].class), any(Map.class))).thenThrow(new IOException("Cloud unavailable"));
		change("CLOUDINARY", true).andExpect(status().isOk())
				.andExpect(jsonPath("$.migrated", is(0))).andExpect(jsonPath("$.problems.length()", is(1)));
		mvc.perform(get(localUrl)).andExpect(status().isOk());
		mvc.perform(get("/api/workout/{trackerId}", trackerId).header("Authorization", "Bearer " + token))
				.andExpect(jsonPath("$.days[0].exercises[0].imageUrl", is(localUrl)));
		assertThat(cleanup.countByCleanedFalse()).isZero();
	}

	@Test
	void retainsCloudinaryAssetWhenAnotherVersionFailedToMigrate() throws Exception {
		String otherUrl = CLOUD_URL.replace("v123", "v124");
		when(cloud.sameFile(CLOUD_URL, otherUrl)).thenReturn(true);
		when(cloud.download(otherUrl)).thenThrow(new IllegalStateException("File unavailable"));
		for (String[] item : new String[][]{{"First", CLOUD_URL}, {"Second", otherUrl}}) {
			mvc.perform(post("/api/todo/{trackerId}", trackerId)
							.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
							.content("{\"title\":\"" + item[0] + "\",\"imageUrl\":\"" + item[1] + "\"}"))
					.andExpect(status().isCreated());
		}
		change("LOCAL", true).andExpect(status().isOk()).andExpect(jsonPath("$.migrated", is(1)))
				.andExpect(jsonPath("$.pendingCleanup", is(1))).andExpect(jsonPath("$.problems.length()", is(2)));
		verify(cloud, never()).delete(CLOUD_URL);
	}

	@Test
	void requiresAuthenticationAndRejectsCloudWithoutCredentials() throws Exception {
		mvc.perform(get("/profile/image-storage")).andExpect(status().isUnauthorized());
		mvc.perform(put("/profile/image-storage").contentType(MediaType.APPLICATION_JSON)
						.content("{\"provider\":\"LOCAL\",\"migrate\":false}"))
				.andExpect(status().isUnauthorized());
		when(cloud.isConfigured()).thenReturn(false);
		change("CLOUDINARY", false).andExpect(status().isBadRequest());
		mvc.perform(get("/profile/image-storage").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.provider", is("LOCAL")));
	}

	@Test
	void onlyAdministratorsCanChangeGlobalStorage() throws Exception {
		var user = users.findByEmail(email).orElseThrow();
		user.setRoles(new java.util.ArrayList<>(java.util.List.of(roles.findByName("USER").orElseThrow())));
		users.saveAndFlush(user);
		mvc.perform(get("/profile/image-storage").header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden());
		change("LOCAL", false).andExpect(status().isForbidden());
	}

	@Test
	void movesPdfTransactionAttachmentsBothWays() throws Exception {
		byte[] pdf = "%PDF-1.4 test".getBytes();
		String oldUrl = local.save(new MockMultipartFile("file", "receipt.pdf", "application/pdf", pdf),
				"expense-tracker/attachments", false);
		var transaction = transactions.saveAndFlush(Transaction.builder()
				.expenseTracker(trackers.findById(trackerId).orElseThrow())
				.transactionType(TransactionType.EXPENSE).status(TransactionStatus.COMPLETED)
				.currencyCode("CZK").transactionDate(java.time.Instant.now()).build());
		var attachment = attachments.saveAndFlush(TransactionAttachment.builder().transaction(transaction)
				.fileName("receipt.pdf").fileUrl(oldUrl).contentType("application/pdf").fileSize(pdf.length).build());
		String cloudPdf = "https://res.cloudinary.com/test/raw/upload/v1/expense-tracker/attachments/receipt.pdf";
		when(uploader.upload(any(byte[].class), any(Map.class))).thenReturn(Map.of("secure_url", cloudPdf));
		when(cloud.download(cloudPdf)).thenReturn(new StoredFileData("application/pdf", pdf));
		when(cloud.delete(cloudPdf)).thenReturn(true);
		change("CLOUDINARY", true).andExpect(status().isOk()).andExpect(jsonPath("$.migrated", is(1)));
		assertThat(attachments.findById(attachment.getId()).orElseThrow().getFileUrl()).isEqualTo(cloudPdf);
		mvc.perform(get(oldUrl)).andExpect(status().isNotFound());
		change("LOCAL", true).andExpect(status().isOk()).andExpect(jsonPath("$.migrated", is(1)));
		String newUrl = attachments.findById(attachment.getId()).orElseThrow().getFileUrl();
		mvc.perform(get(newUrl)).andExpect(status().isOk())
				.andExpect(content().contentType("application/pdf"))
				.andExpect(content().bytes(pdf));
		verify(cloud).delete(cloudPdf);
	}

	private String uploadWorkoutImage() throws Exception {
		mvc.perform(put("/api/workout/{trackerId}", trackerId).header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON).content(PLAN)).andExpect(status().isOk());
		MvcResult upload = mvc.perform(multipart("/api/workout/{trackerId}/days/monday/exercises/pushups/image", trackerId)
						.file(new MockMultipartFile("file", "x.png", "image/png", PNG))
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andReturn();
		return field(upload, "imageUrl");
	}

	private org.springframework.test.web.servlet.ResultActions change(String provider, boolean migrate) throws Exception {
		return mvc.perform(put("/profile/image-storage").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"provider\":\"" + provider + "\",\"migrate\":" + migrate + "}"));
	}

	private String field(MvcResult result, String name) throws Exception {
		var matcher = Pattern.compile("\"" + name + "\"\\s*:\\s*\"([^\"]+)\"")
				.matcher(result.getResponse().getContentAsString());
		if (!matcher.find()) throw new IllegalStateException("Missing response field: " + name);
		return matcher.group(1);
	}
}
