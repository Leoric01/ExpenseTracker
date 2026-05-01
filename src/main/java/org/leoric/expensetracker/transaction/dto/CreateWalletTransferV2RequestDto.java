package org.leoric.expensetracker.transaction.dto;

import java.time.Instant;
import java.util.UUID;

public record CreateWalletTransferV2RequestDto(
		UUID sourceHoldingId,
		UUID targetHoldingId,
		Long amount,
		Long settledAmount,
		Long feeAmount,
		Instant transactionDate,
		String description,
		String note,
		String externalRef
) {
}