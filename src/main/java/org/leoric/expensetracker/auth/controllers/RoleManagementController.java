package org.leoric.expensetracker.auth.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.auth.dto.RoleManagementDtos.GlobalRequestResponse;
import org.leoric.expensetracker.auth.dto.RoleManagementDtos.RoleRequest;
import org.leoric.expensetracker.auth.dto.RoleManagementDtos.RoleSelection;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.auth.services.GlobalRoleManagementService;
import org.leoric.expensetracker.expensetracker.models.constants.ExpenseTrackerAccessRequestStatus;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleManagementController {
	private final GlobalRoleManagementService service;

	@GetMapping
	public List<String> globalRoles() {
		return List.of("USER", "ADMIN");
	}

	@GetMapping("/requests/mine")
	public Page<GlobalRequestResponse> mine(@AuthenticationPrincipal User user, @ParameterObject Pageable pageable) {
		return service.mine(user, pageable);
	}

	@GetMapping("/requests/mine/pending")
	public List<GlobalRequestResponse> minePending(@AuthenticationPrincipal User user) {
		return service.minePending(user);
	}

	@PostMapping("/requests")
	@ResponseStatus(HttpStatus.CREATED)
	public GlobalRequestResponse request(@AuthenticationPrincipal User user, @Valid @RequestBody RoleRequest request) {
		return service.request(user, request.role());
	}

	@PatchMapping("/requests/{id}/cancel")
	public GlobalRequestResponse cancel(@AuthenticationPrincipal User user, @PathVariable UUID id) {
		return service.cancel(user, id);
	}

	@Secured("ADMIN")
	@GetMapping("/requests")
	public Page<GlobalRequestResponse> all(@RequestParam(required = false) ExpenseTrackerAccessRequestStatus status, @ParameterObject Pageable pageable) {
		return service.all(status, pageable);
	}

	@Secured("ADMIN")
	@PatchMapping("/requests/{id}/approve")
	public GlobalRequestResponse approve(@AuthenticationPrincipal User user, @PathVariable UUID id) {
		return service.resolve(user, id, true);
	}

	@Secured("ADMIN")
	@PatchMapping("/requests/{id}/reject")
	public GlobalRequestResponse reject(@AuthenticationPrincipal User user, @PathVariable UUID id) {
		return service.resolve(user, id, false);
	}

	@Secured("ADMIN")
	@PutMapping("/users/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void setRoles(@AuthenticationPrincipal User user, @PathVariable UUID id, @Valid @RequestBody RoleSelection selection) {
		service.setRoles(user, id, selection.roles(), selection.expectedRoles());
	}
}
