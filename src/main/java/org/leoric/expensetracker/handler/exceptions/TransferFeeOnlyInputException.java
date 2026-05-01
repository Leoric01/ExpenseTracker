package org.leoric.expensetracker.handler.exceptions;

public class TransferFeeOnlyInputException extends RuntimeException {
	public TransferFeeOnlyInputException(String msg) {
		super(msg);
	}
}