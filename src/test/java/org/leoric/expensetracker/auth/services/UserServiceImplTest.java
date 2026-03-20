package org.leoric.expensetracker.auth.services;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.leoric.expensetracker.auth.dto.UserInfoResponse;
import org.leoric.expensetracker.auth.dto.UserPasswordChangeDto;
import org.leoric.expensetracker.auth.dto.UserProfileUpdateDto;
import org.leoric.expensetracker.auth.dto.UserResponseFullDto;
import org.leoric.expensetracker.auth.mapstruct.UserMapper;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.auth.repositories.UserRepository;
import org.leoric.expensetracker.handler.exceptions.IncorrectCurrentPasswordException;
import org.leoric.expensetracker.handler.exceptions.NewPasswordDoesNotMatchException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

	@Mock
	private UserMapper userMapper;
	@Mock
	private UserRepository userRepository;
	@Mock
	private PasswordEncoder passwordEncoder;

	@InjectMocks
	private UserServiceImpl userService;

	private UUID userId;
	private User user;

	@BeforeEach
	void setUp() {
		userId = UUID.randomUUID();
		user = User.builder()
				.id(userId)
				.email("john@test.com")
				.firstName("John")
				.lastName("Doe")
				.password("encoded-password")
				.enabled(true)
				.accountLocked(false)
				.roles(List.of())
				.build();
	}

	// --- getCurrentUser ---

	@Test
	void getCurrentUser_shouldReturnUserInfo() {
		var expected = new UserInfoResponse(userId, "john@test.com", "John", "Doe", List.of());

		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(userMapper.userToUserInfoResponse(user)).thenReturn(expected);

		UserInfoResponse result = userService.getCurrentUser(user);

		assertThat(result).isNotNull();
		assertThat(result.email()).isEqualTo("john@test.com");
		assertThat(result.firstName()).isEqualTo("John");
	}

	@Test
	void getCurrentUser_shouldThrowWhenUserNotFound() {
		when(userRepository.findById(userId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> userService.getCurrentUser(user))
				.isInstanceOf(RuntimeException.class)
				.hasMessage("User not found");
	}

	// --- updateProfile ---

	@Test
	void updateProfile_shouldUpdateAndReturnFullDto() {
		var dto = new UserProfileUpdateDto("Jane", "Smith");
		var expected = new UserResponseFullDto(
				userId, "john@test.com", "Jane", "Smith",
				List.of(), OffsetDateTime.now(), OffsetDateTime.now()
		);

		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(userRepository.save(any(User.class))).thenReturn(user);
		when(userMapper.userToUserResponseFull(user)).thenReturn(expected);

		UserResponseFullDto result = userService.updateProfile(user, dto);

		assertThat(result).isNotNull();
		assertThat(result.firstName()).isEqualTo("Jane");
		assertThat(result.lastName()).isEqualTo("Smith");
		verify(userMapper).updateUserFromDto(dto, user);
		verify(userRepository).save(user);
	}

	@Test
	void updateProfile_shouldThrowWhenUserNotFound() {
		var dto = new UserProfileUpdateDto("Jane", null);

		when(userRepository.findById(userId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> userService.updateProfile(user, dto))
				.isInstanceOf(EntityNotFoundException.class);
	}

	// --- changePassword ---

	@Test
	void changePassword_shouldChangePasswordSuccessfully() {
		var dto = new UserPasswordChangeDto("encoded-password", "newPassword1", "newPassword1");

		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("encoded-password", "encoded-password")).thenReturn(true);
		when(passwordEncoder.encode("newPassword1")).thenReturn("new-encoded");

		userService.changePassword(user, dto);

		verify(userRepository).save(user);
		assertThat(user.getPassword()).isEqualTo("new-encoded");
	}

	@Test
	void changePassword_shouldThrowWhenCurrentPasswordIncorrect() {
		var dto = new UserPasswordChangeDto("wrong-password", "newPassword1", "newPassword1");

		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);

		assertThatThrownBy(() -> userService.changePassword(user, dto))
				.isInstanceOf(IncorrectCurrentPasswordException.class)
				.hasMessage("Current password is incorrect");

		verify(userRepository, never()).save(any());
	}

	@Test
	void changePassword_shouldThrowWhenNewPasswordsDoNotMatch() {
		var dto = new UserPasswordChangeDto("encoded-password", "newPassword1", "different");

		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("encoded-password", "encoded-password")).thenReturn(true);

		assertThatThrownBy(() -> userService.changePassword(user, dto))
				.isInstanceOf(NewPasswordDoesNotMatchException.class)
				.hasMessage("New password and confirmation do not match");

		verify(userRepository, never()).save(any());
	}

	@Test
	void changePassword_shouldThrowWhenUserNotFound() {
		var dto = new UserPasswordChangeDto("old", "new", "new");

		when(userRepository.findById(userId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> userService.changePassword(user, dto))
				.isInstanceOf(EntityNotFoundException.class);
	}
}