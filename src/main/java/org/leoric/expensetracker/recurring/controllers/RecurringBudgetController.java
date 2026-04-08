package org.leoric.expensetracker.recurring.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.expensetracker.services.interfaces.ExpenseTrackerAccessService;
import org.leoric.expensetracker.recurring.dto.CreateRecurringBudgetRequestDto;
import org.leoric.expensetracker.recurring.dto.RecurringBudgetResponseDto;
import org.leoric.expensetracker.recurring.dto.SyncRecurringBudgetResponseDto;
import org.leoric.expensetracker.recurring.dto.UpdateRecurringBudgetRequestDto;
import org.leoric.expensetracker.recurring.services.interfaces.RecurringBudgetService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_MEMBER;
import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_OWNER;

@RestController
@RequestMapping("/api/recurring-budget")
@RequiredArgsConstructor
public class RecurringBudgetController {

	private final RecurringBudgetService recurringBudgetService;
	private final ExpenseTrackerAccessService expenseTrackerAccessService;

	@PostMapping("/{trackerId}")
	public ResponseEntity<RecurringBudgetResponseDto> recurringBudgetCreate(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@Valid @RequestBody CreateRecurringBudgetRequestDto request) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(recurringBudgetService.recurringBudgetCreate(currentUser, trackerId, request));
	}

	@PostMapping("/{trackerId}/sync")
	public ResponseEntity<SyncRecurringBudgetResponseDto> syncRecurringBudgets(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER);
		return ResponseEntity.ok(recurringBudgetService.syncRecurringBudgets(currentUser, trackerId));
	}

	@GetMapping("/{trackerId}")
	public ResponseEntity<Page<RecurringBudgetResponseDto>> recurringBudgetFindAll(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@RequestParam(required = false) String search,
			@ParameterObject Pageable pageable) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER);
		return ResponseEntity.ok(recurringBudgetService.recurringBudgetFindAll(currentUser, trackerId, search, pageable));
	}

	@GetMapping("/{trackerId}/active")
	public ResponseEntity<Page<RecurringBudgetResponseDto>> recurringBudgetFindAllActive(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@RequestParam(required = false) String search,
			@ParameterObject Pageable pageable) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER);
		return ResponseEntity.ok(recurringBudgetService.recurringBudgetFindAllActive(currentUser, trackerId, search, pageable));
	}

	@GetMapping("/{trackerId}/{templateId}")
	public ResponseEntity<RecurringBudgetResponseDto> recurringBudgetFindById(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID templateId) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER);
		return ResponseEntity.ok(recurringBudgetService.recurringBudgetFindById(currentUser, trackerId, templateId));
	}

	@PatchMapping("/{trackerId}/{templateId}")
	public ResponseEntity<RecurringBudgetResponseDto> recurringBudgetUpdate(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID templateId,
			@Valid @RequestBody UpdateRecurringBudgetRequestDto request) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER);
		return ResponseEntity.ok(recurringBudgetService.recurringBudgetUpdate(currentUser, trackerId, templateId, request));
	}

	@DeleteMapping("/{trackerId}/{templateId}")
	public ResponseEntity<Void> recurringBudgetDeactivate(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID templateId) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER);
		recurringBudgetService.recurringBudgetDeactivate(currentUser, trackerId, templateId);
		return ResponseEntity.noContent().build();
	}
}