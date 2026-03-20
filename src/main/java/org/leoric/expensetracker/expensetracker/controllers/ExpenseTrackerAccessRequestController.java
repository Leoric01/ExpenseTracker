package org.leoric.expensetracker.expensetracker.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.expensetracker.dto.ExpenseTrackerAccessRequestResponseDto;
import org.leoric.expensetracker.expensetracker.dto.InviteUserRequestDto;
import org.leoric.expensetracker.expensetracker.models.constants.ExpenseTrackerAccessRequestType;
import org.leoric.expensetracker.expensetracker.services.interfaces.ExpenseTrackerAccessService;
import org.leoric.expensetracker.expensetracker.services.interfaces.ExpenseTrackerAccessRequestService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_OWNER;

@RestController
@RequestMapping("/api/expense-trackers")
@RequiredArgsConstructor
public class ExpenseTrackerAccessRequestController {

	private final ExpenseTrackerAccessRequestService expenseTrackerAccessRequestService;
	private final ExpenseTrackerAccessService expenseTrackerAccessService;

	@PostMapping("/{trackerId}/access-requests")
	public ResponseEntity<ExpenseTrackerAccessRequestResponseDto> expenseTrackerAccessRequestCreate(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(expenseTrackerAccessRequestService.expenseTrackerAccessRequestCreate(currentUser, trackerId));
	}

	@PostMapping("/{trackerId}/access-requests/invite")
	public ResponseEntity<ExpenseTrackerAccessRequestResponseDto> expenseTrackerAccessRequestInvite(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@Valid @RequestBody InviteUserRequestDto request) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(expenseTrackerAccessRequestService.expenseTrackerAccessRequestInvite(currentUser, trackerId, request));
	}

	@GetMapping("/{trackerId}/access-requests")
	public ResponseEntity<Page<ExpenseTrackerAccessRequestResponseDto>> expenseTrackerAccessRequestFindAllByTracker(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@RequestParam(required = false) String search,
			@ParameterObject Pageable pageable) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER);
		return ResponseEntity.ok(expenseTrackerAccessRequestService.expenseTrackerAccessRequestFindAllByTracker(currentUser, trackerId, search, pageable));
	}

	@GetMapping("/access-requests/mine")
	public ResponseEntity<Page<ExpenseTrackerAccessRequestResponseDto>> expenseTrackerAccessRequestFindAllMine(
			@AuthenticationPrincipal User currentUser,
			@RequestParam(required = false) String search,
			@ParameterObject Pageable pageable) {
		return ResponseEntity.ok(expenseTrackerAccessRequestService.expenseTrackerAccessRequestFindAllMine(currentUser, search, pageable));
	}

	@PatchMapping("/access-requests/{requestId}/approve")
	public ResponseEntity<ExpenseTrackerAccessRequestResponseDto> expenseTrackerAccessRequestApprove(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID requestId) {
		var authInfo = expenseTrackerAccessRequestService.getAuthorizationInfo(requestId);
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(authInfo.expenseTrackerId(), currentUser, EXPENSETRACKER_OWNER);
		return ResponseEntity.ok(expenseTrackerAccessRequestService.expenseTrackerAccessRequestApprove(currentUser, requestId));
	}

	@PatchMapping("/access-requests/{requestId}/reject")
	public ResponseEntity<ExpenseTrackerAccessRequestResponseDto> expenseTrackerAccessRequestReject(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID requestId) {
		var authInfo = expenseTrackerAccessRequestService.getAuthorizationInfo(requestId);
		if (authInfo.expenseTrackerAccessRequestType() == ExpenseTrackerAccessRequestType.REQUEST) {
			expenseTrackerAccessService.assertHasRoleOnExpenseTracker(authInfo.expenseTrackerId(), currentUser, EXPENSETRACKER_OWNER);
		}
		return ResponseEntity.ok(expenseTrackerAccessRequestService.expenseTrackerAccessRequestReject(currentUser, requestId));
	}

	@PatchMapping("/access-requests/{requestId}/cancel")
	public ResponseEntity<Void> expenseTrackerAccessRequestCancel(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID requestId) {
		expenseTrackerAccessRequestService.expenseTrackerAccessRequestCancel(currentUser, requestId);
		return ResponseEntity.noContent().build();
	}

	@PatchMapping("/access-requests/{requestId}/accept")
	public ResponseEntity<ExpenseTrackerAccessRequestResponseDto> expenseTrackerAccessRequestAccept(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID requestId) {
		return ResponseEntity.ok(expenseTrackerAccessRequestService.expenseTrackerAccessRequestAccept(currentUser, requestId));
	}
}