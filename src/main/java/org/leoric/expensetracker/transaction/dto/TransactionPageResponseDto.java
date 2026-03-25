package org.leoric.expensetracker.transaction.dto;

import java.util.List;

public record TransactionPageResponseDto(
		List<TransactionResponseDto> content,
		PageMetaDto page,
		TransactionTotalsDto totals
) {
}