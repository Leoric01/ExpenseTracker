package org.leoric.expensetracker.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.leoric.expensetracker.validation.ValidCurrencyCode;
import org.leoric.expensetracker.wallet.models.constants.WalletType;

public record CreateWalletRequestDto(
		@NotBlank(message = "Name is required")
		String name,

		@NotNull(message = "Wallet type is required")
		WalletType walletType,

		@NotBlank(message = "Currency code is required")
		@ValidCurrencyCode
		String currencyCode,

		Long initialBalance
) {
}