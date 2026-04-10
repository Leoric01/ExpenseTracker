package org.leoric.expensetracker.handler.exceptions;

public class DuplicateHabitNameException extends RuntimeException {
	public DuplicateHabitNameException(String message) {
		super(message);
	}
}