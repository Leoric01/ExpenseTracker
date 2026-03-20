package org.leoric.expensetracker.expensetracker.services;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.leoric.expensetracker.auth.models.Role;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.auth.repositories.RoleRepository;
import org.leoric.expensetracker.auth.repositories.UserRepository;
import org.leoric.expensetracker.expensetracker.dto.CreateExpenseTrackerRequest;
import org.leoric.expensetracker.expensetracker.dto.ExpenseTrackerResponse;
import org.leoric.expensetracker.expensetracker.mapstruct.ExpenseTrackerMapper;
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.leoric.expensetracker.expensetracker.repositories.ExpenseTrackerRepository;
import org.leoric.expensetracker.handler.exceptions.DuplicateExpenseTrackerNameException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpenseTrackerServiceImplTest {

	@Mock
	private ExpenseTrackerRepository expenseTrackerRepository;
	@Mock
	private ExpenseTrackerMapper expenseTrackerMapper;
	@Mock
	private RoleRepository roleRepository;
	@Mock
	private UserRepository userRepository;

	@InjectMocks
	private ExpenseTrackerServiceImpl expenseTrackerService;

	private UUID userId;
	private User user;
	private Role ownerRole;

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

		ownerRole = Role.builder().id(1).name("EXPENSETRACKER_OWNER").build();
	}

	// --- expenseTrackerCreate ---

	@Test
	void create_shouldCreateExpenseTrackerSuccessfully() {
		var request = new CreateExpenseTrackerRequest("Personal", "My tracker", "CZK");
		var response = new ExpenseTrackerResponse(
				UUID.randomUUID(), "Personal", "My tracker", "CZK",
				true, "John Doe", OffsetDateTime.now(), OffsetDateTime.now()
		);

		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(expenseTrackerRepository.existsByCreatedByOwnerIdAndNameIgnoreCase(userId, "Personal")).thenReturn(false);
		when(roleRepository.findByName("EXPENSETRACKER_OWNER")).thenReturn(Optional.of(ownerRole));
		when(expenseTrackerRepository.save(any(ExpenseTracker.class))).thenAnswer(inv -> inv.getArgument(0));
		when(expenseTrackerMapper.toResponse(any(ExpenseTracker.class))).thenReturn(response);

		ExpenseTrackerResponse result = expenseTrackerService.expenseTrackerCreate(user, request);

		assertThat(result).isNotNull();
		assertThat(result.name()).isEqualTo("Personal");
		verify(expenseTrackerRepository).save(any(ExpenseTracker.class));
	}

	@Test
	void create_shouldThrowWhenNameAlreadyExistsForUser() {
		var request = new CreateExpenseTrackerRequest("Personal", "My tracker", "CZK");

		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(expenseTrackerRepository.existsByCreatedByOwnerIdAndNameIgnoreCase(userId, "Personal")).thenReturn(true);

		assertThatThrownBy(() -> expenseTrackerService.expenseTrackerCreate(user, request))
				.isInstanceOf(DuplicateExpenseTrackerNameException.class)
				.hasMessageContaining("Personal");

		verify(expenseTrackerRepository, never()).save(any());
	}

	@Test
	void create_shouldThrowWhenUserNotFound() {
		var request = new CreateExpenseTrackerRequest("Personal", "My tracker", "CZK");

		when(userRepository.findById(userId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> expenseTrackerService.expenseTrackerCreate(user, request))
				.isInstanceOf(EntityNotFoundException.class);

		verify(expenseTrackerRepository, never()).save(any());
	}
}