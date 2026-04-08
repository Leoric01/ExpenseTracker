package org.leoric.expensetracker.institution.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.expensetracker.services.interfaces.ExpenseTrackerAccessService;
import org.leoric.expensetracker.institution.dto.CreateInstitutionRequestDto;
import org.leoric.expensetracker.institution.dto.InstitutionDashboardResponseDto;
import org.leoric.expensetracker.institution.dto.InstitutionResponseDto;
import org.leoric.expensetracker.institution.dto.UpdateInstitutionRequestDto;
import org.leoric.expensetracker.institution.services.interfaces.InstitutionService;
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
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_MEMBER;
import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_OWNER;

@RestController
@RequestMapping("/api/institution")
@RequiredArgsConstructor
public class InstitutionController {

	private final InstitutionService institutionService;
	private final ExpenseTrackerAccessService expenseTrackerAccessService;

	@PostMapping("/{trackerId}")
	public ResponseEntity<InstitutionResponseDto> institutionCreate(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@Valid @RequestBody CreateInstitutionRequestDto request) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(institutionService.institutionCreate(currentUser, trackerId, request));
	}

	@GetMapping("/{trackerId}/dashboard")
	public ResponseEntity<InstitutionDashboardResponseDto> institutionDashboard(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@RequestParam(required = false) Instant from,
			@RequestParam(required = false) Instant to) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER);

		if (from == null) {
			from = YearMonth.now(ZoneOffset.UTC).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
		}
		if (to == null) {
			to = Instant.now();
		}

		return ResponseEntity.ok(institutionService.institutionDashboard(currentUser, trackerId, from, to));
	}

	@GetMapping("/{trackerId}")
	public ResponseEntity<Page<InstitutionResponseDto>> institutionFindAll(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@RequestParam(required = false) String search,
			@ParameterObject Pageable pageable) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER);
		return ResponseEntity.ok(institutionService.institutionFindAll(currentUser, trackerId, search, pageable));
	}

	@GetMapping("/{trackerId}/{institutionId}")
	public ResponseEntity<InstitutionResponseDto> institutionFindById(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID institutionId) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER);
		return ResponseEntity.ok(institutionService.institutionFindById(currentUser, trackerId, institutionId));
	}

	@PatchMapping("/{trackerId}/{institutionId}")
	public ResponseEntity<InstitutionResponseDto> institutionUpdate(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID institutionId,
			@Valid @RequestBody UpdateInstitutionRequestDto request) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER);
		return ResponseEntity.ok(institutionService.institutionUpdate(currentUser, trackerId, institutionId, request));
	}

	@DeleteMapping("/{trackerId}/{institutionId}")
	public ResponseEntity<Void> institutionDeactivate(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID institutionId) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER);
		institutionService.institutionDeactivate(currentUser, trackerId, institutionId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{trackerId}/{institutionId}/icon")
	public ResponseEntity<InstitutionResponseDto> institutionUploadIcon(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID institutionId,
			@RequestParam("icon") MultipartFile icon,
			@RequestParam(value = "iconColor", required = false) String iconColor) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER);
		return ResponseEntity.ok(institutionService.institutionUploadIcon(currentUser, trackerId, institutionId, icon, iconColor));
	}

	@DeleteMapping("/{trackerId}/{institutionId}/icon")
	public ResponseEntity<InstitutionResponseDto> institutionDeleteIcon(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID institutionId) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER);
		return ResponseEntity.ok(institutionService.institutionDeleteIcon(currentUser, trackerId, institutionId));
	}
}