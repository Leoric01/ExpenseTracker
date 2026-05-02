package org.leoric.expensetracker.handler.exceptions;

public class TransferExchangeRateInvalidException extends RuntimeException {
	public TransferExchangeRateInvalidException(String msg) {
		super(msg);
	}
}