package org.leoric.expensetracker.handler.exceptions;

public class DuplicateExpenseTrackerNameException extends RuntimeException {
	public DuplicateExpenseTrackerNameException(String message) {
		super(message);
	}
}