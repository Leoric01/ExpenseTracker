package org.leoric.expensetracker.handler.exceptions;

public class AssetExchangeSettledAmountRequiredException extends RuntimeException {
	public AssetExchangeSettledAmountRequiredException(String msg) {
		super(msg);
	}
}