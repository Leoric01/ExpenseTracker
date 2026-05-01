package org.leoric.expensetracker.transaction.models.constants;

// this one is if I manually correct balance, I need to do that through transaction. but I need to know if transaction is to plus or minus direction
public enum BalanceAdjustmentDirection {
	DEDUCTION,
	ADDITION
}