package org.leoric.expensetracker.handler.exceptions;

public class DuplicateTodoTagNameException extends RuntimeException {
	public DuplicateTodoTagNameException(String message) {
		super(message);
	}
}