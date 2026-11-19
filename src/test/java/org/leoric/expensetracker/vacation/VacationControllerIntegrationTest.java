package org.leoric.expensetracker.vacation;

import org.junit.jupiter.api.*;
import org.leoric.expensetracker.asset.models.Asset;
import org.leoric.expensetracker.asset.models.constants.AssetType;
import org.leoric.expensetracker.asset.models.constants.MarketDataSource;
import org.leoric.expensetracker.asset.repositories.AssetRepository;
import org.leoric.expensetracker.auth.repositories.RoleRepository;
import org.leoric.expensetracker.auth.repositories.UserRepository;
import org.leoric.expensetracker.auth.models.UserExpenseTrackerRole;
import org.leoric.expensetracker.auth.repositories.UserExpenseTrackerRoleRepository;
import org.leoric.expensetracker.expensetracker.repositories.ExpenseTrackerRepository;
import org.leoric.expensetracker.image.services.ImageServiceImpl;
import org.leoric.expensetracker.vacation.services.VacationService;
import org.leoric.expensetracker.exchangerate.services.interfaces.ExchangeRateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.math.BigDecimal;
import static org.hamcrest.Matchers.is;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:vacationTest;MODE=MariaDB;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class VacationControllerIntegrationTest {
	private static final Path DIRECTORY = directory();
	private static final byte[] PNG = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/l9sAAAAASUVORK5CYII=");
	@Autowired MockMvc mvc;
	@Autowired UserRepository users;
	@Autowired RoleRepository roles;
	@Autowired AssetRepository assets;
	@Autowired ExpenseTrackerRepository trackers;
	@Autowired UserExpenseTrackerRoleRepository trackerRoles;
	@Autowired VacationService vacationService;
	@Autowired TransactionTemplate transactions;
	@MockitoSpyBean ImageServiceImpl imageService;
	@MockitoBean ExchangeRateService rates;
	private String token;
	private String trackerId;
	@DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
		registry.add("app.image-storage.local-directory", () -> DIRECTORY.toString());
		String mariadbUrl = System.getenv("VACATION_TEST_MARIADB_URL");
		if (mariadbUrl != null && !mariadbUrl.isBlank()) {
			registry.add("spring.datasource.url", () -> mariadbUrl);
			registry.add("spring.datasource.driver-class-name", () -> "org.mariadb.jdbc.Driver");
			registry.add("spring.datasource.username", () -> "vacation_test");
			registry.add("spring.datasource.password", () -> "vacation_test");
		}
	}
	private static Path directory() { try { return Files.createTempDirectory("vacation-test-"); } catch (java.io.IOException e) { throw new ExceptionInInitializerError(e); } }
	@AfterAll static void cleanup() throws Exception { try (var files = Files.walk(DIRECTORY)) { for (var path : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(path); } }
	@BeforeEach void setup() throws Exception {
		if (!assets.existsByCodeIgnoreCase("CZK")) assets.saveAndFlush(Asset.builder().code("CZK").name("Koruna")
				.assetType(AssetType.FIAT).scale(2).marketDataSource(MarketDataSource.NONE).build());
		if (!assets.existsByCodeIgnoreCase("EUR")) assets.saveAndFlush(Asset.builder().code("EUR").name("Euro")
				.assetType(AssetType.FIAT).scale(2).marketDataSource(MarketDataSource.NONE).build());
		String email = UUID.randomUUID() + "@test.com";
		mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("{\"firstName\":\"Test\",\"lastName\":\"Vacation\",\"email\":\""+email+"\",\"password\":\"password123\"}")).andExpect(status().isCreated());
		var user = users.findByEmail(email).orElseThrow(); user.setRoles(new ArrayList<>(List.of(roles.findByName("USER").orElseThrow()))); users.saveAndFlush(user);
		token = field(mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\""+email+"\",\"password\":\"password123\"}")).andExpect(status().isOk()).andReturn(), "token");
		trackerId = field(mvc.perform(post("/api/expense-trackers").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Vacation tracker\"}")).andExpect(status().isCreated()).andReturn(), "id");
	}
	@Test void retainsHistoryPhotosAndNullVersusZero() throws Exception {
		String base = "/api/vacations/" + trackerId;
		String placeId = field(mvc.perform(post(base+"/places").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Costa Toscana\",\"kind\":\"Loď\",\"recommendation\":\"NORMAL\"}")).andExpect(status().isCreated()).andReturn(),"id");
		String id = field(mvc.perform(post(base).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Plavba\",\"status\":\"COMPLETED\",\"focuses\":[\"Wellness\"],\"year\":2025,\"approximate\":true,\"rating\":0,\"recommendation\":\"FAVORITE\",\"ratings\":{\"Jídlo\":0}}"))
				.andExpect(status().isCreated()).andReturn(),"id");
		String visitId = field(mvc.perform(post(base+"/"+id+"/visits").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"placeId\":\""+placeId+"\",\"rating\":0,\"ratings\":{\"Kajuta\":8}}")).andExpect(status().isCreated()).andReturn(),"id");
		String url = field(mvc.perform(multipart(base+"/"+id+"/photos?visitId="+visitId).file(new MockMultipartFile("file","a.png","image/png",PNG))
				.header("Authorization","Bearer "+token)).andExpect(status().isCreated()).andReturn(),"imageUrl");
		mvc.perform(get(base+"/"+id).header("Authorization","Bearer "+token)).andExpect(status().isOk())
				.andExpect(jsonPath("$.vacation.rating",is(0))).andExpect(jsonPath("$.visits[0].ratings.Kajuta",is(8)));
		mvc.perform(delete(base+"/places/"+placeId).header("Authorization","Bearer "+token)).andExpect(status().isNoContent());
		mvc.perform(get(base+"/"+id).header("Authorization","Bearer "+token)).andExpect(jsonPath("$.visits[0].name",is("Costa Toscana")));
		mvc.perform(get(url)).andExpect(status().isOk());
		mvc.perform(delete(base+"/"+id).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.pendingCleanup",is(0)));
		mvc.perform(get(url)).andExpect(status().isNotFound());
	}
	@Test void validatesDatesAndRequiresAuthentication() throws Exception {
		String base="/api/vacations/"+trackerId;
		mvc.perform(get(base)).andExpect(status().isUnauthorized());
		mvc.perform(post(base).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Bad\",\"status\":\"COMPLETED\",\"focuses\":[],\"startDate\":\"2025-02-02\",\"endDate\":\"2025-02-01\",\"recommendation\":\"NORMAL\",\"ratings\":{}}"))
				.andExpect(status().isBadRequest());
	}
	@Test void filtersAndPaginatesVacationsWithUnratedLast() throws Exception {
		String base = "/api/vacations/" + trackerId;
		String rated = field(mvc.perform(post(base).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Plavba\",\"status\":\"COMPLETED\",\"type\":\"Plavba\",\"focuses\":[\"Wellness\"],\"year\":2025,\"country\":\"Itálie\",\"provider\":\"Costa\",\"rating\":0,\"recommendation\":\"FAVORITE\",\"ratings\":{}}"))
				.andExpect(status().isCreated()).andReturn(), "id");
		mvc.perform(post(base).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Nehodnocená\",\"status\":\"PLANNED\",\"focuses\":[],\"recommendation\":\"NORMAL\",\"ratings\":{}}"))
				.andExpect(status().isCreated());
		String placeId = field(mvc.perform(post(base + "/places").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Costa Toscana\",\"kind\":\"Loď\",\"recommendation\":\"NORMAL\"}"))
				.andExpect(status().isCreated()).andReturn(), "id");
		mvc.perform(post(base + "/" + rated + "/visits").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"placeId\":\"" + placeId + "\",\"ratings\":{}}")).andExpect(status().isCreated());
		mvc.perform(get(base + "?order=rating&size=1").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.content[0].name", is("Plavba")))
				.andExpect(jsonPath("$.page.totalElements", is(2)));
		mvc.perform(get(base + "?order=rating&size=1&page=1").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.content[0].name", is("Nehodnocená")));
		mvc.perform(get(base + "?search=Toscana&status=COMPLETED&type=plav&focus=wellness&country=it&provider=costa&year=2025&recommendation=FAVORITE&maxRating=0")
				.header("Authorization", "Bearer " + token)).andExpect(status().isOk())
				.andExpect(jsonPath("$.page.totalElements", is(1))).andExpect(jsonPath("$.content[0].name", is("Plavba")));
		mvc.perform(get(base + "?minRating=1").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements", is(0)));
	}
	@Test void filtersAndPaginatesPlacesUsingRatedVisitsOnly() throws Exception {
		String base = "/api/vacations/" + trackerId;
		String place = field(mvc.perform(post(base + "/places").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Costa\",\"kind\":\"Loď\",\"country\":\"Itálie\",\"location\":\"Středomoří\",\"recommendation\":\"FAVORITE\"}"))
				.andExpect(status().isCreated()).andReturn(), "id");
		mvc.perform(post(base + "/places").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Hotel\",\"kind\":\"Hotel\",\"country\":\"Česko\",\"recommendation\":\"AVOID\"}"))
				.andExpect(status().isCreated());
		String trip = field(mvc.perform(post(base).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Trip\",\"status\":\"COMPLETED\",\"focuses\":[],\"recommendation\":\"NORMAL\",\"ratings\":{}}"))
				.andExpect(status().isCreated()).andReturn(), "id");
		for (String rating : List.of("0", "10", "null")) {
			mvc.perform(post(base + "/" + trip + "/visits").header("Authorization", "Bearer " + token)
					.contentType(MediaType.APPLICATION_JSON).content("{\"placeId\":\"" + place + "\",\"rating\":" + rating + ",\"ratings\":{}}"))
					.andExpect(status().isCreated());
		}
		mvc.perform(get(base + "/places/search?order=rating&size=1").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements", is(2)))
				.andExpect(jsonPath("$.content[0].name", is("Costa"))).andExpect(jsonPath("$.content[0].averageRating", is(5.0)))
				.andExpect(jsonPath("$.content[0].ratingCount", is(2)));
		mvc.perform(get(base + "/places/search?order=rating&size=1&page=1").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.content[0].name", is("Hotel")))
				.andExpect(jsonPath("$.content[0].averageRating").isEmpty());
		mvc.perform(get(base + "/places/search?search=ost&kind=lo&area=st&recommendation=FAVORITE&minRating=5&maxRating=5")
				.header("Authorization", "Bearer " + token)).andExpect(status().isOk())
				.andExpect(jsonPath("$.page.totalElements", is(1))).andExpect(jsonPath("$.content[0].id", is(place)));
		mvc.perform(get(base + "/places/search?minRating=6").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements", is(0)));
		mvc.perform(get(base + "/places/search?minRating=7&maxRating=3").header("Authorization", "Bearer " + token))
				.andExpect(status().isBadRequest());
	}
	@Test void membersCanEditButForeignTrackersAndNestedIdsAreRejected() throws Exception {
		String email = UUID.randomUUID() + "@test.com";
		mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"firstName\":\"Test\",\"lastName\":\"Member\",\"email\":\"" + email + "\",\"password\":\"password123\"}"))
				.andExpect(status().isCreated());
		var member = users.findByEmail(email).orElseThrow();
		member.setRoles(new ArrayList<>(List.of(roles.findByName("USER").orElseThrow())));
		users.saveAndFlush(member);
		String memberToken = field(mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
				.andExpect(status().isOk()).andReturn(), "token");
		String base = "/api/vacations/" + trackerId;
		mvc.perform(get(base).header("Authorization", "Bearer " + memberToken)).andExpect(status().isForbidden());
		mvc.perform(get(base + "/places/search").header("Authorization", "Bearer " + memberToken)).andExpect(status().isForbidden());
		trackerRoles.saveAndFlush(UserExpenseTrackerRole.builder().user(users.findByEmail(email).orElseThrow())
				.expenseTracker(trackers.findById(UUID.fromString(trackerId)).orElseThrow())
				.role(roles.findByName("EXPENSETRACKER_MEMBER").orElseThrow()).build());
		mvc.perform(get(base + "/places/search").header("Authorization", "Bearer " + memberToken)).andExpect(status().isOk());
		String input = "{\"name\":\"Trip\",\"status\":\"PLANNED\",\"focuses\":[],\"recommendation\":\"NORMAL\",\"ratings\":{}}";
		String id = field(mvc.perform(post(base).header("Authorization", "Bearer " + memberToken).contentType(MediaType.APPLICATION_JSON)
				.content(input)).andExpect(status().isCreated()).andReturn(), "id");
		mvc.perform(put(base + "/" + id).header("Authorization", "Bearer " + memberToken).contentType(MediaType.APPLICATION_JSON)
				.content(input)).andExpect(status().isOk());
		String otherTracker = field(mvc.perform(post("/api/expense-trackers").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Other\"}")).andExpect(status().isCreated()).andReturn(), "id");
		String otherBase = "/api/vacations/" + otherTracker;
		String otherId = field(mvc.perform(post(otherBase).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
				.content(input)).andExpect(status().isCreated()).andReturn(), "id");
		String otherPlace = field(mvc.perform(post(otherBase + "/places").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Other\",\"kind\":\"Hotel\",\"recommendation\":\"NORMAL\"}"))
				.andExpect(status().isCreated()).andReturn(), "id");
		mvc.perform(get(base + "/" + otherId).header("Authorization", "Bearer " + token)).andExpect(status().isNotFound());
		mvc.perform(post(base + "/" + id + "/visits").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"placeId\":\"" + otherPlace + "\",\"ratings\":{}}")).andExpect(status().isNotFound());
		mvc.perform(delete(base + "/" + id).header("Authorization", "Bearer " + memberToken)).andExpect(status().isOk());
	}
	@Test void deletingOwnerRemovesVacationPhotos() throws Exception {
		String base = "/api/vacations/" + trackerId;
		String id = field(mvc.perform(post(base).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Trip\",\"status\":\"PLANNED\",\"focuses\":[],\"recommendation\":\"NORMAL\",\"ratings\":{}}"))
				.andExpect(status().isCreated()).andReturn(), "id");
		String url = field(mvc.perform(multipart(base + "/" + id + "/photos")
				.file(new MockMultipartFile("file", "a.png", "image/png", PNG)).header("Authorization", "Bearer " + token))
				.andExpect(status().isCreated()).andReturn(), "imageUrl");
		mvc.perform(delete("/profile/me").header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
		mvc.perform(get(url)).andExpect(status().isNotFound());
	}
	@Test void failedPhotoCleanupCanBeRetriedAndRollbackRemovesUpload() throws Exception {
		String base = "/api/vacations/" + trackerId;
		String id = field(mvc.perform(post(base).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Trip\",\"status\":\"PLANNED\",\"focuses\":[],\"recommendation\":\"NORMAL\",\"ratings\":{}}"))
				.andExpect(status().isCreated()).andReturn(), "id");
		var result = mvc.perform(multipart(base + "/" + id + "/photos")
				.file(new MockMultipartFile("file", "a.png", "image/png", PNG)).header("Authorization", "Bearer " + token))
				.andExpect(status().isCreated()).andReturn();
		String photoId = field(result, "id"), url = field(result, "imageUrl");
		doReturn(false).when(imageService).deleteImageByUrl(url);
		mvc.perform(delete(base + "/" + id + "/photos/" + photoId).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.pendingCleanup", is(1)));
		mvc.perform(get(url)).andExpect(status().isOk());
		doCallRealMethod().when(imageService).deleteImageByUrl(url);
		mvc.perform(post(base + "/cleanup").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.pendingCleanup", is(0)));
		mvc.perform(get(url)).andExpect(status().isNotFound());
		String[] rolledBack = new String[1];
		assertThatThrownBy(() -> transactions.executeWithoutResult(_ -> {
			rolledBack[0] = vacationService.addPhoto(UUID.fromString(trackerId), UUID.fromString(id), null,
					new MockMultipartFile("file", "rollback.png", "image/png", PNG)).imageUrl();
			throw new IllegalStateException("rollback");
		})).hasMessage("rollback");
		mvc.perform(get(rolledBack[0])).andExpect(status().isNotFound());
	}
	@Test void convertsOriginalAmountsUsingTripStartAndNeverAddsTotalToBreakdown() throws Exception {
		String base="/api/vacations/"+trackerId;
		String id=field(mvc.perform(post(base).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Plavba\",\"status\":\"COMPLETED\",\"focuses\":[],\"startDate\":\"2025-01-03\",\"recommendation\":\"NORMAL\",\"ratings\":{},\"totalMinor\":10000,\"currencyCode\":\"EUR\"}"))
				.andExpect(status().isCreated()).andReturn(),"id");
		mvc.perform(post(base+"/"+id+"/expenses").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Výlet\",\"amountMinor\":1200,\"currencyCode\":\"EUR\"}")).andExpect(status().isCreated());
		when(rates.getRate(any(),any(),any())).thenReturn(new BigDecimal("25"));
		mvc.perform(get(base+"/"+id+"/conversion?target=CZK").header("Authorization","Bearer "+token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.requestedDate",is("2025-01-03")))
				.andExpect(jsonPath("$.estimated",is(false))).andExpect(jsonPath("$.total.convertedMinor",is(250000)))
				.andExpect(jsonPath("$.expensesSumMinor",is(30000)));
		verify(rates).getRate(any(),any(),eq(LocalDate.of(2025,1,3)));
		mvc.perform(get(base+"/"+id).header("Authorization","Bearer "+token))
				.andExpect(jsonPath("$.vacation.totalMinor",is(10000))).andExpect(jsonPath("$.expenses[0].amountMinor",is(1200)));
	}
	@Test void unavailableRateDoesNotBecomeAZeroOrPartialTotal() throws Exception {
		String base="/api/vacations/"+trackerId;
		String id=field(mvc.perform(post(base).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Plavba\",\"status\":\"PLANNED\",\"focuses\":[],\"year\":2025,\"approximate\":true,\"recommendation\":\"NORMAL\",\"ratings\":{}}"))
				.andExpect(status().isCreated()).andReturn(),"id");
		mvc.perform(post(base+"/"+id+"/expenses").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Výlet\",\"amountMinor\":1200,\"currencyCode\":\"EUR\"}")).andExpect(status().isCreated());
		mvc.perform(get(base+"/"+id+"/conversion?target=CZK").header("Authorization","Bearer "+token)).andExpect(status().isBadRequest());
		mvc.perform(get(base+"/"+id+"/conversion?target=CZK&mode=current").header("Authorization","Bearer "+token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.estimated",is(true)))
				.andExpect(jsonPath("$.expenses[0].error",is("Kurz není dostupný")))
				.andExpect(jsonPath("$.expensesSumMinor").isEmpty());
	}
	@Test void futureRatesAreEstimatedAndOverflowDoesNotProduceATotal() throws Exception {
		String base = "/api/vacations/" + trackerId;
		String start = LocalDate.now(ZoneOffset.UTC).plusDays(10).toString();
		String id = field(mvc.perform(post(base).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Future\",\"status\":\"PLANNED\",\"focuses\":[],\"startDate\":\"" + start +
						"\",\"recommendation\":\"NORMAL\",\"ratings\":{}}")).andExpect(status().isCreated()).andReturn(), "id");
		mvc.perform(post(base + "/" + id + "/expenses").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Large\",\"amountMinor\":9007199254740991,\"currencyCode\":\"EUR\"}"))
				.andExpect(status().isCreated());
		when(rates.getRate(any(), any(), any())).thenReturn(new BigDecimal("2"));
		mvc.perform(get(base + "/" + id + "/conversion?target=CZK").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.estimated", is(true)))
				.andExpect(jsonPath("$.requestedDate", is(LocalDate.now(ZoneOffset.UTC).toString())))
				.andExpect(jsonPath("$.expenses[0].error", is("Přepočtená částka je příliš vysoká")))
				.andExpect(jsonPath("$.expensesSumMinor").isEmpty());
	}
	private String field(MvcResult result,String name) throws Exception {
		var matcher=Pattern.compile("\""+name+"\"\\s*:\\s*\"([^\"]+)\"").matcher(result.getResponse().getContentAsString());
		if (!matcher.find()) throw new IllegalStateException("Missing response field: "+name);
		return matcher.group(1);
	}
}
