package org.leoric.expensetracker.holding.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.expensetracker.services.interfaces.ExpenseTrackerAccessService;
import org.leoric.expensetracker.holding.dto.CreateHoldingRequestDto;
import org.leoric.expensetracker.holding.dto.HoldingLiteResponseDto;
import org.leoric.expensetracker.holding.dto.HoldingResponseDto;
import org.leoric.expensetracker.holding.dto.HoldingSummaryResponseDto;
import org.leoric.expensetracker.holding.services.interfaces.HoldingService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_MEMBER;
import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_OWNER;

@RestController
@RequestMapping("/api/holding")
@RequiredArgsConstructor
public class HoldingController {

	private final HoldingService holdingService;
	private final ExpenseTrackerAccessService expenseTrackerAccessService;

	@PostMapping("/{trackerId}")
	public ResponseEntity<HoldingResponseDto> holdingCreate(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@Valid @RequestBody CreateHoldingRequestDto request) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(holdingService.holdingCreate(currentUser, trackerId, request));
	}


	@GetMapping("/{trackerId}")
	public ResponseEntity<Page<HoldingResponseDto>> holdingFindAll(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@RequestParam(required = false) String search,
			@ParameterObject Pageable pageable) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER);
		return ResponseEntity.ok(holdingService.holdingFindAll(currentUser, trackerId, search, pageable));
	}

	@GetMapping("/{trackerId}/lite")
	public ResponseEntity<List<HoldingLiteResponseDto>> holdingFindAllLite(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER);
		return ResponseEntity.ok(holdingService.holdingFindAllLite(currentUser, trackerId));
	}

	@GetMapping("/{trackerId}/{holdingId}")
	public ResponseEntity<HoldingResponseDto> holdingFindById(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID holdingId) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER);
		return ResponseEntity.ok(holdingService.holdingFindById(currentUser, trackerId, holdingId));
	}

	@DeleteMapping("/{trackerId}/{holdingId}")
	public ResponseEntity<Void> holdingDeactivate(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID holdingId) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER);
		holdingService.holdingDeactivate(currentUser, trackerId, holdingId);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/{trackerId}/{holdingId}/summary")
	public ResponseEntity<HoldingSummaryResponseDto> holdingSummary(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID holdingId,
			@RequestParam(required = false) Instant from,
			@RequestParam(required = false) Instant to) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER);

		if (from == null) {
			from = YearMonth.now(ZoneOffset.UTC).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
		}
		if (to == null) {
			to = Instant.now();
		}

		return ResponseEntity.ok(holdingService.holdingSummary(currentUser, trackerId, holdingId, from, to));
	}
}