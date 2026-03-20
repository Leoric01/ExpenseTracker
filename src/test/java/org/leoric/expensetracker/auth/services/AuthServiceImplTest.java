package org.leoric.expensetracker.auth.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.leoric.expensetracker.auth.dto.AuthenticationRequest;
import org.leoric.expensetracker.auth.dto.AuthenticationResponse;
import org.leoric.expensetracker.auth.dto.RegistrationRequest;
import org.leoric.expensetracker.auth.models.Role;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.auth.repositories.RoleRepository;
import org.leoric.expensetracker.auth.repositories.UserRepository;
import org.leoric.expensetracker.auth.security.JwtService;
import org.leoric.expensetracker.handler.exceptions.EmailAlreadyInUseException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

	@Mock
	private UserRepository userRepository;
	@Mock
	private RoleRepository roleRepository;
	@Mock
	private PasswordEncoder passwordEncoder;
	@Mock
	private JwtService jwtService;
	@Mock
	private AuthenticationManager authenticationManager;

	@InjectMocks
	private AuthServiceImpl authService;

	private Role adminRole;

	@BeforeEach
	void setUp() {
		adminRole = Role.builder().id(1).name("ADMIN").build();
	}

	@Test
	void register_shouldCreateUser() {
		var request = new RegistrationRequest("John", "Doe", "john@test.com", "password123");

		when(userRepository.existsByEmail("john@test.com")).thenReturn(false);
		when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(adminRole));
		when(passwordEncoder.encode("password123")).thenReturn("encoded-password");

		authService.authRegister(request);

		verify(userRepository).save(any(User.class));
		verify(passwordEncoder).encode("password123");
		verify(jwtService, never()).generateToken(anyMap(), any());
	}

	@Test
	void register_shouldThrowWhenEmailAlreadyExists() {
		var request = new RegistrationRequest("John", "Doe", "existing@test.com", "password123");

		when(userRepository.existsByEmail("existing@test.com")).thenReturn(true);

		assertThatThrownBy(() -> authService.authRegister(request))
				.isInstanceOf(EmailAlreadyInUseException.class)
				.hasMessage("Email already in use");

		verify(userRepository, never()).save(any());
	}

	@Test
	void register_shouldThrowWhenAdminRoleNotFound() {
		var request = new RegistrationRequest("John", "Doe", "john@test.com", "password123");

		when(userRepository.existsByEmail("john@test.com")).thenReturn(false);
		when(roleRepository.findByName("ADMIN")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> authService.authRegister(request))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Default role ADMIN not found");
	}

	@Test
	void authenticate_shouldReturnTokenForValidCredentials() {
		var request = new AuthenticationRequest("john@test.com", "password123");
		var user = User.builder()
				.id(UUID.randomUUID())
				.email("john@test.com")
				.password("encoded")
				.enabled(true)
				.build();

		var authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
		when(authenticationManager.authenticate(any())).thenReturn(authentication);
		when(jwtService.generateToken(anyMap(), any(User.class))).thenReturn("jwt-token");

		AuthenticationResponse response = authService.authLogin(request);

		assertThat(response).isNotNull();
		assertThat(response.token()).isEqualTo("jwt-token");
	}

	@Test
	void authenticate_shouldThrowForBadCredentials() {
		var request = new AuthenticationRequest("john@test.com", "wrong-password");

		when(authenticationManager.authenticate(any()))
				.thenThrow(new BadCredentialsException("Bad credentials"));

		assertThatThrownBy(() -> authService.authLogin(request))
				.isInstanceOf(BadCredentialsException.class);
	}
}