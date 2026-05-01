package org.leoric.expensetracker.transaction.dto;

import org.leoric.expensetracker.transaction.models.constants.BalanceAdjustmentDirection;
import org.leoric.expensetracker.transaction.models.constants.TransactionStatus;
import org.leoric.expensetracker.transaction.models.constants.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
public record TransactionResponseDto(
		UUID id,
		TransactionType transactionType,
		TransactionStatus status,
		UUID holdingId,
		String holdingName,
		UUID sourceHoldingId,
		String sourceHoldingName,
		String sourceHoldingAssetCode,
		Integer sourceHoldingAssetScale,
		UUID targetHoldingId,
		String targetHoldingName,
		String targetHoldingAssetCode,
		Integer targetHoldingAssetScale,
		UUID categoryId,
		String categoryName,
		UUID rootCategoryId,
		String rootCategoryName,
		long amount,
		String assetCode,
		Integer assetScale,
		BigDecimal exchangeRate,
		long feeAmount,
		Long settledAmount,
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