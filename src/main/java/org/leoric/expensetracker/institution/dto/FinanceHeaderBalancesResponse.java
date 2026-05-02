package org.leoric.expensetracker.institution.dto;

import java.time.Instant;
import java.util.List;

public record FinanceHeaderBalancesResponse(
		Instant periodFrom,
		Instant periodTo,
		String displayAssetCode,
		Integer displayAssetScale,
		Long grandTotalConverted,
		List<FinanceHeaderNativeBalanceRow> nativeBalances
) {
}