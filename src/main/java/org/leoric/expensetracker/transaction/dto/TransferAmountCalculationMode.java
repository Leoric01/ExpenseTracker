package org.leoric.expensetracker.transaction.dto;

public enum TransferAmountCalculationMode {
	AMOUNT_ONLY_DEFAULTED,
	SETTLED_ONLY_DEFAULTED,
	AMOUNT_AND_FEE,
	SETTLED_AND_FEE,
	AMOUNT_AND_SETTLED,
	ALL_FIELDS_RECONCILED
}