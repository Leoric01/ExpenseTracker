package org.leoric.expensetracker.todo.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TodoControllerIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	private String jwtToken;
	private UUID trackerId;

	@BeforeEach
	void setUp() throws Exception {
		registerAndLogin();
		trackerId = createTracker("Todo tracker");
	}

	@Test
	void todoTree_shouldReturn401WhenNotAuthenticated() throws Exception {
		mockMvc.perform(get("/api/todo/{trackerId}/tree", trackerId))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.businessErrorCode", is("B-1001")));
	}

	@Test
	void todoCreate_shouldReturn400ForBlankTitle() throws Exception {
		mockMvc.perform(post("/api/todo/{trackerId}", trackerId)
						.header("Authorization", "Bearer " + jwtToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "title": "   "
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.businessErrorCode", is("B-1201")));
	}

	@Test
	void todoLifecycle_shouldSupportTreeCascadeAndSoftDelete() throws Exception {
		UUID rootId = createTodo(trackerId, """
				{
				  "title": "Root task",
				  "priority": "HIGH"
				}
				""");
		createTodo(trackerId, """
				{
				  "parentTodoId": "%s",
				  "title": "Child task"
				}
				""".formatted(rootId));

		mockMvc.perform(patch("/api/todo/{trackerId}/{todoId}/status", trackerId, rootId)
						.header("Authorization", "Bearer " + jwtToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "status": "DONE"
								}
								"""))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/todo/{trackerId}/{todoId}", trackerId, rootId)
						.header("Authorization", "Bearer " + jwtToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id", is(rootId.toString())))
				.andExpect(jsonPath("$.status", is("DONE")))
				.andExpect(jsonPath("$.children[0].status", is("DONE")));

		mockMvc.perform(delete("/api/todo/{trackerId}/{todoId}", trackerId, rootId)
						.header("Authorization", "Bearer " + jwtToken))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/todo/{trackerId}/{todoId}", trackerId, rootId)
						.header("Authorization", "Bearer " + jwtToken))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.businessErrorCode", is("B-5203")));
	}

	@Test
	void todoCreate_shouldReturn409ForDuplicateSiblingTitle() throws Exception {
		createTodo(trackerId, """
				{
				  "title": "Duplicate me"
				}
				""");

		mockMvc.perform(post("/api/todo/{trackerId}", trackerId)
						.header("Authorization", "Bearer " + jwtToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "title": "Duplicate me"
								}
								"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.businessErrorCode", is("B-5201")));
	}

	private void registerAndLogin() throws Exception {
		String email = "todo.integration@test.com";
		String password = "password123";

		mockMvc.perform(post("/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "firstName": "Todo",
								  "lastName": "Integration",
								  "email": "%s",
								  "password": "%s"
								}
								""".formatted(email, password)))
				.andExpect(status().isCreated());

		MvcResult loginResult = mockMvc.perform(post("/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "email": "%s",
								  "password": "%s"
								}
								""".formatted(email, password)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token", notNullValue()))
				.andReturn();

		jwtToken = extractJsonString(loginResult.getResponse().getContentAsString(), "token");
	}

	private UUID createTracker(String name) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/expense-trackers")
						.header("Authorization", "Bearer " + jwtToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "%s",
								  "description": "Tracker for todo integration tests"
								}
								""".formatted(name)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id", notNullValue()))
				.andReturn();

		return UUID.fromString(extractJsonString(result.getResponse().getContentAsString(), "id"));
	}

	private UUID createTodo(UUID trackerId, String jsonBody) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/todo/{trackerId}", trackerId)
						.header("Authorization", "Bearer " + jwtToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(jsonBody))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id", notNullValue()))
				.andReturn();

		return UUID.fromString(extractJsonString(result.getResponse().getContentAsString(), "id"));
	}

	private String extractJsonString(String body, String key) {
		java.util.regex.Matcher matcher = java.util.regex.Pattern
				.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"")
				.matcher(body);
		if (!matcher.find()) {
			throw new IllegalStateException("Field '%s' not found in response body: %s".formatted(key, body));
		}
		return matcher.group(1);
	}
}