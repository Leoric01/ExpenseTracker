package org.leoric.expensetracker.holding.services;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class WeightedPositionTest {
	@Test
	void sellingPartOfLongOnlyMovesProfitFromUnrealizedToRealized() {
		WeightedPosition position = new WeightedPosition();
		position.apply(decimal("100000000"), decimal("90000000"));
		BigDecimal realized = position.apply(decimal("-10000000"), decimal("10000000"));

		assertThat(realized).isEqualByComparingTo("1000000");
		assertThat(position.quantity()).isEqualByComparingTo("90000000");
		assertThat(position.unrealized(decimal("90000000"))).isEqualByComparingTo("9000000");
	}

	@Test
	void weightedAverageSurvivesPurchasesAtDifferentPrices() {
		WeightedPosition position = new WeightedPosition();
		position.apply(decimal("500"), decimal("10000"));
		position.apply(decimal("400"), decimal("10000"));
		BigDecimal realized = position.apply(decimal("-300"), decimal("7500"));

		assertThat(realized).isEqualByComparingTo("833.333333333333333333333333333333");
		assertThat(position.unrealized(decimal("15000"))).isEqualByComparingTo("1666.66666666666666666666666666667");
	}

	@Test
	void crossingThroughZeroClosesLongThenOpensShortAndViceVersa() {
		WeightedPosition position = new WeightedPosition();
		position.apply(decimal("10"), decimal("100"));
		assertThat(position.apply(decimal("-15"), decimal("180"))).isEqualByComparingTo("20");
		assertThat(position.quantity()).isEqualByComparingTo("-5");
		assertThat(position.unrealized(decimal("-50"))).isEqualByComparingTo("10");
		assertThat(position.apply(decimal("8"), decimal("80"))).isEqualByComparingTo("10");
		assertThat(position.quantity()).isEqualByComparingTo("3");
		assertThat(position.unrealized(decimal("30"))).isEqualByComparingTo("0");
	}

	private BigDecimal decimal(String value) {
		return new BigDecimal(value);
	}
}
