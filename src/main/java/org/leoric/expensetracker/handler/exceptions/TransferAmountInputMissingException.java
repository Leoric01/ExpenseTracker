package org.leoric.expensetracker.handler.exceptions;

public class TransferAmountInputMissingException extends RuntimeException {
	public TransferAmountInputMissingException(String msg) {
		super(msg);
	}
}