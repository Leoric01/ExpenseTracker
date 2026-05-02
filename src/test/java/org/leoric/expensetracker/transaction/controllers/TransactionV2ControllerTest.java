package org.leoric.expensetracker.transaction.controllers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.expensetracker.services.interfaces.ExpenseTrackerAccessService;
import org.leoric.expensetracker.transaction.dto.AssetExchangeRateQuoteRequestDto;
import org.leoric.expensetracker.transaction.dto.AssetExchangeRateQuoteResponseDto;
import org.leoric.expensetracker.transaction.dto.CreateAssetExchangeV2RequestDto;
import org.leoric.expensetracker.transaction.dto.CreateAssetExchangeV2ResponseDto;
import org.leoric.expensetracker.transaction.dto.CreateWalletTransferV2RequestDto;
import org.leoric.expensetracker.transaction.dto.CreateWalletTransferV2ResponseDto;
import org.leoric.expensetracker.transaction.dto.TransactionV2OperationType;
import org.leoric.expensetracker.transaction.dto.TransferAmountCalculationMode;
import org.leoric.expensetracker.transaction.services.interfaces.TransactionV2Service;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_MEMBER;
import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_OWNER;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionV2ControllerTest {

	@Mock
	private TransactionV2Service transactionV2Service;
	@Mock
	private ExpenseTrackerAccessService expenseTrackerAccessService;

	@InjectMocks
	private TransactionV2Controller controller;

	private final User currentUser = User.builder().id(UUID.randomUUID()).email("test@example.com").build();

	@Test
	void createWalletTransfer_shouldReturnCreated() {
		UUID trackerId = UUID.randomUUID();
		UUID sourceHoldingId = UUID.randomUUID();
		UUID targetHoldingId = UUID.randomUUID();
		CreateWalletTransferV2RequestDto request = new CreateWalletTransferV2RequestDto(
				sourceHoldingId,
				targetHoldingId,
				100L,
				98L,
				Instant.parse("2026-05-02T10:00:00Z"),
				"desc",
				null,
				null
		);
		CreateWalletTransferV2ResponseDto expected = new CreateWalletTransferV2ResponseDto(
				UUID.randomUUID(),
				TransactionV2OperationType.WALLET_TRANSFER,
				TransferAmountCalculationMode.AMOUNT_AND_SETTLED,
				100L,
				98L,
				2L,
				100L,
				98L,
				false,
				sourceHoldingId,
				targetHoldingId,
				"CZK",
				2,
				"CZK",
				2,
				Instant.parse("2026-05-02T10:00:00Z")
		);
		when(transactionV2Service.createWalletTransfer(currentUser, trackerId, request)).thenReturn(expected);

		ResponseEntity<CreateWalletTransferV2ResponseDto> response = controller.createWalletTransfer(currentUser, trackerId, request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(response.getBody()).isEqualTo(expected);
		verify(expenseTrackerAccessService).assertHasRoleOnExpenseTracker(
				trackerId,
				currentUser,
				EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER
		);
	}

	@Test
	void createAssetExchange_shouldReturnCreated() {
		UUID trackerId = UUID.randomUUID();
		UUID sourceHoldingId = UUID.randomUUID();
		UUID targetHoldingId = UUID.randomUUID();
		CreateAssetExchangeV2RequestDto request = new CreateAssetExchangeV2RequestDto(
				sourceHoldingId,
				targetHoldingId,
				1_000_000L,
				1000L,
				160000L,
				new BigDecimal("0.16000000"),
				Instant.parse("2026-05-02T10:00:00Z"),
				"desc",
				null,
				null
		);
		CreateAssetExchangeV2ResponseDto expected = new CreateAssetExchangeV2ResponseDto(
				UUID.randomUUID(),
				TransactionV2OperationType.ASSET_EXCHANGE,
				TransferAmountCalculationMode.ALL_FIELDS_RECONCILED,
				1_000_000L,
				160000L,
				1000L,
				1_001_000L,
				160000L,
				false,
				sourceHoldingId,
				"Spot",
				targetHoldingId,
				"Cash",
				"BTC",
				8,
				"CZK",
				2,
				"BTC",
				new BigDecimal("0.16000000"),
				Instant.parse("2026-05-02T10:00:00Z")
		);
		when(transactionV2Service.createAssetExchange(currentUser, trackerId, request)).thenReturn(expected);

		ResponseEntity<CreateAssetExchangeV2ResponseDto> response = controller.createAssetExchange(currentUser, trackerId, request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(response.getBody()).isEqualTo(expected);
		verify(expenseTrackerAccessService).assertHasRoleOnExpenseTracker(
				trackerId,
				currentUser,
				EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER
		);
	}

	@Test
	void assetExchangeRateQuote_shouldReturnOk() {
		UUID trackerId = UUID.randomUUID();
		UUID sourceHoldingId = UUID.randomUUID();
		UUID targetHoldingId = UUID.randomUUID();
		AssetExchangeRateQuoteRequestDto request = new AssetExchangeRateQuoteRequestDto(sourceHoldingId, targetHoldingId);
		AssetExchangeRateQuoteResponseDto expected = new AssetExchangeRateQuoteResponseDto(
				sourceHoldingId,
				"BTC",
				targetHoldingId,
				"CZK",
				LocalDate.of(2026, 5, 2),
				new BigDecimal("2500000.12")
		);
		when(transactionV2Service.assetExchangeRateQuote(currentUser, trackerId, request)).thenReturn(expected);

		ResponseEntity<AssetExchangeRateQuoteResponseDto> response = controller.assetExchangeRateQuote(currentUser, trackerId, request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isEqualTo(expected);
		verify(expenseTrackerAccessService).assertHasRoleOnExpenseTracker(
				trackerId,
				currentUser,
				EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER
		);
	}
}