package org.leoric.expensetracker.transaction.services.interfaces;

import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.transaction.dto.AssetExchangeRateQuoteRequestDto;
import org.leoric.expensetracker.transaction.dto.AssetExchangeRateQuoteResponseDto;
import org.leoric.expensetracker.transaction.dto.CreateAssetExchangeV2RequestDto;
import org.leoric.expensetracker.transaction.dto.CreateAssetExchangeV2ResponseDto;
import org.leoric.expensetracker.transaction.dto.CreateWalletTransferV2RequestDto;
import org.leoric.expensetracker.transaction.dto.CreateWalletTransferV2ResponseDto;

import java.util.UUID;

public interface TransactionV2Service {

	CreateWalletTransferV2ResponseDto createWalletTransfer(User currentUser, UUID trackerId, CreateWalletTransferV2RequestDto request);

	CreateAssetExchangeV2ResponseDto createAssetExchange(User currentUser, UUID trackerId, CreateAssetExchangeV2RequestDto request);

	AssetExchangeRateQuoteResponseDto assetExchangeRateQuote(User currentUser, UUID trackerId, AssetExchangeRateQuoteRequestDto request);
}