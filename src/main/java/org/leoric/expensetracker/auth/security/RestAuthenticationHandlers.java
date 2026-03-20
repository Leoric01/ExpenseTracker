package org.leoric.expensetracker.auth.security;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.leoric.expensetracker.handler.BusinessErrorCodes;
import org.leoric.expensetracker.handler.ExceptionResponse;
import org.springframework.http.MediaType;
import org.jspecify.annotations.NonNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static org.leoric.expensetracker.handler.BusinessErrorCodes.BAD_CREDENTIALS;
import static org.leoric.expensetracker.handler.BusinessErrorCodes.INSUFFICIENT_ROLE;

@Slf4j
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
		log.warn("[{}] {}", BAD_CREDENTIALS.getCode(), authException.getMessage());
		write(response, BAD_CREDENTIALS, authException.getMessage());
	}

	@Override
	public void handle(
			@NonNull HttpServletRequest request,
			@NonNull HttpServletResponse response,
			@NonNull AccessDeniedException accessDeniedException
	) throws IOException {
		log.warn("[{}] {}", INSUFFICIENT_ROLE.getCode(), accessDeniedException.getMessage());
		write(response, INSUFFICIENT_ROLE, accessDeniedException.getMessage());
	}

	private void write(HttpServletResponse response, BusinessErrorCodes code, String errorMessage) throws IOException {
		response.setStatus(code.getHttpStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		objectMapper.writeValue(
				response.getOutputStream(),
				ExceptionResponse.builder()
						.businessErrorCode(code.getCode())
						.businessErrorDescription(code.getDescription())
						.error(errorMessage)
						.build()
		);
	}
}