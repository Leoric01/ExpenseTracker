package org.leoric.expensetracker.transaction.dto;

import java.util.List;

public record TransactionPageResponseDto(
		List<TransactionPageItemResponseDto> content,
		PageMetaDto page,
		TransactionTotalsDto totals
) {
}