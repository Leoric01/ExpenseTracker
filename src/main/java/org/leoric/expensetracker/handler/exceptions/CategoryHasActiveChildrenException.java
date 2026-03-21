package org.leoric.expensetracker.handler.exceptions;

public class CategoryHasActiveChildrenException extends RuntimeException {
	public CategoryHasActiveChildrenException(String message) {
		super(message);
	}
}