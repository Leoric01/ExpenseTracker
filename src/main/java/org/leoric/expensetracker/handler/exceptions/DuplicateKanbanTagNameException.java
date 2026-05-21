package org.leoric.expensetracker.handler.exceptions;

public class DuplicateKanbanTagNameException extends RuntimeException {
	public DuplicateKanbanTagNameException(String message) {
		super(message);
	}
}