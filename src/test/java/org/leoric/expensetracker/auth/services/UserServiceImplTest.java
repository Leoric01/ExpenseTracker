package org.leoric.expensetracker.auth.services;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.leoric.expensetracker.auth.dto.AdminPasswordResetDto;
import org.leoric.expensetracker.auth.dto.UserInfoResponseDto;
import org.leoric.expensetracker.auth.dto.UserPasswordChangeDto;
import org.leoric.expensetracker.auth.dto.UserProfileUpdateDto;
import org.leoric.expensetracker.auth.dto.UserResponseFullDto;
import org.leoric.expensetracker.auth.mapstruct.UserMapper;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.auth.repositories.OneTimePasswordTokenRepository;
import org.leoric.expensetracker.auth.repositories.UserExpenseTrackerRoleRepository;
import org.leoric.expensetracker.auth.repositories.UserRepository;
import org.leoric.expensetracker.budget.repositories.BudgetPlanRepository;
import org.leoric.expensetracker.category.repositories.CategoryRepository;
import org.leoric.expensetracker.expensetracker.repositories.ExpenseTrackerAccessRequestRepository;
import org.leoric.expensetracker.expensetracker.repositories.ExpenseTrackerRepository;
import org.leoric.expensetracker.handler.exceptions.IncorrectCurrentPasswordException;
import org.leoric.expensetracker.handler.exceptions.NewPasswordDoesNotMatchException;
import org.leoric.expensetracker.recurring.repositories.RecurringBudgetTemplateRepository;
import org.leoric.expensetracker.recurring.repositories.RecurringTransactionTemplateRepository;
import org.leoric.expensetracker.transaction.repositories.TransactionRepository;
import org.leoric.expensetracker.wallet.repositories.WalletRepository;
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
	@Mock
	private ExpenseTrackerRepository expenseTrackerRepository;
	@Mock
	private ExpenseTrackerAccessRequestRepository accessRequestRepository;
	@Mock
	private UserExpenseTrackerRoleRepository userExpenseTrackerRoleRepository;
	@Mock
	private OneTimePasswordTokenRepository otpTokenRepository;
	@Mock
	private TransactionRepository transactionRepository;
	@Mock
	private WalletRepository walletRepository;
	@Mock
	private CategoryRepository categoryRepository;
	@Mock
	private BudgetPlanRepository budgetPlanRepository;
	@Mock
	private RecurringBudgetTemplateRepository recurringBudgetTemplateRepository;
	@Mock
	private RecurringTransactionTemplateRepository recurringTransactionTemplateRepository;

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

	// --- profileMe ---

	@Test
	void getCurrentUser_shouldReturnUserInfo() {
		var expected = new UserInfoResponseDto(userId, "john@test.com", "John", "Doe", List.of());

		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(userMapper.userToUserInfoResponseDto(user)).thenReturn(expected);

		UserInfoResponseDto result = userService.profileMe(user);

		assertThat(result).isNotNull();
		assertThat(result.email()).isEqualTo("john@test.com");
		assertThat(result.firstName()).isEqualTo("John");
	}

	@Test
	void getCurrentUser_shouldThrowWhenUserNotFound() {
		when(userRepository.findById(userId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> userService.profileMe(user))
				.isInstanceOf(RuntimeException.class)
				.hasMessage("User not found");
	}

	// --- profileUpdate ---

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

		UserResponseFullDto result = userService.profileUpdate(user, dto);

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

		assertThatThrownBy(() -> userService.profileUpdate(user, dto))
				.isInstanceOf(EntityNotFoundException.class);
	}

	// --- profileChangePassword ---

	@Test
	void changePassword_shouldChangePasswordSuccessfully() {
		var dto = new UserPasswordChangeDto("encoded-password", "newPassword1", "newPassword1");

		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("encoded-password", "encoded-password")).thenReturn(true);
		when(passwordEncoder.encode("newPassword1")).thenReturn("new-encoded");

		userService.profileChangePassword(user, dto);

		verify(userRepository).save(user);
		assertThat(user.getPassword()).isEqualTo("new-encoded");
	}

	@Test
	void changePassword_shouldThrowWhenCurrentPasswordIncorrect() {
		var dto = new UserPasswordChangeDto("wrong-password", "newPassword1", "newPassword1");

		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);

		assertThatThrownBy(() -> userService.profileChangePassword(user, dto))
				.isInstanceOf(IncorrectCurrentPasswordException.class)
				.hasMessage("Current password is incorrect");

		verify(userRepository, never()).save(any());
	}

	@Test
	void changePassword_shouldThrowWhenNewPasswordsDoNotMatch() {
		var dto = new UserPasswordChangeDto("encoded-password", "newPassword1", "different");

		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("encoded-password", "encoded-password")).thenReturn(true);

		assertThatThrownBy(() -> userService.profileChangePassword(user, dto))
				.isInstanceOf(NewPasswordDoesNotMatchException.class)
				.hasMessage("New password and confirmation do not match");

		verify(userRepository, never()).save(any());
	}

	@Test
	void changePassword_shouldThrowWhenUserNotFound() {
		var dto = new UserPasswordChangeDto("old", "new", "new");

		when(userRepository.findById(userId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> userService.profileChangePassword(user, dto))
				.isInstanceOf(EntityNotFoundException.class);
	}

	// --- adminResetPassword ---

	@Test
	void adminResetPassword_shouldChangePasswordSuccessfully() {
		var dto = new AdminPasswordResetDto("john@test.com", "newPassword1");

		when(userRepository.findByEmail("john@test.com")).thenReturn(Optional.of(user));
		when(passwordEncoder.encode("newPassword1")).thenReturn("new-encoded");

		userService.adminResetPassword(dto);

		verify(userRepository).save(user);
		assertThat(user.getPassword()).isEqualTo("new-encoded");
	}

	@Test
	void adminResetPassword_shouldThrowWhenUserNotFound() {
		var dto = new AdminPasswordResetDto("missing@test.com", "newPassword1");

		when(userRepository.findByEmail("missing@test.com")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> userService.adminResetPassword(dto))
				.isInstanceOf(EntityNotFoundException.class)
				.hasMessage("User not found");

		verify(userRepository, never()).save(any());
	}
}