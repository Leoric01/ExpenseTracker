package org.leoric.expensetracker.handler.exceptions;

public class HabitNotFoundException extends RuntimeException {
	public HabitNotFoundException(String message) {
		super(message);
	}
}