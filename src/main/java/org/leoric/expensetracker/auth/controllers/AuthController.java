package org.leoric.expensetracker.auth.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.auth.dto.AuthenticationRequestDto;
import org.leoric.expensetracker.auth.dto.AuthenticationResponseDto;
import org.leoric.expensetracker.auth.dto.RegistrationRequestDto;
import org.leoric.expensetracker.auth.services.interfaces.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;

	@PostMapping("/register")
	public ResponseEntity<Void> authRegister(@Valid @RequestBody RegistrationRequestDto request) {
		authService.authRegister(request);
		return ResponseEntity.status(HttpStatus.CREATED).build();
	}

	@PostMapping("/login")
	public ResponseEntity<AuthenticationResponseDto> authLogin(@Valid @RequestBody AuthenticationRequestDto request) {
		return ResponseEntity.ok(authService.authLogin(request));
	}
}