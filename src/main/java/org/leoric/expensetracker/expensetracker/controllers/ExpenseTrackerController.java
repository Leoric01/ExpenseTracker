package org.leoric.expensetracker.expensetracker.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.expensetracker.dto.CreateExpenseTrackerRequest;
import org.leoric.expensetracker.expensetracker.dto.ExpenseTrackerResponse;
import org.leoric.expensetracker.expensetracker.dto.UpdateExpenseTrackerRequest;
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

@RestController
@RequestMapping("/expense-trackers")
@RequiredArgsConstructor
public class ExpenseTrackerController {

	private final ExpenseTrackerService expenseTrackerService;

	@PostMapping
	public ResponseEntity<ExpenseTrackerResponse> expenseTrackerCreate(
			@AuthenticationPrincipal User currentUser,
			@Valid @RequestBody CreateExpenseTrackerRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(expenseTrackerService.expenseTrackerCreate(currentUser, request));
	}

	@GetMapping
	public ResponseEntity<Page<ExpenseTrackerResponse>> expenseTrackerFindAll(
			@AuthenticationPrincipal User currentUser,
			@RequestParam(required = false) String search,
			@ParameterObject Pageable pageable) {
		return ResponseEntity.ok(expenseTrackerService.expenseTrackerFindAll(currentUser, search, pageable));
	}

	@GetMapping("/{id}")
	public ResponseEntity<ExpenseTrackerResponse> expenseTrackerFindById(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID id) {
		return ResponseEntity.ok(expenseTrackerService.expenseTrackerFindById(currentUser, id));
	}

	@PatchMapping("/{id}")
	public ResponseEntity<ExpenseTrackerResponse> expenseTrackerUpdate(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID id,
			@Valid @RequestBody UpdateExpenseTrackerRequest request) {
		return ResponseEntity.ok(expenseTrackerService.expenseTrackerUpdate(currentUser, id, request));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> expenseTrackerDeactivate(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID id) {
		expenseTrackerService.expenseTrackerDeactivate(currentUser, id);
		return ResponseEntity.noContent().build();
	}
}