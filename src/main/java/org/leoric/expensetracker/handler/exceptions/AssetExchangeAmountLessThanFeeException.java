package org.leoric.expensetracker.handler.exceptions;

public class AssetExchangeAmountLessThanFeeException extends RuntimeException {
	public AssetExchangeAmountLessThanFeeException(String msg) {
		super(msg);
	}
}