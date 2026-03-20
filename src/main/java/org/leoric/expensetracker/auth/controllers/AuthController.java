package org.leoric.expensetracker.auth.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.auth.dto.AuthenticationRequest;
import org.leoric.expensetracker.auth.dto.AuthenticationResponse;
import org.leoric.expensetracker.auth.dto.RegistrationRequest;
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
	public ResponseEntity<AuthenticationResponse> register(@Valid @RequestBody RegistrationRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
	}

	@PostMapping("/login")
	public ResponseEntity<AuthenticationResponse> login(@Valid @RequestBody AuthenticationRequest request) {
		return ResponseEntity.ok(authService.authenticate(request));
	}
}