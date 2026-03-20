package org.leoric.expensetracker.auth.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.leoric.expensetracker.auth.models.Role;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.config.SecurityProperties;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

	private JwtService jwtService;
	private User testUser;

	private static final String SECRET_KEY = "8OnMwU91F8/rWb/AWvjd2UN5TxFcrpQFlPVzZNPDhiQ9l7W14WTdfeGx9c/Phv1mgHJDFqAyRQgFuDV1YEbRjQ==";

	@BeforeEach
	void setUp() {
		var jwt = new SecurityProperties.Jwt(SECRET_KEY, 86400000L);
		var cors = new SecurityProperties.Cors(List.of(), List.of(), List.of());
		var props = new SecurityProperties(jwt, cors);
		jwtService = new JwtService(props);

		Role userRole = Role.builder().id(1).name("USER").build();
		testUser = User.builder()
				.id(UUID.randomUUID())
				.email("test@test.com")
				.firstName("Test")
				.lastName("User")
				.password("encoded")
				.enabled(true)
				.accountLocked(false)
				.roles(List.of(userRole))
				.build();
	}

	@Test
	void generateToken_shouldReturnNonNullToken() {
		String token = jwtService.generateToken(new HashMap<>(), testUser);

		assertThat(token).isNotNull().isNotBlank();
	}

	@Test
	void extractClaims_shouldReturnCorrectSubject() {
		String token = jwtService.generateToken(new HashMap<>(), testUser);

		Claims claims = jwtService.extractClaims(token);

		assertThat(claims.getSubject()).isEqualTo("test@test.com");
	}

	@Test
	void extractClaims_shouldContainAuthorities() {
		String token = jwtService.generateToken(new HashMap<>(), testUser);

		Claims claims = jwtService.extractClaims(token);

		@SuppressWarnings("unchecked")
		List<String> authorities = claims.get("authorities", List.class);
		assertThat(authorities).containsExactly("USER");
	}

	@Test
	void extractClaims_shouldContainCustomClaims() {
		var extraClaims = new HashMap<String, Object>();
		extraClaims.put("custom-key", "custom-value");

		String token = jwtService.generateToken(extraClaims, testUser);
		Claims claims = jwtService.extractClaims(token);

		assertThat(claims.get("custom-key")).isEqualTo("custom-value");
	}

	@Test
	void isTokenValid_shouldReturnTrueForValidToken() {
		String token = jwtService.generateToken(new HashMap<>(), testUser);
		Claims claims = jwtService.extractClaims(token);

		boolean valid = jwtService.isTokenValid(claims, testUser);

		assertThat(valid).isTrue();
	}

	@Test
	void isTokenValid_shouldReturnFalseForWrongUser() {
		String token = jwtService.generateToken(new HashMap<>(), testUser);
		Claims claims = jwtService.extractClaims(token);

		User otherUser = User.builder()
				.id(UUID.randomUUID())
				.email("other@test.com")
				.password("encoded")
				.enabled(true)
				.build();

		boolean valid = jwtService.isTokenValid(claims, otherUser);

		assertThat(valid).isFalse();
	}

	@Test
	void isTokenValid_shouldReturnFalseForExpiredToken() {
		// Create a JwtService with 0ms expiration
		var jwt = new SecurityProperties.Jwt(SECRET_KEY, 0L);
		var cors = new SecurityProperties.Cors(List.of(), List.of(), List.of());
		var props = new SecurityProperties(jwt, cors);
		var expiredJwtService = new JwtService(props);

		String token = expiredJwtService.generateToken(new HashMap<>(), testUser);

		// Token expired immediately, extractClaims should throw ExpiredJwtException
		org.assertj.core.api.Assertions.assertThatThrownBy(() ->
				expiredJwtService.extractClaims(token)
		).isInstanceOf(io.jsonwebtoken.ExpiredJwtException.class);
	}

	@Test
	void extractExpirationInstant_shouldReturnFutureInstant() {
		String token = jwtService.generateToken(new HashMap<>(), testUser);

		Instant expiration = jwtService.extractExpirationInstant(token);

		assertThat(expiration).isNotNull();
		assertThat(expiration).isAfter(Instant.now());
	}
}