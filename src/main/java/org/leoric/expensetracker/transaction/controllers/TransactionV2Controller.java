package org.leoric.expensetracker.transaction.controllers;

import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.expensetracker.services.interfaces.ExpenseTrackerAccessService;
import org.leoric.expensetracker.transaction.dto.AssetExchangeRateQuoteRequestDto;
import org.leoric.expensetracker.transaction.dto.AssetExchangeRateQuoteResponseDto;
import org.leoric.expensetracker.transaction.dto.CreateAssetExchangeV2RequestDto;
import org.leoric.expensetracker.transaction.dto.CreateAssetExchangeV2ResponseDto;
import org.leoric.expensetracker.transaction.dto.CreateWalletTransferV2RequestDto;
import org.leoric.expensetracker.transaction.dto.CreateWalletTransferV2ResponseDto;
import org.leoric.expensetracker.transaction.services.interfaces.TransactionV2Service;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_MEMBER;
import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_OWNER;

@RestController
@RequestMapping("/api/transaction-v2")
@RequiredArgsConstructor
public class TransactionV2Controller {

	private final TransactionV2Service transactionV2Service;
	private final ExpenseTrackerAccessService expenseTrackerAccessService;

	@PostMapping("/{trackerId}/wallet-transfer")
	public ResponseEntity<CreateWalletTransferV2ResponseDto> createWalletTransfer(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@RequestBody CreateWalletTransferV2RequestDto request) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(transactionV2Service.createWalletTransfer(currentUser, trackerId, request));
	}

	@PostMapping("/{trackerId}/asset-exchange")
	public ResponseEntity<CreateAssetExchangeV2ResponseDto> createAssetExchange(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@RequestBody CreateAssetExchangeV2RequestDto request) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(transactionV2Service.createAssetExchange(currentUser, trackerId, request));
	}

	@PostMapping("/{trackerId}/asset-exchange/rate")
	public ResponseEntity<AssetExchangeRateQuoteResponseDto> assetExchangeRateQuote(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@RequestBody AssetExchangeRateQuoteRequestDto request) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER);
		return ResponseEntity.ok(transactionV2Service.assetExchangeRateQuote(currentUser, trackerId, request));
	}
}