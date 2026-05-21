package org.leoric.expensetracker.handler.exceptions;

public class KanbanBoardNotFoundException extends RuntimeException {
	public KanbanBoardNotFoundException(String message) {
		super(message);
	}
}