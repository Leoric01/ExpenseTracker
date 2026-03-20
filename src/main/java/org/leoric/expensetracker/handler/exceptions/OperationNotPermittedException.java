package org.leoric.expensetracker.handler.exceptions;

public class OperationNotPermittedException extends RuntimeException {
	public OperationNotPermittedException(String msg) {
		super(msg);
	}
}