package org.leoric.expensetracker.handler.exceptions;

public class NotAuthorizedForThisExpenseTrackerException extends RuntimeException {
	public NotAuthorizedForThisExpenseTrackerException() {
		super("You are not authorized to manage this expense tracker");
	}

	public NotAuthorizedForThisExpenseTrackerException(String message) {
		super(message);
	}
}