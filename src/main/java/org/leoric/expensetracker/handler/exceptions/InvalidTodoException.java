package org.leoric.expensetracker.handler.exceptions;

public class InvalidTodoException extends RuntimeException {
	public InvalidTodoException(String message) {
		super(message);
	}
}