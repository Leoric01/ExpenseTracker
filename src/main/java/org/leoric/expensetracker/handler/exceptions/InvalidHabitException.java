package org.leoric.expensetracker.handler.exceptions;

public class InvalidHabitException extends RuntimeException {
	public InvalidHabitException(String message) {
		super(message);
	}
}