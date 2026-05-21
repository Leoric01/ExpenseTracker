package org.leoric.expensetracker.handler.exceptions;

public class TodoTagNotFoundException extends RuntimeException {
	public TodoTagNotFoundException(String message) {
		super(message);
	}
}