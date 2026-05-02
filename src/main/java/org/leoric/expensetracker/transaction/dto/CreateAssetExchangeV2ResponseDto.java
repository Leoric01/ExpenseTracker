package org.leoric.expensetracker.transaction.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreateAssetExchangeV2ResponseDto(
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
		String sourceHoldingName,
		UUID targetHoldingId,
		String targetHoldingName,
		String sourceAssetCode,
		Integer sourceAssetScale,
		String targetAssetCode,
		Integer targetAssetScale,
		String assetCode,
		BigDecimal exchangeRate,
		Instant transactionDate
) {
}