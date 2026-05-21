package org.leoric.expensetracker.handler.exceptions;

public class DuplicateKanbanStageNameException extends RuntimeException {
	public DuplicateKanbanStageNameException(String message) {
		super(message);
	}
}