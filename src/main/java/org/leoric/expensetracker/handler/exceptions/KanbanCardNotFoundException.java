package org.leoric.expensetracker.handler.exceptions;

public class KanbanCardNotFoundException extends RuntimeException {
	public KanbanCardNotFoundException(String message) {
		super(message);
	}
}