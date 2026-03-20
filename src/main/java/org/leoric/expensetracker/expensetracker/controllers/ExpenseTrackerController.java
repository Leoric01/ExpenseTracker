package org.leoric.expensetracker.expensetracker.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.expensetracker.dto.CreateExpenseTrackerRequestDto;
import org.leoric.expensetracker.expensetracker.dto.ExpenseTrackerMineResponseDto;
import org.leoric.expensetracker.expensetracker.dto.ExpenseTrackerResponseDto;
import org.leoric.expensetracker.expensetracker.dto.UpdateExpenseTrackerRequestDto;
import org.leoric.expensetracker.expensetracker.services.interfaces.ExpenseTrackerAccessService;
import org.leoric.expensetracker.expensetracker.services.interfaces.ExpenseTrackerService;
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
@RequestMapping("/api/expense-trackers")
@RequiredArgsConstructor
public class ExpenseTrackerController {

	private final ExpenseTrackerService expenseTrackerService;
	private final ExpenseTrackerAccessService expenseTrackerAccessService;

	@PostMapping
	public ResponseEntity<ExpenseTrackerResponseDto> expenseTrackerCreate(
			@AuthenticationPrincipal User currentUser,
			@Valid @RequestBody CreateExpenseTrackerRequestDto request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(expenseTrackerService.expenseTrackerCreate(currentUser, request));
	}

	@GetMapping
	public ResponseEntity<Page<ExpenseTrackerResponseDto>> expenseTrackerFindAll(
			@AuthenticationPrincipal User currentUser,
			@RequestParam(required = false) String search,
			@ParameterObject Pageable pageable) {
		return ResponseEntity.ok(expenseTrackerService.expenseTrackerFindAll(currentUser, search, pageable));
	}

	@GetMapping("/mine")
	public ResponseEntity<Page<ExpenseTrackerMineResponseDto>> expenseTrackerFindAllMine(
			@AuthenticationPrincipal User currentUser,
			@RequestParam(required = false) String search,
			@ParameterObject Pageable pageable) {
		return ResponseEntity.ok(expenseTrackerService.expenseTrackerFindAllMine(currentUser, search, pageable));
	}

	@GetMapping("/{id}")
	public ResponseEntity<ExpenseTrackerResponseDto> expenseTrackerFindById(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID id) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(id, currentUser, EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER);
		return ResponseEntity.ok(expenseTrackerService.expenseTrackerFindById(currentUser, id));
	}

	@PatchMapping("/{id}")
	public ResponseEntity<ExpenseTrackerResponseDto> expenseTrackerUpdate(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID id,
			@Valid @RequestBody UpdateExpenseTrackerRequestDto request) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(id, currentUser, EXPENSETRACKER_OWNER);
		return ResponseEntity.ok(expenseTrackerService.expenseTrackerUpdate(currentUser, id, request));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> expenseTrackerDeactivate(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID id) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(id, currentUser, EXPENSETRACKER_OWNER);
		expenseTrackerService.expenseTrackerDeactivate(currentUser, id);
		return ResponseEntity.noContent().build();
	}
}