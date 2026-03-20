package org.leoric.expensetracker.handler.exceptions;

public class EmailAlreadyInUseException extends RuntimeException {
	public EmailAlreadyInUseException(String message) {
		super(message);
	}
}