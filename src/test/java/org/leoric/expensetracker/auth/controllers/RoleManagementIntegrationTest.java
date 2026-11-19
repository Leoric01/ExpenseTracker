package org.leoric.expensetracker.auth.controllers;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RoleManagementIntegrationTest {
	@Autowired MockMvc mvc;

	@Test
	void lastAdministratorCannotBeDemotedOrDeleteTheirAccount() throws Exception {
		String admin = login("admin@test.com", "admin123");
		String adminId = JsonPath.read(getJson("/profile/me", admin), "$.id");
		putJson("/api/roles/users/" + adminId, admin, "{\"roles\":[\"USER\"],\"expectedRoles\":[\"ADMIN\"]}").andExpect(status().isBadRequest());
		mvc.perform(delete("/profile/me").header("Authorization", bearer(admin))).andExpect(status().isBadRequest());
		mvc.perform(get("/profile/me").header("Authorization", bearer(admin)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.roles", hasItems("ADMIN")));
	}

	@Test
	void concurrentAdministratorDemotionsCannotRemoveAllAdministrators() throws Exception {
		String first = login("admin@test.com", "admin123");
		String second = register("second-admin@test.com");
		String firstId = JsonPath.read(getJson("/profile/me", first), "$.id");
		String secondId = JsonPath.read(getJson("/profile/me", second), "$.id");
		putJson("/api/roles/users/" + secondId, first, "{\"roles\":[\"USER\",\"ADMIN\"],\"expectedRoles\":[\"USER\"]}").andExpect(status().isNoContent());
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch go = new CountDownLatch(1);
		try (var pool = Executors.newFixedThreadPool(2)) {
			var a = pool.submit(() -> { ready.countDown(); go.await(); return putJson("/api/roles/users/" + firstId, first, "{\"roles\":[\"USER\"],\"expectedRoles\":[\"ADMIN\"]}").andReturn().getResponse().getStatus(); });
			var b = pool.submit(() -> { ready.countDown(); go.await(); return putJson("/api/roles/users/" + secondId, second, "{\"roles\":[\"USER\"],\"expectedRoles\":[\"USER\",\"ADMIN\"]}").andReturn().getResponse().getStatus(); });
			assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			go.countDown();
			assertThat(java.util.List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)))
					.containsExactlyInAnyOrder(204, 400);
		}
	}

	@Test
	void staleRoleUpdatesAreRejectedWithoutOverwritingNewerChanges() throws Exception {
		String admin = login("admin@test.com", "admin123");
		String user = register("stale-role@test.com");
		String userId = JsonPath.read(getJson("/profile/me", user), "$.id");
		String tracker = JsonPath.read(postJson("/api/expense-trackers", user, "{\"name\":\"Stale tracker\"}")
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
		String member = register("stale-member@test.com");
		String memberId = JsonPath.read(getJson("/profile/me", member), "$.id");

		putJson("/api/roles/users/" + userId, admin, "{\"roles\":[\"USER\",\"ADMIN\"],\"expectedRoles\":[\"USER\"]}").andExpect(status().isNoContent());
		putJson("/api/roles/users/" + userId, admin, "{\"roles\":[\"USER\"],\"expectedRoles\":[\"USER\"]}").andExpect(status().isBadRequest());
		mvc.perform(get("/profile/me").header("Authorization", bearer(user)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.roles", hasItems("USER", "ADMIN")));

		String path = "/api/roles/trackers/" + tracker + "/users/" + memberId;
		putJson(path, admin, "{\"roles\":[\"EXPENSETRACKER_MEMBER\"],\"expectedRoles\":[]}").andExpect(status().isNoContent());
		putJson(path, admin, "{\"roles\":[\"EXPENSETRACKER_OWNER\"],\"expectedRoles\":[]}").andExpect(status().isBadRequest());
		mvc.perform(get("/api/expense-trackers/mine").header("Authorization", bearer(member)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.content[0].roles", hasItems("EXPENSETRACKER_MEMBER")))
				.andExpect(jsonPath("$.content[0].roles.length()", is(1)));
	}

	@Test
	void requestLifecycleAndDirectGrantsResolvePendingRequests() throws Exception {
		String admin = login("admin@test.com", "admin123");
		String user = register("request-lifecycle@test.com");
		String userId = JsonPath.read(getJson("/profile/me", user), "$.id");
		String first = JsonPath.read(postJson("/api/roles/requests", user, "{\"role\":\"ADMIN\"}")
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
		patch("/api/roles/requests/" + first + "/reject", admin).andExpect(status().isOk()).andExpect(jsonPath("$.status", is("REJECTED")));
		patch("/api/roles/requests/" + first + "/approve", admin).andExpect(status().isBadRequest());
		String second = JsonPath.read(postJson("/api/roles/requests", user, "{\"role\":\"ADMIN\"}")
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
		patch("/api/roles/requests/" + second + "/cancel", user).andExpect(status().isOk()).andExpect(jsonPath("$.status", is("CANCELLED")));
		String third = JsonPath.read(postJson("/api/roles/requests", user, "{\"role\":\"ADMIN\"}")
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
		putJson("/api/roles/users/" + userId, admin, "{\"roles\":[\"USER\",\"ADMIN\"],\"expectedRoles\":[\"USER\"]}").andExpect(status().isNoContent());
		patch("/api/roles/requests/" + third + "/approve", admin).andExpect(status().isBadRequest());
		mvc.perform(get("/api/roles/requests?status=PENDING").header("Authorization", bearer(admin)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.content.length()", is(0)));
		mvc.perform(get("/api/roles/notifications").header("Authorization", bearer(user)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.decisions[*].status", hasItems("GRANTED", "APPROVED", "REJECTED")));

		String owner = register("request-owner@test.com");
		String tracker = JsonPath.read(postJson("/api/expense-trackers", owner, "{\"name\":\"Request tracker\"}")
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
		String newcomer = register("request-newcomer@test.com");
		String newcomerId = JsonPath.read(getJson("/profile/me", newcomer), "$.id");
		postJson("/api/expense-trackers/" + tracker + "/access-requests", newcomer, "{}").andExpect(status().isCreated());
		putJson("/api/roles/trackers/" + tracker + "/users/" + newcomerId, admin,
				"{\"roles\":[\"EXPENSETRACKER_MEMBER\"],\"expectedRoles\":[]}").andExpect(status().isNoContent());
		mvc.perform(get("/api/expense-trackers/access-requests/mine").header("Authorization", bearer(newcomer)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.content[0].status", is("APPROVED")));
		String member = register("request-member@test.com");
		String memberId = JsonPath.read(getJson("/profile/me", member), "$.id");
		putJson("/api/roles/trackers/" + tracker + "/users/" + memberId, admin,
				"{\"roles\":[\"EXPENSETRACKER_MEMBER\"],\"expectedRoles\":[]}").andExpect(status().isNoContent());
		String request = JsonPath.read(postJson("/api/expense-trackers/" + tracker + "/access-requests/owner", member, "{}")
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
		postJson("/api/expense-trackers/" + tracker + "/access-requests/owner", member, "{}").andExpect(status().isBadRequest());
		mvc.perform(get("/api/expense-trackers/" + tracker + "/access-requests?status=PENDING").header("Authorization", bearer(owner)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.content.length()", is(1)));
		putJson("/api/roles/trackers/" + tracker + "/users/" + memberId, owner,
				"{\"roles\":[\"EXPENSETRACKER_MEMBER\",\"EXPENSETRACKER_OWNER\"],\"expectedRoles\":[\"EXPENSETRACKER_MEMBER\"]}").andExpect(status().isNoContent());
		patch("/api/expense-trackers/access-requests/" + request + "/approve", owner).andExpect(status().isBadRequest());
		mvc.perform(get("/api/expense-trackers/" + tracker + "/access-requests/owner/pending").header("Authorization", bearer(member)))
				.andExpect(status().isNoContent());
		mvc.perform(get("/api/expense-trackers/" + tracker + "/access-requests?status=PENDING").header("Authorization", bearer(owner)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.content.length()", is(0)));
		mvc.perform(get("/api/roles/notifications").header("Authorization", bearer(member)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.decisions[*].context", hasItems("Request tracker")));
	}

	@Test
	void deletingAccountsCleansUpRequestsAndNullsDeletedDecisionMaker() throws Exception {
		String admin = login("admin@test.com", "admin123");
		String second = register("decision-maker@test.com");
		String secondId = JsonPath.read(getJson("/profile/me", second), "$.id");
		putJson("/api/roles/users/" + secondId, admin, "{\"roles\":[\"USER\",\"ADMIN\"],\"expectedRoles\":[\"USER\"]}").andExpect(status().isNoContent());
		String user = register("delete-requester@test.com");
		String request = JsonPath.read(postJson("/api/roles/requests", user, "{\"role\":\"ADMIN\"}")
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
		patch("/api/roles/requests/" + request + "/reject", second).andExpect(status().isOk());
		mvc.perform(delete("/profile/me").header("Authorization", bearer(second))).andExpect(status().isNoContent());
		mvc.perform(get("/api/roles/requests/mine").header("Authorization", bearer(user)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.content[0].decidedBy").doesNotExist());
		String owner = register("delete-tracker-owner@test.com");
		String tracker = JsonPath.read(postJson("/api/expense-trackers", owner, "{\"name\":\"Deletion tracker\"}")
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
		String userId = JsonPath.read(getJson("/profile/me", user), "$.id");
		putJson("/api/roles/trackers/" + tracker + "/users/" + userId, admin,
				"{\"roles\":[\"EXPENSETRACKER_MEMBER\"],\"expectedRoles\":[]}").andExpect(status().isNoContent());
		postJson("/api/expense-trackers/" + tracker + "/access-requests/owner", user, "{}").andExpect(status().isCreated());
		mvc.perform(delete("/profile/me").header("Authorization", bearer(user))).andExpect(status().isNoContent());
		mvc.perform(get("/api/expense-trackers/" + tracker + "/access-requests").header("Authorization", bearer(owner)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.content.length()", is(0)));
	}

	@Test
	void roleRequestsAndTrackerManagementRespectScopesAndProtectOwners() throws Exception {
		String admin = login("admin@test.com", "admin123");
		String owner = register("role-owner@test.com");
		String member = register("role-member@test.com");
		String outsider = register("role-outsider@test.com");
		String ownerId = JsonPath.read(getJson("/profile/me", owner), "$.id");
		String memberId = JsonPath.read(getJson("/profile/me", member), "$.id");
		String outsiderId = JsonPath.read(getJson("/profile/me", outsider), "$.id");
		String tracker = JsonPath.read(postJson("/api/expense-trackers", owner, "{\"name\":\"Roles tracker\"}")
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");

		mvc.perform(get("/profile").header("Authorization", bearer(member))).andExpect(status().isForbidden());
		postJson("/api/roles/requests", member, "{\"role\":\"EXPENSETRACKER_OWNER\"}").andExpect(status().isBadRequest());
		String globalRequest = JsonPath.read(postJson("/api/roles/requests", member, "{\"role\":\"ADMIN\"}")
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
		postJson("/api/roles/requests", member, "{\"role\":\"ADMIN\"}").andExpect(status().isBadRequest());
		patch("/api/roles/requests/" + globalRequest + "/cancel", outsider).andExpect(status().isBadRequest());
		patch("/api/roles/requests/" + globalRequest + "/approve", outsider).andExpect(status().isForbidden());
		patch("/api/roles/requests/" + globalRequest + "/approve", admin).andExpect(status().isOk());
		mvc.perform(get("/profile/me").header("Authorization", bearer(member)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.roles", hasItems("USER", "ADMIN")));
		// Existing token is checked against current database roles, not its old claim.
		mvc.perform(get("/profile").header("Authorization", bearer(member))).andExpect(status().isOk());
		putJson("/api/roles/users/" + memberId, admin, "{\"roles\":[\"USER\"],\"expectedRoles\":[\"USER\",\"ADMIN\"]}").andExpect(status().isNoContent());
		mvc.perform(get("/profile").header("Authorization", bearer(member))).andExpect(status().isForbidden());
		putJson("/api/roles/trackers/" + tracker + "/users/" + outsiderId, owner,
				"{\"roles\":[\"EXPENSETRACKER_MEMBER\"],\"expectedRoles\":[]}").andExpect(status().isBadRequest());
		putJson("/api/roles/trackers/" + tracker + "/users/" + outsiderId, admin,
				"{\"roles\":[\"EXPENSETRACKER_MEMBER\"],\"expectedRoles\":[]}").andExpect(status().isNoContent());
		mvc.perform(get("/api/expense-trackers/" + tracker).header("Authorization", bearer(outsider))).andExpect(status().isOk());

		String access = JsonPath.read(postJson("/api/expense-trackers/" + tracker + "/access-requests", member, "{}")
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
		patch("/api/expense-trackers/access-requests/" + access + "/approve", owner).andExpect(status().isOk());
		String roleRequest = JsonPath.read(postJson("/api/expense-trackers/" + tracker + "/access-requests/owner", member, "{}")
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
		patch("/api/expense-trackers/access-requests/" + roleRequest + "/approve", outsider).andExpect(status().isForbidden());
		patch("/api/expense-trackers/access-requests/" + roleRequest + "/approve", owner).andExpect(status().isOk());
		mvc.perform(get("/api/expense-trackers/mine").header("Authorization", bearer(member)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.content[0].roles", hasItems("EXPENSETRACKER_OWNER", "EXPENSETRACKER_MEMBER")))
				.andExpect(jsonPath("$.content[0].role", is("EXPENSETRACKER_OWNER")));
		putJson("/api/roles/trackers/" + tracker + "/users/" + ownerId, member, "{\"roles\":[],\"expectedRoles\":[\"EXPENSETRACKER_OWNER\"]}")
				.andExpect(status().isBadRequest());
		putJson("/api/roles/trackers/" + tracker + "/users/" + memberId, outsider, "{\"roles\":[],\"expectedRoles\":[\"EXPENSETRACKER_OWNER\",\"EXPENSETRACKER_MEMBER\"]}")
				.andExpect(status().isForbidden());
		putJson("/api/roles/trackers/" + tracker + "/users/" + memberId, owner, "{\"roles\":[],\"expectedRoles\":[\"EXPENSETRACKER_OWNER\",\"EXPENSETRACKER_MEMBER\"]}")
				.andExpect(status().isNoContent());
		mvc.perform(get("/api/expense-trackers/" + tracker).header("Authorization", bearer(member))).andExpect(status().isForbidden());
	}

	private String register(String email) throws Exception {
		postJson("/auth/register", null, "{\"firstName\":\"Role\",\"lastName\":\"Test\",\"email\":\"" + email + "\",\"password\":\"password123\"}")
				.andExpect(status().isCreated());
		return login(email, "password123");
	}
	private String login(String email, String password) throws Exception {
		String json = postJson("/auth/login", null, "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(json, "$.token");
	}
	private String getJson(String path, String token) throws Exception {
		return mvc.perform(get(path).header("Authorization", bearer(token))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}
	private ResultActions postJson(String path, String token, String json) throws Exception {
		var request = post(path).contentType(MediaType.APPLICATION_JSON).content(json);
		if (token != null) request.header("Authorization", bearer(token));
		return mvc.perform(request);
	}
	private ResultActions putJson(String path, String token, String json) throws Exception {
		return mvc.perform(put(path).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json));
	}
	private ResultActions patch(String path, String token) throws Exception {
		return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(path).header("Authorization", bearer(token)));
	}
	private String bearer(String token) { return "Bearer " + token; }
}
