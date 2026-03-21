package org.leoric.expensetracker.handler.exceptions;

public class DuplicateCategoryNameException extends RuntimeException {
	public DuplicateCategoryNameException(String message) {
		super(message);
	}
}