package org.leoric.expensetracker.auth.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

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
class ProfileControllerIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	private String jwtToken;

	@BeforeEach
	void setUp() throws Exception {
		// Register a test user
		mockMvc.perform(post("/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"firstName": "Profile",
									"lastName": "Tester",
									"email": "profile@test.com",
									"password": "password123"
								}
								"""))
				.andExpect(status().isCreated());

		// Login to get a JWT token
		MvcResult result = mockMvc.perform(post("/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"email": "profile@test.com",
									"password": "password123"
								}
								"""))
				.andExpect(status().isOk())
				.andReturn();

		String body = result.getResponse().getContentAsString();
		// Extract token from {"token":"..."}
		jwtToken = body.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
	}

	// --- GET /profile/me ---

	@Test
	void me_shouldReturn200WithUserInfo() throws Exception {
		mockMvc.perform(get("/profile/me")
						.header("Authorization", "Bearer " + jwtToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id", notNullValue()))
				.andExpect(jsonPath("$.email", is("profile@test.com")))
				.andExpect(jsonPath("$.firstName", is("Profile")))
				.andExpect(jsonPath("$.lastName", is("Tester")))
				.andExpect(jsonPath("$.roles", notNullValue()));
	}

	@Test
	void me_shouldReturn401WhenNotAuthenticated() throws Exception {
		mockMvc.perform(get("/profile/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.businessErrorCode", is("B-1001")))
				.andExpect(jsonPath("$.businessErrorDescription", notNullValue()));
	}

	@Test
	void me_shouldReturn401WithInvalidToken() throws Exception {
		mockMvc.perform(get("/profile/me")
						.header("Authorization", "Bearer invalid.jwt.token"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.businessErrorCode", is("B-1001")))
				.andExpect(jsonPath("$.businessErrorDescription", notNullValue()));
	}

	// --- PATCH /profile/update ---

	@Test
	void updateProfile_shouldReturn200WithUpdatedUser() throws Exception {
		mockMvc.perform(patch("/profile/update")
						.header("Authorization", "Bearer " + jwtToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"firstName": "Updated",
									"lastName": "Name"
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.firstName", is("Updated")))
				.andExpect(jsonPath("$.lastName", is("Name")))
				.andExpect(jsonPath("$.email", is("profile@test.com")));
	}

	@Test
	void updateProfile_shouldReturn401WhenNotAuthenticated() throws Exception {
		mockMvc.perform(patch("/profile/update")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"firstName": "Updated"
								}
								"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.businessErrorCode", is("B-1001")))
				.andExpect(jsonPath("$.businessErrorDescription", notNullValue()));
	}

	@Test
	void updateProfile_changesArePersisted() throws Exception {
		// Update the profile
		mockMvc.perform(patch("/profile/update")
						.header("Authorization", "Bearer " + jwtToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"firstName": "Persisted",
									"lastName": "Check"
								}
								"""))
				.andExpect(status().isOk());

		// Verify via /me
		mockMvc.perform(get("/profile/me")
						.header("Authorization", "Bearer " + jwtToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.firstName", is("Persisted")))
				.andExpect(jsonPath("$.lastName", is("Check")));
	}

	// --- PATCH /profile/change-password ---

	@Test
	void changePassword_shouldReturn204() throws Exception {
		mockMvc.perform(patch("/profile/change-password")
						.header("Authorization", "Bearer " + jwtToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"oldPassword": "password123",
									"newPassword": "newSecurePassword1",
									"newConfirmationPassword": "newSecurePassword1"
								}
								"""))
				.andExpect(status().isNoContent());

		// Verify can login with new password
		mockMvc.perform(post("/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"email": "profile@test.com",
									"password": "newSecurePassword1"
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token", notNullValue()));
	}

	@Test
	void changePassword_shouldReturn400WhenCurrentPasswordIncorrect() throws Exception {
		mockMvc.perform(patch("/profile/change-password")
						.header("Authorization", "Bearer " + jwtToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"oldPassword": "wrongPassword",
									"newPassword": "newSecurePassword1",
									"newConfirmationPassword": "newSecurePassword1"
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.businessErrorCode", is("B-1101")));
	}

	@Test
	void changePassword_shouldReturn400WhenNewPasswordsDoNotMatch() throws Exception {
		mockMvc.perform(patch("/profile/change-password")
						.header("Authorization", "Bearer " + jwtToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"oldPassword": "password123",
									"newPassword": "newSecurePassword1",
									"newConfirmationPassword": "differentPassword"
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.businessErrorCode", is("B-1102")));
	}

	@Test
	void changePassword_shouldReturn401WhenNotAuthenticated() throws Exception {
		mockMvc.perform(patch("/profile/change-password")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"oldPassword": "password123",
									"newPassword": "newSecurePassword1",
									"newConfirmationPassword": "newSecurePassword1"
								}
								"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.businessErrorCode", is("B-1001")))
				.andExpect(jsonPath("$.businessErrorDescription", notNullValue()));
	}

	@Test
	void changePassword_oldPasswordStopsWorking() throws Exception {
		// Change password
		mockMvc.perform(patch("/profile/change-password")
						.header("Authorization", "Bearer " + jwtToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"oldPassword": "password123",
									"newPassword": "newSecurePassword1",
									"newConfirmationPassword": "newSecurePassword1"
								}
								"""))
				.andExpect(status().isNoContent());

		// Verify old password doesn't work anymore
		mockMvc.perform(post("/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"email": "profile@test.com",
									"password": "password123"
								}
								"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.businessErrorCode", is("B-1001")))
				.andExpect(jsonPath("$.businessErrorDescription", notNullValue()));
	}
}