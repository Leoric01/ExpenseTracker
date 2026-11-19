package org.leoric.expensetracker.workout.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.leoric.expensetracker.auth.models.UserExpenseTrackerRole;
import org.leoric.expensetracker.auth.repositories.RoleRepository;
import org.leoric.expensetracker.auth.repositories.UserExpenseTrackerRoleRepository;
import org.leoric.expensetracker.auth.repositories.UserRepository;
import org.leoric.expensetracker.expensetracker.repositories.ExpenseTrackerRepository;
import org.leoric.expensetracker.image.services.LocalFileStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;
import java.util.Base64;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WorkoutControllerIntegrationTest {
	private static final Path IMAGE_DIRECTORY = createImageDirectory();
	private static final byte[] PNG = Base64.getDecoder().decode(
			"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/l9sAAAAASUVORK5CYII=");
	private static final String PLAN = """
			{"title":"Workout","weekLabel":"Current plan","days":[
			  {"id":"monday","label":"Po","name":"Push","focus":"Chest","exercises":[
			    {"id":"pushups","name":"Pushups","sets":"2 x 10","description":"Slow"},
			    {"id":"plank","name":"Plank","sets":"30 s"}]},
			  {"id":"friday","label":"Pa","name":"Full body","focus":"Body","exercises":[
			    {"id":"pushups","name":"Pushups","sets":"1 x 10"}]}
			]}
			""";

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private RoleRepository roleRepository;
	@Autowired
	private UserExpenseTrackerRoleRepository trackerRoleRepository;
	@Autowired
	private ExpenseTrackerRepository trackerRepository;
	@Autowired
	private LocalFileStorage localFileStorage;
	private String token;
	private UUID trackerId;

	@DynamicPropertySource
	static void storageProperties(DynamicPropertyRegistry registry) {
		registry.add("app.image-storage.local-directory", () -> IMAGE_DIRECTORY.toString());
	}

	private static Path createImageDirectory() {
		try {
			return Files.createTempDirectory("workout-integration-");
		} catch (java.io.IOException e) {
			throw new ExceptionInInitializerError(e);
		}
	}

	@AfterAll
	static void removeImageDirectory() throws Exception {
		try (var paths = Files.walk(IMAGE_DIRECTORY)) {
			for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
		}
	}

	@BeforeEach
	void setUp() throws Exception {
		token = registerAndLogin().token();
		MvcResult result = mockMvc.perform(post("/api/expense-trackers")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Workout tracker\"}"))
				.andExpect(status().isCreated()).andReturn();
		trackerId = UUID.fromString(field(result, "id"));
	}

	@Test
	void planLifecyclePreservesCompletionForMatchingDayAndExercise() throws Exception {
		mockMvc.perform(get("/api/workout/{trackerId}", trackerId).header("Authorization", "Bearer " + token))
				.andExpect(status().isNotFound());
		putPlan(PLAN).andExpect(status().isOk())
				.andExpect(jsonPath("$.days[0].exercises[0].completed", is(false)));
		mockMvc.perform(put("/api/workout/{trackerId}/days/monday/exercises/pushups/completion", trackerId)
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON).content("{\"completed\":true}"))
				.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/workout/{trackerId}", trackerId).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.days[0].exercises[0].completed", is(true)))
				.andExpect(jsonPath("$.days[1].exercises[0].completed", is(false)));

		String updated = """
				{"title":"Updated","weekLabel":"Next plan","days":[
				  {"id":"friday","label":"Pa","name":"Full body","focus":"Body","exercises":[
				    {"id":"pushups","name":"Pushups","sets":"1 x 10"}]},
				  {"id":"monday","label":"Po","name":"Push","focus":"Chest","exercises":[
				    {"id":"new","name":"New","sets":"1"},
				    {"id":"pushups","name":"Pushups updated","sets":"3 x 10"}]}
				]}
				""";
		putPlan(updated).andExpect(status().isOk())
				.andExpect(jsonPath("$.days[1].exercises[1].completed", is(true)))
				.andExpect(jsonPath("$.days[1].exercises[0].completed", is(false)));
		mockMvc.perform(get("/api/workout/{trackerId}", trackerId).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.days[0].id", is("friday")))
				.andExpect(jsonPath("$.days[1].exercises[1].name", is("Pushups updated")))
				.andExpect(jsonPath("$.days[1].exercises[1].completed", is(true)));
		mockMvc.perform(put("/api/workout/{trackerId}/days/monday/exercises/plank/completion", trackerId)
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON).content("{\"completed\":true}"))
				.andExpect(status().isNotFound());
		mockMvc.perform(delete("/api/workout/{trackerId}/completion", trackerId)
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/workout/{trackerId}", trackerId).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.days[1].exercises[1].completed", is(false)));
		putPlan(PLAN).andExpect(status().isOk())
				.andExpect(jsonPath("$.days[0].exercises[1].id", is("plank")))
				.andExpect(jsonPath("$.days[0].exercises[1].completed", is(false)));
		mockMvc.perform(put("/api/workout/{trackerId}/days/friday/exercises/pushups/completion", trackerId)
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON).content("{\"completed\":true}"))
				.andExpect(status().isNoContent());
		mockMvc.perform(put("/api/workout/{trackerId}/days/friday/exercises/pushups/completion", trackerId)
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON).content("{\"completed\":false}"))
				.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/workout/{trackerId}", trackerId).header("Authorization", "Bearer " + token))
				.andExpect(jsonPath("$.days[1].exercises[0].completed", is(false)));
	}

	@Test
	void rejectsInvalidAndDuplicateIdsWithoutChangingPlan() throws Exception {
		putPlan(PLAN).andExpect(status().isOk());
		putPlan(PLAN.replace("\"id\":\"plank\"", "\"id\":\"pushups\""))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.businessErrorCode", is("B-5401")));
		putPlan(PLAN.replace("\"id\":\"friday\"", "\"id\":\"monday\""))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.businessErrorCode", is("B-5401")));
		putPlan(PLAN.replace("\"id\":\"plank\"", "\"id\":\"invalid/id\""))
				.andExpect(status().isBadRequest());
		putPlan(PLAN.replace("\"id\":\"plank\",\"name\":\"Plank\"", "\"id\":\"plank\",\"name\":\" \""))
				.andExpect(status().isBadRequest());
		putPlan(PLAN.replace("{\"id\":\"plank\",\"name\":\"Plank\",\"sets\":\"30 s\"}", "null"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(put("/api/workout/{trackerId}/days/monday/exercises/pushups/completion", trackerId)
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/workout/{trackerId}", trackerId).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.days[0].exercises[1].id", is("plank")));
	}

	@Test
	void removingAndReaddingDayDoesNotKeepCompletion() throws Exception {
		putPlan(PLAN).andExpect(status().isOk());
		mockMvc.perform(put("/api/workout/{trackerId}/days/monday/exercises/pushups/completion", trackerId)
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON).content("{\"completed\":true}"))
				.andExpect(status().isNoContent());
		putPlan("{\"title\":\"Empty\",\"weekLabel\":\"Current\",\"days\":[]}")
				.andExpect(status().isOk()).andExpect(jsonPath("$.days.length()", is(0)));
		putPlan(PLAN).andExpect(status().isOk())
				.andExpect(jsonPath("$.days[0].exercises[0].completed", is(false)));
		mockMvc.perform(get("/api/workout/{trackerId}", trackerId).header("Authorization", "Bearer " + token))
				.andExpect(jsonPath("$.days[0].exercises[0].completed", is(false)));
	}

	@Test
	void imageUploadReadDeleteAndPlanChanges() throws Exception {
		putPlan(PLAN).andExpect(status().isOk());
		MockMultipartFile image = new MockMultipartFile("file", "test.png", "image/png", PNG);
		MvcResult upload = mockMvc.perform(multipart("/api/workout/{trackerId}/days/monday/exercises/pushups/image", trackerId)
						.file(image).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.days[0].exercises[0].imageUrl").isString())
				.andReturn();
		String url = field(upload, "imageUrl");
		mockMvc.perform(get(url)).andExpect(status().isOk()).andExpect(result ->
				org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(PNG));
		mockMvc.perform(get(url)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
				.string("X-Content-Type-Options", "nosniff"))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
						.contentType("image/png"));
		mockMvc.perform(multipart("/api/workout/{trackerId}/days/monday/exercises/pushups/image", trackerId)
						.file(image).header("Authorization", "Bearer " + token))
				.andExpect(status().isBadRequest());
		putPlan(PLAN.replace("\"name\":\"Pushups\"", "\"name\":\"Changed\""))
				.andExpect(status().isOk()).andExpect(jsonPath("$.days[0].exercises[0].imageUrl", is(url)));
		putPlan("{\"title\":\"Empty\",\"weekLabel\":\"Current\",\"days\":[]}")
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.businessErrorCode", is("B-5401")));
		mockMvc.perform(delete("/api/workout/{trackerId}/days/monday/exercises/pushups/image", trackerId)
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isNoContent());
		mockMvc.perform(get(url)).andExpect(status().isNotFound());
		mockMvc.perform(delete("/api/workout/{trackerId}/days/monday/exercises/pushups/image", trackerId)
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isNoContent());
		putPlan("{\"title\":\"Empty\",\"weekLabel\":\"Current\",\"days\":[]}")
				.andExpect(status().isOk());
	}

	@Test
	void invalidImageAndMissingExerciseAreRejected() throws Exception {
		putPlan(PLAN).andExpect(status().isOk());
		mockMvc.perform(multipart("/api/workout/{trackerId}/days/monday/exercises/pushups/image", trackerId)
						.file(new MockMultipartFile("file", "fake.png", "image/png", "fake".getBytes()))
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isBadRequest());
		mockMvc.perform(multipart("/api/workout/{trackerId}/days/monday/exercises/missing/image", trackerId)
						.file(new MockMultipartFile("file", "test.png", "image/png", PNG))
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isNotFound());
	}

	@Test
	void localPdfIsServedAsAttachmentWithSafeHeaders() throws Exception {
		var pdf = new MockMultipartFile("file", "receipt.pdf", "application/pdf", "%PDF-1.4".getBytes());
		String url = localFileStorage.save(pdf, "expense-tracker/attachments", false);
		mockMvc.perform(get(url))
				.andExpect(status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType("application/pdf"))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Disposition", "attachment"))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Content-Type-Options", "nosniff"));
	}

	@Test
	void endpointsRequireAuthenticationAndTrackerAccess() throws Exception {
		putPlan(PLAN).andExpect(status().isOk());
		mockMvc.perform(get("/api/workout/{trackerId}", trackerId)).andExpect(status().isUnauthorized());
		var other = registerAndLogin();
		String otherToken = other.token();
		mockMvc.perform(get("/api/workout/{trackerId}", trackerId).header("Authorization", "Bearer " + otherToken))
				.andExpect(status().isForbidden());
		mockMvc.perform(put("/api/workout/{trackerId}", trackerId)
						.header("Authorization", "Bearer " + otherToken)
						.contentType(MediaType.APPLICATION_JSON).content(PLAN))
				.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/workout/{trackerId}/completion", trackerId)
						.header("Authorization", "Bearer " + otherToken))
				.andExpect(status().isForbidden());
		trackerRoleRepository.saveAndFlush(UserExpenseTrackerRole.builder()
				.user(userRepository.findByEmail(other.email()).orElseThrow())
				.expenseTracker(trackerRepository.findById(trackerId).orElseThrow())
				.role(roleRepository.findByName("EXPENSETRACKER_MEMBER").orElseThrow())
				.build());
		mockMvc.perform(get("/api/workout/{trackerId}", trackerId).header("Authorization", "Bearer " + otherToken))
				.andExpect(status().isOk());
		mockMvc.perform(put("/api/workout/{trackerId}", trackerId)
						.header("Authorization", "Bearer " + otherToken)
						.contentType(MediaType.APPLICATION_JSON).content(PLAN))
				.andExpect(status().isForbidden());
		mockMvc.perform(put("/api/workout/{trackerId}/days/monday/exercises/pushups/completion", trackerId)
						.header("Authorization", "Bearer " + otherToken)
						.contentType(MediaType.APPLICATION_JSON).content("{\"completed\":true}"))
				.andExpect(status().isNoContent());
		mockMvc.perform(multipart("/api/workout/{trackerId}/days/monday/exercises/pushups/image", trackerId)
						.file(new MockMultipartFile("file", "test.png", "image/png", PNG))
						.header("Authorization", "Bearer " + otherToken))
				.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/workout/{trackerId}/days/monday/exercises/pushups/image", trackerId)
						.header("Authorization", "Bearer " + otherToken))
				.andExpect(status().isForbidden());
		mockMvc.perform(multipart("/api/workout/{trackerId}/days/monday/exercises/pushups/image", trackerId)
						.file(new MockMultipartFile("file", "test.png", "image/png", PNG)))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(delete("/api/workout/{trackerId}/completion", trackerId)
						.header("Authorization", "Bearer " + otherToken))
				.andExpect(status().isNoContent());
	}

	private org.springframework.test.web.servlet.ResultActions putPlan(String json) throws Exception {
		return mockMvc.perform(put("/api/workout/{trackerId}", trackerId)
				.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private Session registerAndLogin() throws Exception {
		String email = UUID.randomUUID() + "@test.com";
		String credentials = "{\"email\":\"" + email + "\",\"password\":\"password123\"}";
		mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
						.content("{\"firstName\":\"Test\",\"lastName\":\"Workout\",\"email\":\"" + email
								+ "\",\"password\":\"password123\"}"))
				.andExpect(status().isCreated());
		var user = userRepository.findByEmail(email).orElseThrow();
		user.setRoles(new java.util.ArrayList<>(java.util.List.of(roleRepository.findByName("USER").orElseThrow())));
		userRepository.saveAndFlush(user);
		MvcResult result = mockMvc.perform(post("/auth/login")
						.contentType(MediaType.APPLICATION_JSON).content(credentials))
				.andExpect(status().isOk()).andReturn();
		return new Session(email, field(result, "token"));
	}

	private record Session(String email, String token) {}

	private String field(MvcResult result, String name) throws Exception {
		var matcher = Pattern.compile("\"" + name + "\"\\s*:\\s*\"([^\"]+)\"")
				.matcher(result.getResponse().getContentAsString());
		if (!matcher.find()) throw new IllegalStateException("Missing response field: " + name);
		return matcher.group(1);
	}
}
