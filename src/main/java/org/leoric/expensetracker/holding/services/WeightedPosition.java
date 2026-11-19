package org.leoric.expensetracker.holding.services;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Weighted long cost or short proceeds, in display minor units, for one portfolio asset.
 */
final class WeightedPosition {
	private static final MathContext MC = MathContext.DECIMAL128;

	private BigDecimal quantity = BigDecimal.ZERO;
	private BigDecimal openValue = BigDecimal.ZERO;

	BigDecimal apply(BigDecimal change, BigDecimal value) {
		if (value.signum() < 0 || change.signum() == 0) {
			if (change.signum() == 0 && value.signum() == 0) return BigDecimal.ZERO;
			throw new IllegalArgumentException("Quantity change must be nonzero and value nonnegative");
		}
		BigDecimal remaining = change.abs();
		BigDecimal realized = BigDecimal.ZERO;
		if (quantity.signum() != 0 && quantity.signum() != change.signum()) {
			BigDecimal closing = remaining.min(quantity.abs());
			BigDecimal existingValue = openValue.multiply(closing, MC).divide(quantity.abs(), MC);
			BigDecimal closingValue = value.multiply(closing, MC).divide(change.abs(), MC);
			realized = quantity.signum() > 0
					? closingValue.subtract(existingValue, MC)
					: existingValue.subtract(closingValue, MC);
			openValue = openValue.subtract(existingValue, MC);
			quantity = quantity.add(closing.multiply(BigDecimal.valueOf(change.signum())), MC);
			remaining = remaining.subtract(closing);
			if (quantity.signum() == 0) openValue = BigDecimal.ZERO;
		}
		if (remaining.signum() > 0) {
			BigDecimal openingValue = value.multiply(remaining, MC).divide(change.abs(), MC);
			quantity = quantity.add(remaining.multiply(BigDecimal.valueOf(change.signum())), MC);
			openValue = openValue.add(openingValue, MC);
		}
		return realized;
	}

	BigDecimal quantity() {
		return quantity;
	}

	BigDecimal unrealized(BigDecimal signedMarketValue) {
		return signedMarketValue.subtract(openValue.multiply(BigDecimal.valueOf(quantity.signum())), MC);
	}
}
