package org.leoric.expensetracker.handler.exceptions;

public class NewPasswordDoesNotMatchException extends RuntimeException {
	public NewPasswordDoesNotMatchException(String message) {
		super(message);
	}
}