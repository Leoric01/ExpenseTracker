package org.leoric.expensetracker.handler.exceptions;

public class AssetExchangeSameAssetException extends RuntimeException {
	public AssetExchangeSameAssetException(String msg) {
		super(msg);
	}
}