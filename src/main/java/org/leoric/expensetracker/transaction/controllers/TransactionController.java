package org.leoric.expensetracker.transaction.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.expensetracker.services.interfaces.ExpenseTrackerAccessService;
import org.leoric.expensetracker.transaction.dto.CreateTransactionRequestDto;
import org.leoric.expensetracker.transaction.dto.TransactionAttachmentResponseDto;
import org.leoric.expensetracker.transaction.dto.TransactionFilter;
import org.leoric.expensetracker.transaction.dto.TransactionPageResponseDto;
import org.leoric.expensetracker.transaction.dto.TransactionResponseDto;
import org.leoric.expensetracker.transaction.dto.UpdateTransactionRequestDto;
import org.leoric.expensetracker.transaction.models.constants.TransactionStatus;
import org.leoric.expensetracker.transaction.models.constants.TransactionType;
import org.leoric.expensetracker.transaction.services.interfaces.TransactionService;
import org.springdoc.core.annotations.ParameterObject;
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
import java.util.List;
import java.util.UUID;

import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_MEMBER;
import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_OWNER;

@RestController
@RequestMapping("/api/transaction")
@RequiredArgsConstructor
public class TransactionController {

	private final TransactionService transactionService;
	private final ExpenseTrackerAccessService expenseTrackerAccessService;

	@PostMapping("/{trackerId}")
	public ResponseEntity<TransactionResponseDto> transactionCreate(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@Valid @RequestBody CreateTransactionRequestDto request) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(transactionService.transactionCreate(currentUser, trackerId, request));
	}

	@GetMapping("/{trackerId}")
	public ResponseEntity<TransactionPageResponseDto> transactionFindAllPageable(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@RequestParam(required = false) String search,
			@RequestParam(required = false) UUID categoryId,
			@RequestParam(required = false) UUID holdingId,
			@RequestParam(required = false) TransactionType transactionType,
			@RequestParam(required = false) TransactionStatus status,
			@RequestParam(required = false) Instant dateFrom,
			@RequestParam(required = false) Instant dateTo,
			@ParameterObject Pageable pageable) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER);

		TransactionFilter filter = new TransactionFilter(
				search,
				categoryId,
				holdingId,
				transactionType,
				status,
				dateFrom,
				dateTo
		);

		return ResponseEntity.ok(transactionService.transactionFindAllPageable(currentUser, trackerId, filter, pageable));
	}

	@GetMapping("/{trackerId}/{transactionId}")
	public ResponseEntity<TransactionResponseDto> transactionFindById(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID transactionId) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER);
		return ResponseEntity.ok(transactionService.transactionFindById(currentUser, trackerId, transactionId));
	}

	@PatchMapping("/{trackerId}/{transactionId}")
	public ResponseEntity<TransactionResponseDto> transactionUpdate(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID transactionId,
			@Valid @RequestBody UpdateTransactionRequestDto request) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER);
		return ResponseEntity.ok(transactionService.transactionUpdate(currentUser, trackerId, transactionId, request));
	}

	@DeleteMapping("/{trackerId}/{transactionId}")
	public ResponseEntity<TransactionResponseDto> transactionCancel(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID transactionId) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER);
		return ResponseEntity.ok(transactionService.transactionCancel(currentUser, trackerId, transactionId));
	}

	@PostMapping("/{trackerId}/{transactionId}/attachments")
	public ResponseEntity<TransactionAttachmentResponseDto> transactionUploadAttachment(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID transactionId,
			@RequestParam("file") MultipartFile file) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(transactionService.transactionUploadAttachment(currentUser, trackerId, transactionId, file));
	}

	@GetMapping("/{trackerId}/{transactionId}/attachments")
	public ResponseEntity<List<TransactionAttachmentResponseDto>> transactionFindAttachments(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID transactionId) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER);
		return ResponseEntity.ok(transactionService.transactionFindAttachments(currentUser, trackerId, transactionId));
	}

	@DeleteMapping("/{trackerId}/{transactionId}/attachments/{attachmentId}")
	public ResponseEntity<Void> transactionDeleteAttachment(
			@AuthenticationPrincipal User currentUser,
			@PathVariable UUID trackerId,
			@PathVariable UUID transactionId,
			@PathVariable UUID attachmentId) {
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(trackerId, currentUser, EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER);
		transactionService.transactionDeleteAttachment(currentUser, trackerId, transactionId, attachmentId);
		return ResponseEntity.noContent().build();
	}
}