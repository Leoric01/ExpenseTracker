package org.leoric.expensetracker.wallet.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.expensetracker.services.interfaces.ExpenseTrackerAccessService;
import org.leoric.expensetracker.wallet.dto.CreateWalletRequestDto;
import org.leoric.expensetracker.wallet.dto.UpdateWalletRequestDto;
import org.leoric.expensetracker.wallet.dto.WalletDashboardResponseDto;
import org.leoric.expensetracker.wallet.dto.WalletResponseDto;
import org.leoric.expensetracker.wallet.dto.WalletSummaryResponseDto;
import org.leoric.expensetracker.wallet.services.interfaces.WalletService;
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
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

	private final WalletService walletService;
	private final ExpenseTrackerAccessService expenseTrackerAccessService;

	@PostMapping("/{trackerId}")
	public ResponseEntity<WalletResponseDto> walletCreate(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@Valid @RequestBody CreateWalletRequestDto request) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(walletService.walletCreate(currentUser, trackerId, request));
	}

	@GetMapping("/{trackerId}/dashboard")
	public ResponseEntity<WalletDashboardResponseDto> walletDashboard(
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

		return ResponseEntity.ok(walletService.walletDashboard(currentUser, trackerId, from, to));
	}

	@GetMapping("/{trackerId}")
	public ResponseEntity<Page<WalletResponseDto>> walletFindAll(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@RequestParam(required = false) String search,
			@ParameterObject Pageable pageable) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER);
		return ResponseEntity.ok(walletService.walletFindAll(currentUser, trackerId, search, pageable));
	}

	@GetMapping("/{trackerId}/{walletId}")
	public ResponseEntity<WalletResponseDto> walletFindById(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID walletId) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER);
		return ResponseEntity.ok(walletService.walletFindById(currentUser, trackerId, walletId));
	}

	@PatchMapping("/{trackerId}/{walletId}")
	public ResponseEntity<WalletResponseDto> walletUpdate(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID walletId,
			@Valid @RequestBody UpdateWalletRequestDto request) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER);
		return ResponseEntity.ok(walletService.walletUpdate(currentUser, trackerId, walletId, request));
	}

	@DeleteMapping("/{trackerId}/{walletId}")
	public ResponseEntity<Void> walletDeactivate(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID walletId) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER);
		walletService.walletDeactivate(currentUser, trackerId, walletId);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/{trackerId}/{walletId}/summary")
	public ResponseEntity<WalletSummaryResponseDto> walletSummary(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID walletId,
			@RequestParam(required = false) Instant from,
			@RequestParam(required = false) Instant to) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER);

		if (from == null) {
			from = YearMonth.now(ZoneOffset.UTC).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
		}
		if (to == null) {
			to = Instant.now();
		}

		return ResponseEntity.ok(walletService.walletSummary(currentUser, trackerId, walletId, from, to));
	}

	@PostMapping("/{trackerId}/{walletId}/icon")
	public ResponseEntity<WalletResponseDto> walletUploadIcon(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID walletId,
			@RequestParam("icon") MultipartFile icon,
			@RequestParam(value = "iconColor", required = false) String iconColor) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER);
		return ResponseEntity.ok(walletService.walletUploadIcon(currentUser, trackerId, walletId, icon, iconColor));
	}

	@DeleteMapping("/{trackerId}/{walletId}/icon")
	public ResponseEntity<WalletResponseDto> walletDeleteIcon(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID walletId) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER);
		return ResponseEntity.ok(walletService.walletDeleteIcon(currentUser, trackerId, walletId));
	}
}