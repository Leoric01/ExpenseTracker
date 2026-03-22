package org.leoric.expensetracker.wallet.dto;

import org.leoric.expensetracker.auth.dto.WidgetItemResponseDto;

import java.time.Instant;
import java.util.List;

public record WalletDashboardResponseDto(
		Instant periodFrom,
		Instant periodTo,
		List<WidgetItemResponseDto> widgetOrder,
		List<WalletSummaryResponseDto> wallets
) {
}