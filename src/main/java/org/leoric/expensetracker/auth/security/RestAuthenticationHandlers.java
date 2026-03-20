package org.leoric.expensetracker.auth.security;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.jspecify.annotations.NonNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RestAuthenticationHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

	private final ObjectMapper objectMapper;

	@Override
	public void commence(
			@NonNull HttpServletRequest request,
			@NonNull HttpServletResponse response,
			@NonNull AuthenticationException authException
	) throws IOException {
		write(response, HttpStatus.UNAUTHORIZED, "Unauthorized");
	}

	@Override
	public void handle(
			@NonNull HttpServletRequest request,
			@NonNull HttpServletResponse response,
			@NonNull AccessDeniedException accessDeniedException
	) throws IOException {
		write(response, HttpStatus.FORBIDDEN, "Forbidden");
	}

	private void write(HttpServletResponse response, HttpStatus status, String message) throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		objectMapper.writeValue(
				response.getOutputStream(),
				Map.of(
						"timestamp", OffsetDateTime.now(ZoneOffset.UTC).toString(),
						"status", status.value(),
						"error", status.getReasonPhrase(),
						"message", message
				)
		);
	}
}