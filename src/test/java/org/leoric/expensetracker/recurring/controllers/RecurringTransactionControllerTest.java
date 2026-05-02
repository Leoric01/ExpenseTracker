package org.leoric.expensetracker.recurring.controllers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.budget.models.constants.PeriodType;
import org.leoric.expensetracker.expensetracker.services.interfaces.ExpenseTrackerAccessService;
import org.leoric.expensetracker.recurring.dto.CreateRecurringTransactionRequestDto;
import org.leoric.expensetracker.recurring.dto.RecurringTransactionResponseDto;
import org.leoric.expensetracker.recurring.dto.UpdateRecurringTransactionRequestDto;
import org.leoric.expensetracker.recurring.services.interfaces.RecurringTransactionService;
import org.leoric.expensetracker.transaction.models.constants.TransactionType;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_MEMBER;
import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_OWNER;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecurringTransactionControllerTest {

	@Mock
	private RecurringTransactionService recurringTransactionService;
	@Mock
	private ExpenseTrackerAccessService expenseTrackerAccessService;

	@InjectMocks
	private RecurringTransactionController controller;

	private final User currentUser = User.builder().id(UUID.randomUUID()).email("test@example.com").build();

	@Test
	void recurringTransactionCreate_shouldReturnCreated() {
		UUID trackerId = UUID.randomUUID();
		CreateRecurringTransactionRequestDto request = new CreateRecurringTransactionRequestDto(
				TransactionType.EXPENSE,
				UUID.randomUUID(),
				UUID.randomUUID(),
				1_000L,
				"CZK",
				"Desc",
				null,
				PeriodType.MONTHLY,
				1,
				LocalDate.of(2026, 5, 1),
				null
		);
		RecurringTransactionResponseDto expected = responseDto();
		when(recurringTransactionService.recurringTransactionCreate(currentUser, trackerId, request)).thenReturn(expected);

		ResponseEntity<RecurringTransactionResponseDto> response = controller.recurringTransactionCreate(currentUser, trackerId, request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(response.getBody()).isEqualTo(expected);
	}

	@Test
	void recurringTransactionFindAll_shouldReturnOk() {
		UUID trackerId = UUID.randomUUID();
		Pageable pageable = PageRequest.of(0, 10);
		Page<RecurringTransactionResponseDto> expected = new PageImpl<>(List.of(responseDto()));
		when(recurringTransactionService.recurringTransactionFindAll(currentUser, trackerId, "rent", pageable)).thenReturn(expected);

		ResponseEntity<Page<RecurringTransactionResponseDto>> response = controller.recurringTransactionFindAll(currentUser, trackerId, "rent", pageable);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isEqualTo(expected);
	}

	@Test
	void recurringTransactionFindAllActive_shouldReturnOk() {
		UUID trackerId = UUID.randomUUID();
		Pageable pageable = PageRequest.of(0, 10);
		Page<RecurringTransactionResponseDto> expected = new PageImpl<>(List.of(responseDto()));
		when(recurringTransactionService.recurringTransactionFindAllActive(currentUser, trackerId, "rent", pageable)).thenReturn(expected);

		ResponseEntity<Page<RecurringTransactionResponseDto>> response = controller.recurringTransactionFindAllActive(currentUser, trackerId, "rent", pageable);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isEqualTo(expected);
	}

	@Test
	void recurringTransactionFindById_shouldReturnOk() {
		UUID trackerId = UUID.randomUUID();
		UUID templateId = UUID.randomUUID();
		RecurringTransactionResponseDto expected = responseDto();
		when(recurringTransactionService.recurringTransactionFindById(currentUser, trackerId, templateId)).thenReturn(expected);

		ResponseEntity<RecurringTransactionResponseDto> response = controller.recurringTransactionFindById(currentUser, trackerId, templateId);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isEqualTo(expected);
	}

	@Test
	void recurringTransactionUpdate_shouldRequireOwner() {
		UUID trackerId = UUID.randomUUID();
		UUID templateId = UUID.randomUUID();
		UpdateRecurringTransactionRequestDto request = new UpdateRecurringTransactionRequestDto(
				UUID.randomUUID(),
				UUID.randomUUID(),
				2_000L,
				"USD",
				"desc",
				null,
				PeriodType.MONTHLY,
				1,
				LocalDate.of(2026, 5, 1),
				null
		);
		RecurringTransactionResponseDto expected = responseDto();
		when(recurringTransactionService.recurringTransactionUpdate(currentUser, trackerId, templateId, request)).thenReturn(expected);

		ResponseEntity<RecurringTransactionResponseDto> response = controller.recurringTransactionUpdate(currentUser, trackerId, templateId, request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isEqualTo(expected);
		verify(expenseTrackerAccessService).assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER);
	}

	@Test
	void recurringTransactionDeactivate_shouldRequireOwnerAndReturnNoContent() {
		UUID trackerId = UUID.randomUUID();
		UUID templateId = UUID.randomUUID();

		ResponseEntity<Void> response = controller.recurringTransactionDeactivate(currentUser, trackerId, templateId);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		verify(expenseTrackerAccessService).assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER);
		verify(recurringTransactionService).recurringTransactionDeactivate(currentUser, trackerId, templateId);
	}

	@Test
	void recurringTransactionCreate_shouldCheckOwnerOrMember() {
		UUID trackerId = UUID.randomUUID();
		CreateRecurringTransactionRequestDto request = new CreateRecurringTransactionRequestDto(
				TransactionType.EXPENSE,
				UUID.randomUUID(),
				null,
				1_000L,
				"CZK",
				null,
				null,
				PeriodType.MONTHLY,
				1,
				LocalDate.of(2026, 5, 1),
				null
		);
		when(recurringTransactionService.recurringTransactionCreate(currentUser, trackerId, request)).thenReturn(responseDto());

		controller.recurringTransactionCreate(currentUser, trackerId, request);

		verify(expenseTrackerAccessService).assertHasRoleOnExpenseTracker(
				trackerId,
				currentUser,
				EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER
		);
	}

	private RecurringTransactionResponseDto responseDto() {
		return new RecurringTransactionResponseDto(
				UUID.randomUUID(),
				TransactionType.EXPENSE,
				UUID.randomUUID(),
				"Wallet",
				UUID.randomUUID(),
				"Food",
				1_000L,
				"CZK",
				2,
				"desc",
				null,
				PeriodType.MONTHLY,
				1,
				LocalDate.of(2026, 5, 1),
				null,
				LocalDate.of(2026, 6, 1),
				true,
				null,
				null
		);
	}
}