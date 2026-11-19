package org.leoric.expensetracker.transaction.services;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class TransferAmounts {
	private TransferAmounts() {}

	static long convert(long amount, long fee, BigDecimal rate, int sourceScale, int targetScale) {
		return BigDecimal.valueOf(Math.subtractExact(amount, fee))
				.multiply(rate)
				.movePointRight(targetScale - sourceScale)
				.setScale(0, RoundingMode.HALF_UP)
				.longValueExact();
	}
}
