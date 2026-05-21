package org.leoric.expensetracker.handler.exceptions;

public class KanbanStageNotFoundException extends RuntimeException {
	public KanbanStageNotFoundException(String message) {
		super(message);
	}
}