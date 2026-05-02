package org.leoric.expensetracker.transaction.dto;

import java.time.Instant;
import java.util.UUID;

public record CreateWalletTransferV2ResponseDto(
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
		Integer sourceAssetScale,
		String targetAssetCode,
		Integer targetAssetScale,
		Instant transactionDate
) {
}