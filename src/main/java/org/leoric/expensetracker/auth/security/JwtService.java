package org.leoric.expensetracker.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.config.SecurityProperties;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class JwtService {

	private final SecurityProperties securityProperties;

	public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
		Instant now = Instant.now();
		Instant expiration = now.plusMillis(securityProperties.jwt().expirationMs());

		List<String> authorities = userDetails.getAuthorities()
				.stream()
				.map(GrantedAuthority::getAuthority)
				.toList();

		Map<String, Object> claims = new HashMap<>(extraClaims);
		claims.put("authorities", authorities);

		return Jwts.builder()
				.claims(claims)
				.subject(userDetails.getUsername())
				.issuedAt(Date.from(now))
				.expiration(Date.from(expiration))
				.signWith(signingKey())
				.compact();
	}

	public Claims extractClaims(String token) {
		return Jwts.parser()
				.verifyWith(signingKey())
				.build()
				.parseSignedClaims(token)
				.getPayload();
	}

	public Instant extractExpirationInstant(String token) {
		Date expiration = extractClaims(token).getExpiration();
		return expiration != null ? expiration.toInstant() : null;
	}

	public boolean isTokenValid(Claims claims, UserDetails userDetails) {
		String username = claims.getSubject();
		Date expiration = claims.getExpiration();
		Instant exp = expiration != null ? expiration.toInstant() : null;
		return username != null
				&& username.equals(userDetails.getUsername())
				&& exp != null
				&& exp.isAfter(Instant.now());
	}

	private SecretKey signingKey() {
		byte[] keyBytes = Decoders.BASE64.decode(securityProperties.jwt().secretKey());
		return Keys.hmacShaKeyFor(keyBytes);
	}
}