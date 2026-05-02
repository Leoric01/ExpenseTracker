package org.leoric.expensetracker.transaction.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionV2ResponseDto(
		UUID transactionId,
		TransactionV2OperationType operationType,
		TransferAmountCalculationMode calculationMode,
		long amount,
		long settledAmount,
		long feeAmount,
		long sourceDeduction,
		long targetAddition,
		boolean feeOverridden,
		UUID sourceHoldingId,
		UUID targetHoldingId,
		String sourceAssetCode,
		String targetAssetCode,
		String assetCode,
		BigDecimal exchangeRate,
		Instant transactionDate
) {
}