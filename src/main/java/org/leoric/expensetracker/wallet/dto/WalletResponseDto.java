package org.leoric.expensetracker.wallet.dto;

import org.leoric.expensetracker.wallet.models.constants.WalletType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record WalletResponseDto(
		UUID id,
		String name,
		WalletType walletType,
		String currencyCode,
		String iconUrl,
		String iconColor,
		long initialBalance,
		long currentBalance,
		boolean active,
		OffsetDateTime createdDate,
		OffsetDateTime lastModifiedDate
) {
}