package org.leoric.expensetracker.handler.exceptions;

public class DuplicateBudgetPlanNameException extends RuntimeException {
	public DuplicateBudgetPlanNameException(String message) {
		super(message);
	}
}