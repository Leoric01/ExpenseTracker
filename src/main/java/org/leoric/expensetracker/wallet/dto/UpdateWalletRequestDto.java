package org.leoric.expensetracker.wallet.dto;

import org.leoric.expensetracker.validation.ValidCurrencyCode;
import org.leoric.expensetracker.wallet.models.constants.WalletType;

public record UpdateWalletRequestDto(
		String name,

		WalletType walletType,

		@ValidCurrencyCode
		String currencyCode
) {
}