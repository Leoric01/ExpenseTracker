package org.leoric.expensetracker.auth.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.leoric.expensetracker.auth.dto.AuthenticationRequestDto;
import org.leoric.expensetracker.auth.dto.AuthenticationResponseDto;
import org.leoric.expensetracker.auth.dto.RegistrationRequestDto;
import org.leoric.expensetracker.auth.models.Role;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.auth.repositories.RoleRepository;
import org.leoric.expensetracker.auth.repositories.UserRepository;
import org.leoric.expensetracker.auth.security.JwtService;
import org.leoric.expensetracker.auth.services.interfaces.AuthService;
import org.leoric.expensetracker.handler.exceptions.EmailAlreadyInUseException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;

import static org.leoric.expensetracker.ExpenseTrackerApplication.ADMIN;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final AuthenticationManager authenticationManager;

	public void authRegister(RegistrationRequestDto request) {
		if (userRepository.existsByEmail(request.email())) {
			throw new EmailAlreadyInUseException("Email already in use");
		}

		Role adminRole = roleRepository.findByName(ADMIN)
				.orElseThrow(() -> new IllegalStateException("Default role ADMIN not found"));

		User user = User.builder()
				.firstName(request.firstName())
				.lastName(request.lastName())
				.email(request.email())
				.password(passwordEncoder.encode(request.password()))
				.roles(List.of(adminRole))
				.enabled(true)
				.accountLocked(false)
				.build();

		userRepository.save(user);
	}

	public AuthenticationResponseDto authLogin(AuthenticationRequestDto request) {
		Authentication authentication = authenticationManager.authenticate(
				new UsernamePasswordAuthenticationToken(request.email(), request.password())
		);

		User user = (User) authentication.getPrincipal();
		if (user == null) {
			throw new IllegalStateException("Authentication succeeded but principal is null");
		}
		String token = jwtService.generateToken(new HashMap<>(), user);
		return new AuthenticationResponseDto(token);
	}
}