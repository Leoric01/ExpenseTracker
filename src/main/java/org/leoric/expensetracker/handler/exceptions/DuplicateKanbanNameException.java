package org.leoric.expensetracker.handler.exceptions;

public class DuplicateKanbanNameException extends RuntimeException {
	public DuplicateKanbanNameException(String message) {
		super(message);
	}
}