package org.leoric.expensetracker.restaurant;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.leoric.expensetracker.asset.models.Asset;
import org.leoric.expensetracker.asset.models.constants.AssetType;
import org.leoric.expensetracker.asset.models.constants.MarketDataSource;
import org.leoric.expensetracker.asset.repositories.AssetRepository;
import org.leoric.expensetracker.auth.repositories.RoleRepository;
import org.leoric.expensetracker.auth.repositories.UserRepository;
import org.leoric.expensetracker.auth.models.UserExpenseTrackerRole;
import org.leoric.expensetracker.expensetracker.repositories.ExpenseTrackerRepository;
import org.leoric.expensetracker.auth.repositories.UserExpenseTrackerRoleRepository;
import org.leoric.expensetracker.restaurant.services.RestaurantService;
import org.leoric.expensetracker.restaurant.repositories.RestaurantFileCleanupRepository;
import org.leoric.expensetracker.restaurant.repositories.RestaurantPhotoRepository;
import org.leoric.expensetracker.restaurant.models.RestaurantPhoto;
import org.leoric.expensetracker.image.services.ImageServiceImpl;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Comparator;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.is;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doCallRealMethod;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:restaurantTest;MODE=MariaDB;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class RestaurantControllerIntegrationTest {
	private static final Path DIRECTORY = createDirectory();
	private static final byte[] PNG = Base64.getDecoder().decode(
			"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/l9sAAAAASUVORK5CYII=");
	@Autowired private MockMvc mvc;
	@Autowired private UserRepository users;
	@Autowired private RoleRepository roles;
	@Autowired private AssetRepository assets;
	@Autowired private ExpenseTrackerRepository trackers;
	@Autowired private UserExpenseTrackerRoleRepository trackerRoles;
	@Autowired private RestaurantService restaurantService;
	@Autowired private RestaurantFileCleanupRepository cleanup;
	@Autowired private RestaurantPhotoRepository photos;
	@Autowired private TransactionTemplate transactions;
	@MockitoSpyBean private ImageServiceImpl imageService;
	private String token;
	private UUID trackerId;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("app.image-storage.local-directory", () -> DIRECTORY.toString());
	}
	private static Path createDirectory() {
		try { return Files.createTempDirectory("restaurant-test-"); }
		catch (java.io.IOException e) { throw new ExceptionInInitializerError(e); }
	}
	@AfterAll
	static void cleanup() throws Exception {
		try (var files = Files.walk(DIRECTORY)) {
			for (var file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
		}
	}
	@BeforeEach
	void setup() throws Exception {
		if (!assets.existsByCodeIgnoreCase("CZK")) {
			assets.saveAndFlush(Asset.builder().code("CZK").name("Czech koruna").assetType(AssetType.FIAT)
					.scale(2).marketDataSource(MarketDataSource.NONE).build());
		}
		token = login();
		var result = mvc.perform(post("/api/expense-trackers").header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Restaurant tracker\"}"))
				.andExpect(status().isCreated()).andReturn();
		trackerId = UUID.fromString(field(result, "id"));
	}

	@Test
	void recordsHistoryRatingsPhotosAndDeletesSafely() throws Exception {
		String base = "/api/restaurants/" + trackerId;
		var restaurant = mvc.perform(post(base).header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Bistro\",\"recommendation\":\"AVOID\",\"priceLevel\":\"HIGH\",\"services\":[\"Wolt\",\"Bolt\"]}"))
				.andExpect(status().isCreated()).andReturn();
		String id = field(restaurant, "id");
		var dish = mvc.perform(post(base + "/" + id + "/dishes").header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Burger\",\"recommendation\":\"AVOID\"}"))
				.andExpect(status().isCreated()).andReturn();
		String dishId = field(dish, "id");
		var first = createVisit(base, id, 0);
		String visitId = field(first, "id");
		var item = mvc.perform(post(base + "/" + id + "/visits/" + visitId + "/items")
						.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
						.content("{\"dishId\":\"" + dishId + "\",\"rating\":10,\"priceMinor\":19900}"))
				.andExpect(status().isCreated()).andReturn();
		String itemId = field(item, "id");
		createVisit(base, id, 8);
		mvc.perform(get(base + "/" + id).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.restaurant.averageRating", is(4.0)))
				.andExpect(jsonPath("$.restaurant.worstRating", is(0)))
				.andExpect(jsonPath("$.restaurant.ratingCount", is(2)));
		mvc.perform(get(base + "?search=Burger&minRating=4&maxRating=4").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements", is(1)));
		var upload = mvc.perform(multipart(base + "/" + id + "/visits/" + visitId + "/items/" + itemId + "/photos")
						.file(new MockMultipartFile("file", "x.png", "image/png", PNG))
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isCreated()).andReturn();
		String url = field(upload, "imageUrl");
		mvc.perform(get(url)).andExpect(status().isOk()).andExpect(content().bytes(PNG));
		mvc.perform(delete(base + "/" + id + "/dishes/" + dishId).header("Authorization", "Bearer " + token))
				.andExpect(status().isNoContent());
		mvc.perform(get(base + "/" + id).header("Authorization", "Bearer " + token))
				.andExpect(jsonPath("$.visits[1].items[0].name", is("Burger")));
		mvc.perform(delete(base + "/" + id).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.pendingCleanup", is(0)));
		mvc.perform(get(url)).andExpect(status().isNotFound());
	}

	@Test
	void deletingOwnerAccountRemovesRestaurantPhotos() throws Exception {
		String base = "/api/restaurants/" + trackerId;
		String memberEmail = UUID.randomUUID() + "@test.com";
		String memberToken = login(memberEmail);
		trackerRoles.saveAndFlush(UserExpenseTrackerRole.builder().user(users.findByEmail(memberEmail).orElseThrow())
				.expenseTracker(trackers.findById(trackerId).orElseThrow())
				.role(roles.findByName("EXPENSETRACKER_MEMBER").orElseThrow()).build());
		String restaurantId = field(mvc.perform(post(base).header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Account\",\"recommendation\":\"NORMAL\",\"services\":[]}"))
				.andExpect(status().isCreated()).andReturn(), "id");
		String url = field(mvc.perform(multipart(base + "/" + restaurantId + "/photos")
				.file(new MockMultipartFile("file", "a.png", "image/png", PNG))
				.header("Authorization", "Bearer " + token)).andExpect(status().isCreated()).andReturn(), "imageUrl");
		mvc.perform(delete("/profile/me").header("Authorization", "Bearer " + token))
				.andExpect(status().isNoContent()).andExpect(header().string("X-Restaurant-Pending-Cleanup", "0"));
		mvc.perform(get(url)).andExpect(status().isNotFound());
		mvc.perform(get("/profile/me").header("Authorization", "Bearer " + memberToken)).andExpect(status().isOk());
	}

	@Test
	void retainsFailedCleanupAndRetriesIt() throws Exception {
		String base = "/api/restaurants/" + trackerId;
		String restaurantId = field(mvc.perform(post(base).header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Retry\",\"recommendation\":\"NORMAL\",\"services\":[]}"))
				.andExpect(status().isCreated()).andReturn(), "id");
		String photoUrl = base + "/" + restaurantId + "/photos";
		MvcResult result = mvc.perform(multipart(photoUrl).file(new MockMultipartFile("file", "a.png", "image/png", PNG))
				.header("Authorization", "Bearer " + token)).andExpect(status().isCreated()).andReturn();
		String id = field(result, "id");
		String url = field(result, "imageUrl");
		doReturn(false).when(imageService).deleteImageByUrl(url);
		mvc.perform(delete(photoUrl + "/" + id).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.pendingCleanup", is(1)));
		mvc.perform(get(url)).andExpect(status().isOk());
		doCallRealMethod().when(imageService).deleteImageByUrl(url);
		mvc.perform(post(base + "/cleanup").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.pendingCleanup", is(0)));
		mvc.perform(get(url)).andExpect(status().isNotFound());
	}

	@Test
	void defersSharedPhotoCleanupUntilLastReferenceIsDeleted() throws Exception {
		String base = "/api/restaurants/" + trackerId;
		String restaurantId = field(mvc.perform(post(base).header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Shared\",\"recommendation\":\"NORMAL\",\"services\":[]}"))
				.andExpect(status().isCreated()).andReturn(), "id");
		String photoUrl = base + "/" + restaurantId + "/photos";
		MvcResult result = mvc.perform(multipart(photoUrl).file(new MockMultipartFile("file", "a.png", "image/png", PNG))
				.header("Authorization", "Bearer " + token)).andExpect(status().isCreated()).andReturn();
		String id = field(result, "id");
		String url = field(result, "imageUrl");
		RestaurantPhoto original = photos.findById(UUID.fromString(id)).orElseThrow();
		RestaurantPhoto shared = new RestaurantPhoto();
		shared.setRestaurant(original.getRestaurant());
		shared.setImageUrl(url);
		UUID sharedId = photos.saveAndFlush(shared).getId();
		mvc.perform(delete(photoUrl + "/" + id).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.pendingCleanup", is(1)));
		mvc.perform(get(url)).andExpect(status().isOk());
		mvc.perform(delete(photoUrl + "/" + sharedId).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.pendingCleanup", is(0)));
		mvc.perform(get(url)).andExpect(status().isNotFound());
	}

	@Test
	void memberCanEditAndForeignNestedIdsAreRejected() throws Exception {
		String base = "/api/restaurants/" + trackerId;
		String email = UUID.randomUUID() + "@test.com";
		String memberToken = login(email);
		trackerRoles.saveAndFlush(UserExpenseTrackerRole.builder().user(users.findByEmail(email).orElseThrow())
				.expenseTracker(trackers.findById(trackerId).orElseThrow())
				.role(roles.findByName("EXPENSETRACKER_MEMBER").orElseThrow()).build());
		String restaurantId = field(mvc.perform(post(base).header("Authorization", "Bearer " + memberToken)
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Member restaurant\",\"recommendation\":\"NORMAL\",\"services\":[]}"))
				.andExpect(status().isCreated()).andReturn(), "id");
		mvc.perform(delete(base + "/" + restaurantId).header("Authorization", "Bearer " + memberToken))
				.andExpect(status().isOk());
		String otherTracker = field(mvc.perform(post("/api/expense-trackers").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Other\"}"))
				.andExpect(status().isCreated()).andReturn(), "id");
		String otherRestaurant = field(mvc.perform(post("/api/restaurants/" + otherTracker).header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Other\",\"recommendation\":\"NORMAL\",\"services\":[]}"))
				.andExpect(status().isCreated()).andReturn(), "id");
		mvc.perform(get(base + "/" + otherRestaurant).header("Authorization", "Bearer " + token)).andExpect(status().isNotFound());
		mvc.perform(delete(base + "/" + otherRestaurant).header("Authorization", "Bearer " + token)).andExpect(status().isNotFound());
	}

	@Test
	void filtersAndPaginatesWithUnratedLast() throws Exception {
		String base = "/api/restaurants/" + trackerId;
		String rated = field(mvc.perform(post(base).header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Rated\",\"recommendation\":\"FAVORITE\",\"priceLevel\":\"LOW\",\"services\":[\"Wolt\"]}"))
				.andExpect(status().isCreated()).andReturn(), "id");
		mvc.perform(post(base).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Unrated\",\"recommendation\":\"NORMAL\",\"services\":[]}")).andExpect(status().isCreated());
		createVisit(base, rated, 0);
		mvc.perform(get(base + "?order=rating&size=1").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.content[0].name", is("Rated")))
				.andExpect(jsonPath("$.page.totalElements", is(2)));
		mvc.perform(get(base + "?order=rating&size=1&page=1").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.content[0].name", is("Unrated")));
		mvc.perform(get(base + "?recommendation=FAVORITE&priceLevel=LOW&serviceName=wolt&maxRating=0")
				.header("Authorization", "Bearer " + token)).andExpect(status().isOk())
				.andExpect(jsonPath("$.page.totalElements", is(1))).andExpect(jsonPath("$.content[0].worstRating", is(0)));
		mvc.perform(get(base + "?minRating=1").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements", is(0)));
	}

	@Test
	void removesUploadedFileAfterTransactionRollback() throws Exception {
		String base = "/api/restaurants/" + trackerId;
		UUID restaurantId = UUID.fromString(field(mvc.perform(post(base).header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Rollback\",\"recommendation\":\"NORMAL\",\"services\":[]}"))
				.andExpect(status().isCreated()).andReturn(), "id"));
		String[] url = new String[1];
		assertThatThrownBy(() -> transactions.executeWithoutResult(_ -> {
			url[0] = restaurantService.addPhoto(trackerId, restaurantId,
					new MockMultipartFile("file", "rollback.png", "image/png", PNG)).imageUrl();
			throw new IllegalStateException("rollback");
		})).hasMessage("rollback");
		assertThat(url[0]).isNotBlank();
		mvc.perform(get(url[0])).andExpect(status().isNotFound());
		assertThat(cleanup.count()).isZero();
	}

	@Test
	void retainsDeletedServiceInHistoryAndAllowsUpdatingVisit() throws Exception {
		String base = "/api/restaurants/" + trackerId;
		String tagId = field(mvc.perform(post(base + "/services").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Delivery\"}"))
				.andExpect(status().isCreated()).andReturn(), "id");
		String restaurantId = field(mvc.perform(post(base).header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Bistro\",\"recommendation\":\"NORMAL\",\"services\":[]}"))
				.andExpect(status().isCreated()).andReturn(), "id");
		String visitUrl = base + "/" + restaurantId + "/visits";
		String visitId = field(mvc.perform(post(visitUrl).header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"currencyCode\":\"CZK\",\"serviceName\":\"Delivery\"}"))
				.andExpect(status().isCreated()).andReturn(), "id");
		mvc.perform(delete(base + "/services/" + tagId).header("Authorization", "Bearer " + token))
				.andExpect(status().isNoContent());
		mvc.perform(put(visitUrl + "/" + visitId).header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"currencyCode\":\"CZK\",\"serviceName\":\"Delivery\",\"rating\":0}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.serviceName", is("Delivery")))
				.andExpect(jsonPath("$.rating", is(0)));
		mvc.perform(get(base + "/" + restaurantId).header("Authorization", "Bearer " + token))
				.andExpect(jsonPath("$.restaurant.services.length()", is(0)))
				.andExpect(jsonPath("$.visits[0].serviceName", is("Delivery")));
	}

	@Test
	void requiresTrackerAccessAndRejectsBadRatings() throws Exception {
		String base = "/api/restaurants/" + trackerId;
		mvc.perform(get(base)).andExpect(status().isUnauthorized());
		String other = login();
		mvc.perform(get(base).header("Authorization", "Bearer " + other)).andExpect(status().isForbidden());
		var created = mvc.perform(post(base).header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Bistro\",\"recommendation\":\"NORMAL\",\"services\":[]}"))
				.andExpect(status().isCreated()).andReturn();
		String id = field(created, "id");
		mvc.perform(post(base + "/" + id + "/visits").header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON).content("{\"currencyCode\":\"CZK\",\"rating\":11}"))
				.andExpect(status().isBadRequest());
	}

	private MvcResult createVisit(String base, String restaurantId, int rating) throws Exception {
		return mvc.perform(post(base + "/" + restaurantId + "/visits").header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"visitedOn\":\"2026-01-01\",\"currencyCode\":\"CZK\",\"rating\":" + rating + "}"))
				.andExpect(status().isCreated()).andReturn();
	}
	private String login() throws Exception {
		return login(UUID.randomUUID() + "@test.com");
	}
	private String login(String email) throws Exception {
		mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(
				"{\"firstName\":\"Test\",\"lastName\":\"Restaurant\",\"email\":\"" + email + "\",\"password\":\"password123\"}"))
				.andExpect(status().isCreated());
		var user = users.findByEmail(email).orElseThrow();
		user.setRoles(new java.util.ArrayList<>(java.util.List.of(roles.findByName("USER").orElseThrow())));
		users.saveAndFlush(user);
		return field(mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"password123\"}")).andExpect(status().isOk()).andReturn(), "token");
	}
	private String field(MvcResult result, String name) throws Exception {
		var matcher = Pattern.compile("\"" + name + "\"\\s*:\\s*\"([^\"]+)\"").matcher(result.getResponse().getContentAsString());
		if (!matcher.find()) throw new IllegalStateException("Missing response field: " + name);
		return matcher.group(1);
	}
}
