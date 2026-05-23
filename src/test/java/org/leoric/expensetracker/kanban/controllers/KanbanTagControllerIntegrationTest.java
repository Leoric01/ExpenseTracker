package org.leoric.expensetracker.kanban.controllers;

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

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class KanbanTagControllerIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	private String jwtToken;
	private UUID trackerId;

	@BeforeEach
	void setUp() throws Exception {
		mockMvc.perform(post("/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"firstName\":\"Kanban\",\"lastName\":\"Tag\",\"email\":\"kanban.tag.integration@test.com\",\"password\":\"password123\"}"))
				.andExpect(status().isCreated());

		MvcResult login = mockMvc.perform(post("/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"kanban.tag.integration@test.com\",\"password\":\"password123\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token", notNullValue()))
				.andReturn();
		jwtToken = extractJsonString(login.getResponse().getContentAsString(), "token");

		MvcResult tracker = mockMvc.perform(post("/api/expense-trackers")
						.header("Authorization", "Bearer " + jwtToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Kanban tag tracker\",\"description\":\"desc\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id", notNullValue()))
				.andReturn();
		trackerId = UUID.fromString(extractJsonString(tracker.getResponse().getContentAsString(), "id"));

	}

	@Test
	void kanbanTagList_shouldReturn401WhenNotAuthenticated() throws Exception {
		mockMvc.perform(get("/api/kanban-tag/{trackerId}", trackerId))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.businessErrorCode", is("B-1001")));
	}

	@Test
	void kanbanTagLifecycle_andDuplicate_shouldWork() throws Exception {
		MvcResult create = mockMvc.perform(post("/api/kanban-tag/{trackerId}", trackerId)
						.header("Authorization", "Bearer " + jwtToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"backend\",\"color\":\"#aa00ff\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id", notNullValue()))
				.andExpect(jsonPath("$.color", is("#AA00FF")))
				.andReturn();
		UUID tagId = UUID.fromString(extractJsonString(create.getResponse().getContentAsString(), "id"));

		mockMvc.perform(post("/api/kanban-tag/{trackerId}", trackerId)
						.header("Authorization", "Bearer " + jwtToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"backend\",\"color\":\"#00ff00\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.businessErrorCode", is("B-5307")));

		mockMvc.perform(patch("/api/kanban-tag/{trackerId}/{tagId}", trackerId, tagId)
						.header("Authorization", "Bearer " + jwtToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"devops\",\"color\":\"#00cc11\",\"active\":true}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name", is("devops")));

		mockMvc.perform(get("/api/kanban-tag/{trackerId}", trackerId)
						.header("Authorization", "Bearer " + jwtToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].name", is("devops")));
	}

	@Test
	void kanbanTagBulkCreate_shouldCreateMultipleTags() throws Exception {
		mockMvc.perform(post("/api/kanban-tag/{trackerId}/bulk", trackerId)
						.header("Authorization", "Bearer " + jwtToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								[
								  {"name":"java","color":"#ff00aa"},
								  {"name":"python","color":"#00ff00"}
								]
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].name", is("java")))
				.andExpect(jsonPath("$[1].name", is("python")));
	}

	@Test
	void kanbanTagBulkCreate_shouldRejectEmptyPayload() throws Exception {
		mockMvc.perform(post("/api/kanban-tag/{trackerId}/bulk", trackerId)
						.header("Authorization", "Bearer " + jwtToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("[]"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.businessErrorCode", is("B-5303")));
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