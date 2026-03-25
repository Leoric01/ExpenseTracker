package org.leoric.expensetracker.handler.exceptions;

public class WidgetItemReorderMismatchException extends RuntimeException {
	public WidgetItemReorderMismatchException(String message) {
		super(message);
	}
}