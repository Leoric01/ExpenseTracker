package org.leoric.expensetracker.handler.exceptions;

public class KanbanTagNotFoundException extends RuntimeException {
	public KanbanTagNotFoundException(String message) {
		super(message);
	}
}