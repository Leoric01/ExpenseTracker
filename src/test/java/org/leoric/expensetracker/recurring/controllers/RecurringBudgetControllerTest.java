package org.leoric.expensetracker.recurring.controllers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.budget.models.constants.PeriodType;
import org.leoric.expensetracker.expensetracker.services.interfaces.ExpenseTrackerAccessService;
import org.leoric.expensetracker.recurring.dto.CreateRecurringBudgetRequestDto;
import org.leoric.expensetracker.recurring.dto.RecurringBudgetResponseDto;
import org.leoric.expensetracker.recurring.dto.SyncRecurringBudgetResponseDto;
import org.leoric.expensetracker.recurring.dto.UpdateRecurringBudgetRequestDto;
import org.leoric.expensetracker.recurring.services.interfaces.RecurringBudgetService;
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
class RecurringBudgetControllerTest {

	@Mock
	private RecurringBudgetService recurringBudgetService;
	@Mock
	private ExpenseTrackerAccessService expenseTrackerAccessService;

	@InjectMocks
	private RecurringBudgetController controller;

	private final User currentUser = User.builder().id(UUID.randomUUID()).email("test@example.com").build();

	@Test
	void recurringBudgetCreate_shouldReturnCreated() {
		UUID trackerId = UUID.randomUUID();
		CreateRecurringBudgetRequestDto request = new CreateRecurringBudgetRequestDto(
				"Rent",
				10_000L,
				"czk",
				PeriodType.MONTHLY,
				1,
				LocalDate.of(2026, 5, 1),
				null,
				null
		);
		RecurringBudgetResponseDto expected = responseDto();
		when(recurringBudgetService.recurringBudgetCreate(currentUser, trackerId, request)).thenReturn(expected);

		ResponseEntity<RecurringBudgetResponseDto> response = controller.recurringBudgetCreate(currentUser, trackerId, request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(response.getBody()).isEqualTo(expected);
		verify(expenseTrackerAccessService).assertHasRoleOnExpenseTracker(
				trackerId,
				currentUser,
				EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER
		);
	}

	@Test
	void syncRecurringBudgets_shouldReturnOk() {
		UUID trackerId = UUID.randomUUID();
		SyncRecurringBudgetResponseDto expected = new SyncRecurringBudgetResponseDto(2, 3);
		when(recurringBudgetService.syncRecurringBudgets(currentUser, trackerId)).thenReturn(expected);

		ResponseEntity<SyncRecurringBudgetResponseDto> response = controller.syncRecurringBudgets(currentUser, trackerId);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isEqualTo(expected);
	}

	@Test
	void recurringBudgetFindAll_shouldReturnOk() {
		UUID trackerId = UUID.randomUUID();
		Pageable pageable = PageRequest.of(0, 10);
		Page<RecurringBudgetResponseDto> expected = new PageImpl<>(List.of(responseDto()));
		when(recurringBudgetService.recurringBudgetFindAll(currentUser, trackerId, "rent", pageable)).thenReturn(expected);

		ResponseEntity<Page<RecurringBudgetResponseDto>> response = controller.recurringBudgetFindAll(currentUser, trackerId, "rent", pageable);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isEqualTo(expected);
	}

	@Test
	void recurringBudgetFindAllActive_shouldReturnOk() {
		UUID trackerId = UUID.randomUUID();
		Pageable pageable = PageRequest.of(0, 10);
		Page<RecurringBudgetResponseDto> expected = new PageImpl<>(List.of(responseDto()));
		when(recurringBudgetService.recurringBudgetFindAllActive(currentUser, trackerId, "rent", pageable)).thenReturn(expected);

		ResponseEntity<Page<RecurringBudgetResponseDto>> response = controller.recurringBudgetFindAllActive(currentUser, trackerId, "rent", pageable);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isEqualTo(expected);
	}

	@Test
	void recurringBudgetFindById_shouldReturnOk() {
		UUID trackerId = UUID.randomUUID();
		UUID templateId = UUID.randomUUID();
		RecurringBudgetResponseDto expected = responseDto();
		when(recurringBudgetService.recurringBudgetFindById(currentUser, trackerId, templateId)).thenReturn(expected);

		ResponseEntity<RecurringBudgetResponseDto> response = controller.recurringBudgetFindById(currentUser, trackerId, templateId);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isEqualTo(expected);
	}

	@Test
	void recurringBudgetUpdate_shouldRequireOwner() {
		UUID trackerId = UUID.randomUUID();
		UUID templateId = UUID.randomUUID();
		UpdateRecurringBudgetRequestDto request = new UpdateRecurringBudgetRequestDto(
				"Rent",
				20_000L,
				"USD",
				PeriodType.MONTHLY,
				1,
				LocalDate.of(2026, 5, 1),
				null,
				null
		);
		RecurringBudgetResponseDto expected = responseDto();
		when(recurringBudgetService.recurringBudgetUpdate(currentUser, trackerId, templateId, request)).thenReturn(expected);

		ResponseEntity<RecurringBudgetResponseDto> response = controller.recurringBudgetUpdate(currentUser, trackerId, templateId, request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isEqualTo(expected);
		verify(expenseTrackerAccessService).assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER);
	}

	@Test
	void recurringBudgetDeactivate_shouldRequireOwnerAndReturnNoContent() {
		UUID trackerId = UUID.randomUUID();
		UUID templateId = UUID.randomUUID();

		ResponseEntity<Void> response = controller.recurringBudgetDeactivate(currentUser, trackerId, templateId);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		verify(expenseTrackerAccessService).assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER);
		verify(recurringBudgetService).recurringBudgetDeactivate(currentUser, trackerId, templateId);
	}

	private RecurringBudgetResponseDto responseDto() {
		return new RecurringBudgetResponseDto(
				UUID.randomUUID(),
				"Rent",
				10_000L,
				"CZK",
				2,
				PeriodType.MONTHLY,
				1,
				UUID.randomUUID(),
				"Housing",
				LocalDate.of(2026, 5, 1),
				null,
				LocalDate.of(2026, 6, 1),
				true,
				null,
				null
		);
	}
}