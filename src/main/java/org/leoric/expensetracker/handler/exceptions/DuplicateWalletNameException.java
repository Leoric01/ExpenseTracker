package org.leoric.expensetracker.handler.exceptions;

public class DuplicateWalletNameException extends RuntimeException {
	public DuplicateWalletNameException(String message) {
		super(message);
	}
}