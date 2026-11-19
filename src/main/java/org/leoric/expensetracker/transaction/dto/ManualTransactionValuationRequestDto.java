package org.leoric.expensetracker.transaction.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ManualTransactionValuationRequestDto(
		@NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 12) BigDecimal rate
) {}
