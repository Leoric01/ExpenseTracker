package org.leoric.expensetracker.budget.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.budget.dto.BudgetPlanResponseDto;
import org.leoric.expensetracker.budget.dto.BulkBudgetImportItemDto;
import org.leoric.expensetracker.budget.dto.BulkBudgetImportResponseDto;
import org.leoric.expensetracker.budget.dto.CreateBudgetPlanRequestDto;
import org.leoric.expensetracker.budget.dto.UpdateBudgetPlanRequestDto;
import org.leoric.expensetracker.budget.services.interfaces.BudgetPlanService;
import org.leoric.expensetracker.expensetracker.services.interfaces.ExpenseTrackerAccessService;
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

import java.util.List;
import java.util.UUID;

import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_MEMBER;
import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_OWNER;

@RestController
@RequestMapping("/api/budget-plan")
@RequiredArgsConstructor
public class BudgetPlanController {

	private final BudgetPlanService budgetPlanService;
	private final ExpenseTrackerAccessService expenseTrackerAccessService;

	@PostMapping("/{trackerId}")
	public ResponseEntity<BudgetPlanResponseDto> budgetPlanCreate(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@Valid @RequestBody CreateBudgetPlanRequestDto request) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(budgetPlanService.budgetPlanCreate(currentUser, trackerId, request));
	}

	@GetMapping("/{trackerId}")
	public ResponseEntity<Page<BudgetPlanResponseDto>> budgetPlanFindAll(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@RequestParam(required = false) String search,
			@ParameterObject Pageable pageable) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER);
		return ResponseEntity.ok(budgetPlanService.budgetPlanFindAll(currentUser, trackerId, search, pageable));
	}

	@GetMapping("/{trackerId}/active")
	public ResponseEntity<Page<BudgetPlanResponseDto>> budgetPlanFindAllActive(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@RequestParam(required = false) String search,
			@ParameterObject Pageable pageable) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER);
		return ResponseEntity.ok(budgetPlanService.budgetPlanFindAllActive(currentUser, trackerId, search, pageable));
	}

	@GetMapping("/{trackerId}/{budgetPlanId}")
	public ResponseEntity<BudgetPlanResponseDto> budgetPlanFindById(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID budgetPlanId) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER);
		return ResponseEntity.ok(budgetPlanService.budgetPlanFindById(currentUser, trackerId, budgetPlanId));
	}

	@PatchMapping("/{trackerId}/{budgetPlanId}")
	public ResponseEntity<BudgetPlanResponseDto> budgetPlanUpdate(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID budgetPlanId,
			@Valid @RequestBody UpdateBudgetPlanRequestDto request) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER);
		return ResponseEntity.ok(budgetPlanService.budgetPlanUpdate(currentUser, trackerId, budgetPlanId, request));
	}

	@DeleteMapping("/{trackerId}/{budgetPlanId}")
	public ResponseEntity<Void> budgetPlanDeactivate(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID budgetPlanId) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER);
		budgetPlanService.budgetPlanDeactivate(currentUser, trackerId, budgetPlanId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{trackerId}/import")
	public ResponseEntity<BulkBudgetImportResponseDto> bulkImport(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@Valid @RequestBody List<BulkBudgetImportItemDto> items) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(budgetPlanService.bulkImport(currentUser, trackerId, items));
	}
}