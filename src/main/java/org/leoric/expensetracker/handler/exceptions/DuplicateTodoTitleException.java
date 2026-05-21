package org.leoric.expensetracker.handler.exceptions;

public class DuplicateTodoTitleException extends RuntimeException {
	public DuplicateTodoTitleException(String message) {
		super(message);
	}
}