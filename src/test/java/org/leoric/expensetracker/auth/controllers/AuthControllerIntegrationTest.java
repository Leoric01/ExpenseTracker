package org.leoric.expensetracker.auth.controllers;

import org.junit.jupiter.api.Test;
import com.jayway.jsonpath.JsonPath;
import org.leoric.expensetracker.auth.repositories.UserRepository;
import org.leoric.expensetracker.auth.security.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AuthControllerIntegrationTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired private UserRepository users;
	@Autowired private JwtService jwtService;

	@Test
	void registeredUsersHaveDistinctIdentitiesAndCannotAccessEachOthersTrackers() throws Exception {
		var first = registerAndLogin("first-isolation@test.com", "First");
		var second = registerAndLogin("second-isolation@test.com", "Second");
		var firstClaims = jwtService.extractClaims(first);
		var secondClaims = jwtService.extractClaims(second);
		assertThat(firstClaims.get("id", String.class)).isEqualTo(users.findByEmail("first-isolation@test.com").orElseThrow().getId().toString());
		assertThat(firstClaims.get("id", String.class)).isNotEqualTo(secondClaims.get("id", String.class));
		assertThat(firstClaims.get("fullName", String.class)).isEqualTo("First Test");
		assertThat(firstClaims.get("authorities")).isEqualTo(java.util.List.of("USER"));

		var created = mockMvc.perform(post("/api/expense-trackers")
						.header("Authorization", "Bearer " + first)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Private tracker\"}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		String trackerId = JsonPath.read(created, "$.id");
		mockMvc.perform(get("/api/expense-trackers/{id}", trackerId).header("Authorization", "Bearer " + first))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/expense-trackers/{id}", trackerId).header("Authorization", "Bearer " + second))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.businessErrorCode", is("B-2001")));
		mockMvc.perform(get("/api/expense-trackers/mine").header("Authorization", "Bearer " + second))
				.andExpect(status().isOk());
		mockMvc.perform(get("/profile/image-storage").header("Authorization", "Bearer " + first))
				.andExpect(status().isForbidden());
	}

	private String registerAndLogin(String email, String firstName) throws Exception {
		mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
						.content("{\"firstName\":\"" + firstName + "\",\"lastName\":\"Test\",\"email\":\"" + email + "\",\"password\":\"password123\"}"))
				.andExpect(status().isCreated());
		var response = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.token");
	}

	// --- Register ---

	@Test
	void register_shouldReturn201WithNoBody() throws Exception {
		mockMvc.perform(post("/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"firstName": "John",
									"lastName": "Doe",
									"email": "john@test.com",
									"password": "password123"
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$").doesNotExist());
	}

	@Test
	void register_shouldReturn400ForInvalidEmail() throws Exception {
		mockMvc.perform(post("/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"firstName": "John",
									"lastName": "Doe",
									"email": "not-an-email",
									"password": "password123"
								}
								"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void register_shouldReturn400ForBlankFirstName() throws Exception {
		mockMvc.perform(post("/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"firstName": "",
									"lastName": "Doe",
									"email": "john@test.com",
									"password": "password123"
								}
								"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void register_shouldReturn400ForShortPassword() throws Exception {
		mockMvc.perform(post("/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"firstName": "John",
									"lastName": "Doe",
									"email": "john@test.com",
									"password": "sh"
								}
								"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void register_shouldReturn400ForBlankPassword() throws Exception {
		mockMvc.perform(post("/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"firstName": "John",
									"lastName": "Doe",
									"email": "john@test.com",
									"password": ""
								}
								"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void register_shouldFailWhenEmailAlreadyUsed() throws Exception {
		// Register first user
		mockMvc.perform(post("/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"firstName": "John",
									"lastName": "Doe",
									"email": "duplicate@test.com",
									"password": "password123"
								}
								"""))
				.andExpect(status().isCreated());

		// Try registering same email again
		mockMvc.perform(post("/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"firstName": "Jane",
									"lastName": "Doe",
									"email": "duplicate@test.com",
									"password": "password456"
								}
								"""))
				.andExpect(status().isConflict());
	}

	// --- Login ---

	@Test
	void login_shouldReturn200WithToken() throws Exception {
		// Register first
		mockMvc.perform(post("/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"firstName": "John",
									"lastName": "Doe",
									"email": "logintest@test.com",
									"password": "password123"
								}
								"""))
				.andExpect(status().isCreated());

		// Login
		mockMvc.perform(post("/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"email": "logintest@test.com",
									"password": "password123"
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token", notNullValue()));
	}

	@Test
	void login_shouldReturn401ForBadCredentials() throws Exception {
		mockMvc.perform(post("/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"email": "nonexistent@test.com",
									"password": "wrongpassword"
								}
								"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.businessErrorCode", is("B-1001")))
				.andExpect(jsonPath("$.businessErrorDescription", notNullValue()));
	}

	@Test
	void login_shouldReturn401ForWrongPassword() throws Exception {
		// Register first
		mockMvc.perform(post("/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"firstName": "John",
									"lastName": "Doe",
									"email": "wrongpwd@test.com",
									"password": "password123"
								}
								"""))
				.andExpect(status().isCreated());

		// Login with wrong password
		mockMvc.perform(post("/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"email": "wrongpwd@test.com",
									"password": "wrongpassword"
								}
								"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.businessErrorCode", is("B-1001")))
				.andExpect(jsonPath("$.businessErrorDescription", notNullValue()));
	}

	@Test
	void login_shouldReturn400ForMissingFields() throws Exception {
		mockMvc.perform(post("/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"email": "",
									"password": ""
								}
								"""))
				.andExpect(status().isBadRequest());
	}

	// --- Login with default admin ---

	@Test
	void login_shouldSucceedWithSeededAdminUser() throws Exception {
		var response = mockMvc.perform(post("/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"email": "admin@test.com",
									"password": "admin123"
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token", notNullValue()))
				.andReturn().getResponse().getContentAsString();
		String token = JsonPath.read(response, "$.token");
		assertThat(jwtService.extractClaims(token).get("authorities")).isEqualTo(java.util.List.of("ADMIN"));
	}
}