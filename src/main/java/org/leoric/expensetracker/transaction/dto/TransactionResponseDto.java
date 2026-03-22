package org.leoric.expensetracker.transaction.dto;

import org.leoric.expensetracker.transaction.models.constants.BalanceAdjustmentDirection;
import org.leoric.expensetracker.transaction.models.constants.TransactionStatus;
import org.leoric.expensetracker.transaction.models.constants.TransactionType;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record TransactionResponseDto(
		UUID id,
		TransactionType transactionType,
		TransactionStatus status,
		UUID walletId,
		String walletName,
		UUID sourceWalletId,
		String sourceWalletName,
		UUID targetWalletId,
		String targetWalletName,
		UUID categoryId,
		String categoryName,
		long amount,
		String currencyCode,
		BalanceAdjustmentDirection balanceAdjustmentDirection,
		Instant transactionDate,
		String description,
		String note,
		String externalRef,
		List<TransactionAttachmentResponseDto> attachments,
		OffsetDateTime createdDate,
		OffsetDateTime lastModifiedDate
) {
}