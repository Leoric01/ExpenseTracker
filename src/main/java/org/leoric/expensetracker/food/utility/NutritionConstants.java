package org.leoric.expensetracker.food.utility;

import java.math.BigDecimal;

public final class NutritionConstants {

	public static final BigDecimal KCAL_PER_KG = new BigDecimal("7700");
	public static final BigDecimal DAYS_PER_WEEK = new BigDecimal("7");
	public static final BigDecimal DEFAULT_ACTIVITY_MULTIPLIER = new BigDecimal("1.50");
	public static final BigDecimal FAT_RATIO_LEAN = new BigDecimal("0.22");
	public static final BigDecimal FAT_RATIO_HIGHER_BF = new BigDecimal("0.25");
	public static final BigDecimal PROTEIN_MULTIPLIER_LEAN = new BigDecimal("2.20");
	public static final BigDecimal PROTEIN_MULTIPLIER_MID = new BigDecimal("1.76");
	public static final BigDecimal PROTEIN_MULTIPLIER_HIGH_BF = new BigDecimal("1.606");
	public static final BigDecimal MAX_ADAPTIVE_ADJUSTMENT_KCAL = new BigDecimal("700");
	public static final String ALGORITHM_VERSION = "sheet-v1";

	private NutritionConstants() {
	}
}