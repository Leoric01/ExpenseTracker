package org.leoric.expensetracker.auth.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.leoric.expensetracker.ExpenseTrackerApplication;
import org.leoric.expensetracker.auth.models.Role;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.auth.repositories.RoleRepository;
import org.leoric.expensetracker.auth.repositories.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AdminUserControllerIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private String adminJwtToken;
	private String userJwtToken;

	@BeforeEach
	void setUp() throws Exception {
		Role userRole = roleRepository.findByName(ExpenseTrackerApplication.USER)
					.orElseThrow();

		userRepository.save(User.builder()
				.firstName("Dummy")
				.lastName("User")
				.email("dummy-user@test.com")
				.password(passwordEncoder.encode("password123"))
				.enabled(true)
				.accountLocked(false)
				.roles(java.util.List.of(userRole))
				.build());

		adminJwtToken = loginAndExtractToken("admin@test.com", "admin123");
		userJwtToken = loginAndExtractToken("dummy-user@test.com", "password123");
	}

	@Test
	void resetPassword_shouldReturn204AndAllowLoginWithNewPassword() throws Exception {
		mockMvc.perform(patch("/admin/users/reset-password")
					.header("Authorization", "Bearer " + adminJwtToken)
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
						{
							"email": "dummy-user@test.com",
							"newPassword": "newSecurePassword1"
						}
						"""))
				.andExpect(status().isNoContent());

		mockMvc.perform(post("/auth/login")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
						{
							"email": "dummy-user@test.com",
							"password": "newSecurePassword1"
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token", notNullValue()));

		mockMvc.perform(post("/auth/login")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
						{
							"email": "dummy-user@test.com",
							"password": "password123"
						}
						"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.businessErrorCode", is("B-1001")));
	}

	@Test
	void resetPassword_shouldReturn403ForNonAdmin() throws Exception {
		mockMvc.perform(patch("/admin/users/reset-password")
					.header("Authorization", "Bearer " + userJwtToken)
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
						{
							"email": "dummy-user@test.com",
							"newPassword": "newSecurePassword1"
						}
						"""))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.businessErrorCode", is("B-1005")));
	}

	private String loginAndExtractToken(String email, String password) throws Exception {
		MvcResult result = mockMvc.perform(post("/auth/login")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
						{
							"email": "%s",
							"password": "%s"
						}
						""".formatted(email, password)))
				.andExpect(status().isOk())
				.andReturn();

		String body = result.getResponse().getContentAsString();
		return body.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
	}
}