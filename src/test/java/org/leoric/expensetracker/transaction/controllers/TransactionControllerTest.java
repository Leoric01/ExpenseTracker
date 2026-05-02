package org.leoric.expensetracker.transaction.controllers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.expensetracker.services.interfaces.ExpenseTrackerAccessService;
import org.leoric.expensetracker.transaction.dto.CreateTransactionRequestDto;
import org.leoric.expensetracker.transaction.dto.TransactionAmountRateMode;
import org.leoric.expensetracker.transaction.dto.TransactionAttachmentResponseDto;
import org.leoric.expensetracker.transaction.dto.TransactionFilter;
import org.leoric.expensetracker.transaction.dto.TransactionPageResponseDto;
import org.leoric.expensetracker.transaction.dto.TransactionResponseDto;
import org.leoric.expensetracker.transaction.dto.UpdateTransactionRequestDto;
import org.leoric.expensetracker.transaction.models.constants.TransactionStatus;
import org.leoric.expensetracker.transaction.models.constants.TransactionType;
import org.leoric.expensetracker.transaction.services.interfaces.TransactionService;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_MEMBER;
import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_OWNER;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionControllerTest {

	@Mock
	private TransactionService transactionService;
	@Mock
	private ExpenseTrackerAccessService expenseTrackerAccessService;

	@InjectMocks
	private TransactionController controller;

	private final User currentUser = User.builder().id(UUID.randomUUID()).email("test@example.com").build();

	@Test
	void transactionCreate_shouldReturnCreatedAndCallService() {
		UUID trackerId = UUID.randomUUID();
		CreateTransactionRequestDto request = new CreateTransactionRequestDto(
				TransactionType.EXPENSE,
				null,
				null,
				null,
				null,
				120L,
				null,
				"CZK",
				null,
				0L,
				Instant.now(),
				"Lunch",
				null,
				null
		);
		TransactionResponseDto expected = minimalTransactionResponse();
		when(transactionService.transactionCreate(currentUser, trackerId, request)).thenReturn(expected);

		ResponseEntity<TransactionResponseDto> response = controller.transactionCreate(currentUser, trackerId, request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(response.getBody()).isEqualTo(expected);
		verify(expenseTrackerAccessService).assertHasRoleOnExpenseTracker(
				trackerId,
				currentUser,
				EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER
		);
		verify(transactionService).transactionCreate(currentUser, trackerId, request);
	}

	@Test
	void transactionFindAllPageable_shouldBuildFilterAndReturnOk() {
		UUID trackerId = UUID.randomUUID();
		UUID categoryId = UUID.randomUUID();
		UUID holdingId = UUID.randomUUID();
		Instant dateFrom = Instant.parse("2026-01-01T00:00:00Z");
		Instant dateTo = Instant.parse("2026-01-31T00:00:00Z");
		Pageable pageable = PageRequest.of(0, 20);
		TransactionPageResponseDto expected = new TransactionPageResponseDto(List.of(), null, null);
		when(transactionService.transactionFindAllPageable(currentUser, trackerId,
				new TransactionFilter("rent", categoryId, holdingId, TransactionType.EXPENSE, TransactionStatus.COMPLETED, dateFrom, dateTo, TransactionAmountRateMode.TRANSACTION_DATE),
				pageable)).thenReturn(expected);

		ResponseEntity<TransactionPageResponseDto> response = controller.transactionFindAllPageable(
				currentUser,
				trackerId,
				"rent",
				categoryId,
				holdingId,
				TransactionType.EXPENSE,
				TransactionStatus.COMPLETED,
				dateFrom,
				dateTo,
				TransactionAmountRateMode.TRANSACTION_DATE,
				pageable
		);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isEqualTo(expected);
		verify(expenseTrackerAccessService).assertHasRoleOnExpenseTracker(
				trackerId,
				currentUser,
				EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER
		);
	}

	@Test
	void transactionFindById_shouldReturnOk() {
		UUID trackerId = UUID.randomUUID();
		UUID transactionId = UUID.randomUUID();
		TransactionResponseDto expected = minimalTransactionResponse();
		when(transactionService.transactionFindById(currentUser, trackerId, transactionId)).thenReturn(expected);

		ResponseEntity<TransactionResponseDto> response = controller.transactionFindById(currentUser, trackerId, transactionId);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isEqualTo(expected);
		verify(expenseTrackerAccessService).assertHasRoleOnExpenseTracker(
				trackerId,
				currentUser,
				EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER
		);
	}

	@Test
	void transactionUpdate_shouldReturnOk() {
		UUID trackerId = UUID.randomUUID();
		UUID transactionId = UUID.randomUUID();
		UpdateTransactionRequestDto request = new UpdateTransactionRequestDto(null, null, null, 200L, "CZK", null, 0L, null, null, null, null, null, null);
		TransactionResponseDto expected = minimalTransactionResponse();
		when(transactionService.transactionUpdate(currentUser, trackerId, transactionId, request)).thenReturn(expected);

		ResponseEntity<TransactionResponseDto> response = controller.transactionUpdate(currentUser, trackerId, transactionId, request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isEqualTo(expected);
		verify(expenseTrackerAccessService).assertHasRoleOnExpenseTracker(
				trackerId,
				currentUser,
				EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER
		);
	}

	@Test
	void transactionCancel_shouldRequireOwnerAndReturnOk() {
		UUID trackerId = UUID.randomUUID();
		UUID transactionId = UUID.randomUUID();
		TransactionResponseDto expected = minimalTransactionResponse();
		when(transactionService.transactionCancel(currentUser, trackerId, transactionId)).thenReturn(expected);

		ResponseEntity<TransactionResponseDto> response = controller.transactionCancel(currentUser, trackerId, transactionId);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isEqualTo(expected);
		verify(expenseTrackerAccessService).assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER);
	}

	@Test
	void transactionUploadAttachment_shouldReturnCreated() {
		UUID trackerId = UUID.randomUUID();
		UUID transactionId = UUID.randomUUID();
		MockMultipartFile file = new MockMultipartFile("file", "receipt.txt", "text/plain", "hello".getBytes());
		TransactionAttachmentResponseDto expected = new TransactionAttachmentResponseDto(UUID.randomUUID(), "receipt.txt", "https://cdn/x", "text/plain", 5L, null);
		when(transactionService.transactionUploadAttachment(currentUser, trackerId, transactionId, file)).thenReturn(expected);

		ResponseEntity<TransactionAttachmentResponseDto> response = controller.transactionUploadAttachment(currentUser, trackerId, transactionId, file);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(response.getBody()).isEqualTo(expected);
		verify(expenseTrackerAccessService).assertHasRoleOnExpenseTracker(
				trackerId,
				currentUser,
				EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER
		);
	}

	@Test
	void transactionFindAttachments_shouldReturnOk() {
		UUID trackerId = UUID.randomUUID();
		UUID transactionId = UUID.randomUUID();
		List<TransactionAttachmentResponseDto> expected = List.of(
				new TransactionAttachmentResponseDto(UUID.randomUUID(), "a.pdf", "https://cdn/a", "application/pdf", 10L, null)
		);
		when(transactionService.transactionFindAttachments(currentUser, trackerId, transactionId)).thenReturn(expected);

		ResponseEntity<List<TransactionAttachmentResponseDto>> response = controller.transactionFindAttachments(currentUser, trackerId, transactionId);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isEqualTo(expected);
		verify(expenseTrackerAccessService).assertHasRoleOnExpenseTracker(
				trackerId,
				currentUser,
				EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER
		);
	}

	@Test
	void transactionDeleteAttachment_shouldReturnNoContent() {
		UUID trackerId = UUID.randomUUID();
		UUID transactionId = UUID.randomUUID();
		UUID attachmentId = UUID.randomUUID();

		ResponseEntity<Void> response = controller.transactionDeleteAttachment(currentUser, trackerId, transactionId, attachmentId);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		verify(expenseTrackerAccessService).assertHasRoleOnExpenseTracker(
				trackerId,
				currentUser,
				EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER
		);
		verify(transactionService).transactionDeleteAttachment(currentUser, trackerId, transactionId, attachmentId);
	}

	private TransactionResponseDto minimalTransactionResponse() {
		return new TransactionResponseDto(
				UUID.randomUUID(),
				TransactionType.EXPENSE,
				TransactionStatus.COMPLETED,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				100L,
				"CZK",
				2,
				null,
				0L,
				null,
				null,
				Instant.now(),
				null,
				null,
				null,
				List.of(),
				null,
				null
		);
	}
}