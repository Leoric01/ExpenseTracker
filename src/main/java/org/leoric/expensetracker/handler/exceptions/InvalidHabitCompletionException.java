package org.leoric.expensetracker.handler.exceptions;

public class InvalidHabitCompletionException extends RuntimeException {
	public InvalidHabitCompletionException(String message) {
		super(message);
	}
}