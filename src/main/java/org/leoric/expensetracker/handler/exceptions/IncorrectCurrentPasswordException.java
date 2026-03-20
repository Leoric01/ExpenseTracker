package org.leoric.expensetracker.handler.exceptions;

public class IncorrectCurrentPasswordException extends RuntimeException {
	public IncorrectCurrentPasswordException(String message) {
		super(message);
	}
}