package org.leoric.expensetracker.handler.exceptions;

public class TodoNotFoundException extends RuntimeException {
	public TodoNotFoundException(String message) {
		super(message);
	}
}