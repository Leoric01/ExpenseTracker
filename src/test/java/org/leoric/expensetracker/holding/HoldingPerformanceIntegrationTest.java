package org.leoric.expensetracker.holding;

import org.junit.jupiter.api.Test;
import org.leoric.expensetracker.asset.models.Asset;
import org.leoric.expensetracker.asset.models.constants.AssetType;
import org.leoric.expensetracker.asset.models.constants.MarketDataSource;
import org.leoric.expensetracker.asset.repositories.AssetRepository;
import org.leoric.expensetracker.auth.repositories.RoleRepository;
import org.leoric.expensetracker.auth.repositories.UserRepository;
import org.leoric.expensetracker.exchangerate.services.interfaces.ExchangeRateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:holdingPerformance;MODE=MariaDB;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class HoldingPerformanceIntegrationTest {
	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired AssetRepository assets;
	@Autowired UserRepository users;
	@Autowired RoleRepository roles;
	@MockitoBean ExchangeRateService rates;

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		String url = System.getenv("HOLDING_TEST_MARIADB_URL");
		if (url != null && !url.isBlank()) {
			registry.add("spring.datasource.url", () -> url);
			registry.add("spring.datasource.driver-class-name", () -> "org.mariadb.jdbc.Driver");
			registry.add("spring.datasource.username", () -> "holding_test");
			registry.add("spring.datasource.password", () -> "holding_test");
			registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
		}
	}

	@Test
	void openingAndManualCostArePersistedAndDashboardKeepsPeriodAndInvestmentSeparate() throws Exception {
		Asset czk = asset("CZK", 2, AssetType.FIAT);
		Asset btc = asset("BTC", 8, AssetType.CRYPTO);
		when(rates.convertAmount(anyLong(), any(Asset.class), any(Asset.class), any(Instant.class)))
				.thenAnswer(call -> call.<Long>getArgument(0) / 100);
		when(rates.convertAmount(anyLong(), any(Asset.class), any(Asset.class), any(LocalDate.class)))
				.thenAnswer(call -> call.<Long>getArgument(0) / 100);
		when(rates.getRate(any(Asset.class), any(Asset.class), any(LocalDate.class)))
				.thenReturn(new BigDecimal("10000"));

		String token = registerAndLogin();
		String tracker = id(postJson("/api/expense-trackers", token,
				"{\"name\":\"Portfolio\",\"preferredDisplayAssetId\":\"" + czk.getId() + "\"}"));
		String institution = id(postJson("/api/institution/" + tracker, token,
				"{\"name\":\"Exchange\",\"institutionType\":\"EXCHANGE\"}"));
		String account = id(postJson("/api/account/" + tracker, token,
				"{\"institutionId\":\"" + institution + "\",\"name\":\"Wallet\",\"accountType\":\"EXCHANGE_SPOT\"}"));
		String holding = id(postJson("/api/holding/" + tracker, token,
				"{\"accountId\":\"" + account + "\",\"assetId\":\"" + btc.getId()
						+ "\",\"initialAmount\":100000000,\"initialValuationMode\":\"MARKET\"}"));

		String url = "/api/holding/" + tracker + "/" + holding + "/initial-valuation";
		mvc.perform(patch(url).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"mode\":\"MANUAL\",\"amount\":900000,\"assetId\":\"" + czk.getId()
						+ "\",\"valuationAt\":\"2024-01-01T00:00:00Z\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.initialValuationAmount", is(900000)));
		mvc.perform(patch(url).contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"MARKET\"}"))
				.andExpect(status().isUnauthorized());
		String outsider = registerAndLogin();
		mvc.perform(patch(url).header("Authorization", "Bearer " + outsider)
				.contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"MARKET\"}"))
				.andExpect(status().isForbidden());

		mvc.perform(get("/api/institution/" + tracker + "/dashboard")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.investmentPerformance.total", is(100000)))
				.andExpect(jsonPath("$.periodPerformance.result", is(0)))
				.andExpect(jsonPath("$.institutions[0].investmentPerformance.total", is(100000)))
				.andExpect(jsonPath("$.institutions[0].periodPerformance.result", is(0)))
				.andExpect(jsonPath("$.institutions[0].accounts[0].investmentPerformance.total", is(100000)))
				.andExpect(jsonPath("$.institutions[0].accounts[0].periodPerformance.result", is(0)))
				.andExpect(jsonPath("$.institutions[0].accounts[0].holdings[0].startBalance", is(0)))
				.andExpect(jsonPath("$.institutions[0].accounts[0].holdings[0].convertedEndBalance", is(1000000)))
				.andExpect(jsonPath("$.institutions[0].accounts[0].holdings[0].periodPerformance.convertedIncome", is(1000000)))
				.andExpect(jsonPath("$.institutions[0].accounts[0].holdings[0].periodPerformance.result", is(0)));
		mvc.perform(get("/api/holding/" + tracker + "/" + holding + "/summary")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.investmentPerformance.total", is(100000)))
				.andExpect(jsonPath("$.periodPerformance.convertedIncome", is(1000000)))
				.andExpect(jsonPath("$.periodPerformance.result", is(0)));

		String transaction = id(postJson("/api/transaction/" + tracker, token,
				"{\"transactionType\":\"TEST\",\"holdingId\":\"" + holding
						+ "\",\"amount\":10000000,\"currencyCode\":\"BTC\",\"transactionDate\":\"" + Instant.now() + "\"}"));
		mvc.perform(patch("/api/transaction/" + tracker + "/" + transaction)
				.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"recalculateBalanceEffect\":true}"))
				.andExpect(status().isOk());
		String valuationUrl = "/api/transaction/" + tracker + "/" + transaction + "/valuation";
		mvc.perform(get(valuationUrl).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.baseAssetCode", is("BTC")))
				.andExpect(jsonPath("$.quoteAssetCode", is("CZK")));
		mvc.perform(patch(valuationUrl).header("Authorization", "Bearer " + outsider)
				.contentType(MediaType.APPLICATION_JSON).content("{\"rate\":20000}"))
				.andExpect(status().isForbidden());
		mvc.perform(patch(valuationUrl).header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"rate\":0}"))
				.andExpect(status().isBadRequest());
		mvc.perform(patch(valuationUrl).header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"rate\":20000}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.source", is("MANUAL")));
		mvc.perform(get("/api/holding/" + tracker + "/" + holding + "/summary")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.investmentPerformance.realized", is(110000)))
				.andExpect(jsonPath("$.investmentPerformance.unrealized", is(90000)));
	}

	private Asset asset(String code, int scale, AssetType type) {
		return assets.findByCodeIgnoreCase(code).orElseGet(() -> assets.saveAndFlush(Asset.builder()
				.code(code).name(code).assetType(type).scale(scale).marketDataSource(MarketDataSource.NONE).build()));
	}

	private String registerAndLogin() throws Exception {
		String email = UUID.randomUUID() + "@test.com";
		mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"firstName\":\"Test\",\"lastName\":\"Portfolio\",\"email\":\"" + email
						+ "\",\"password\":\"password123\"}")).andExpect(status().isCreated());
		var user = users.findByEmail(email).orElseThrow();
		user.setRoles(new ArrayList<>(List.of(roles.findByName("USER").orElseThrow())));
		users.saveAndFlush(user);
		MvcResult result = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
				.andExpect(status().isOk()).andReturn();
		return json.readTree(result.getResponse().getContentAsString()).get("token").asString();
	}

	private MvcResult postJson(String url, String token, String content) throws Exception {
		return mvc.perform(post(url).header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content(content))
				.andExpect(status().isCreated()).andReturn();
	}

	private String id(MvcResult result) throws Exception {
		return json.readTree(result.getResponse().getContentAsString()).get("id").asString();
	}
}
