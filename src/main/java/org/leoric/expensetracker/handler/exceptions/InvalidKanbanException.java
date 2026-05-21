package org.leoric.expensetracker.handler.exceptions;

public class InvalidKanbanException extends RuntimeException {
	public InvalidKanbanException(String message) {
		super(message);
	}
}