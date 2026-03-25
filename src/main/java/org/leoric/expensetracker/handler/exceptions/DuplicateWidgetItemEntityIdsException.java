package org.leoric.expensetracker.handler.exceptions;

public class DuplicateWidgetItemEntityIdsException extends RuntimeException {
	public DuplicateWidgetItemEntityIdsException(String message) {
		super(message);
	}
}