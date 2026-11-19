package org.leoric.expensetracker.transaction.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.asset.models.Asset;
import org.leoric.expensetracker.asset.models.constants.MarketDataSource;
import org.leoric.expensetracker.handler.exceptions.OperationNotPermittedException;
import org.leoric.expensetracker.transaction.dto.ManualTransactionValuationRequestDto;
import org.leoric.expensetracker.transaction.dto.TransactionValuationResponseDto;
import org.leoric.expensetracker.transaction.models.Transaction;
import org.leoric.expensetracker.transaction.models.constants.TransactionStatus;
import org.leoric.expensetracker.transaction.repositories.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ManualTransactionValuationService {
	private final TransactionRepository transactions;
	private final TransactionValuationService valuations;

	@Transactional(readOnly = true)
	public TransactionValuationResponseDto get(UUID trackerId, UUID transactionId) {
		return response(transaction(trackerId, transactionId));
	}

	@Transactional
	public TransactionValuationResponseDto update(UUID trackerId, UUID transactionId,
			ManualTransactionValuationRequestDto request) {
		Transaction transaction = transaction(trackerId, transactionId);
		Asset quote = transaction.getExpenseTracker().getPreferredDisplayAsset();
		Asset base = valuations.valuationBase(transaction, quote);
		assertValuable(transaction, base, quote);
		transaction.setMarketRate(request.rate());
		transaction.setMarketRateBaseAsset(base);
		transaction.setMarketRateQuoteAsset(quote);
		transaction.setMarketRateTransactionDate(transaction.getTransactionDate());
		transaction.setMarketRateObservedAt(Instant.now());
		transaction.setMarketRateSource(MarketDataSource.MANUAL);
		transaction.setMarketRateFetchedAt(null);
		transaction.setMarketRateDailyHistorical(null);
		return response(transactions.save(transaction));
	}

	private Transaction transaction(UUID trackerId, UUID transactionId) {
		return transactions.findWithDetailsByIdAndExpenseTrackerId(transactionId, trackerId)
				.orElseThrow(() -> new EntityNotFoundException("Transaction not found"));
	}

	private TransactionValuationResponseDto response(Transaction transaction) {
		Asset quote = transaction.getExpenseTracker().getPreferredDisplayAsset();
		Asset base = valuations.valuationBase(transaction, quote);
		assertValuable(transaction, base, quote);
		boolean matching = transaction.getMarketRate() != null
				&& transaction.getMarketRateBaseAsset() != null && transaction.getMarketRateQuoteAsset() != null
				&& transaction.getMarketRateBaseAsset().getCode().equalsIgnoreCase(base.getCode())
				&& transaction.getMarketRateQuoteAsset().getCode().equalsIgnoreCase(quote.getCode())
				&& transaction.getTransactionDate().equals(transaction.getMarketRateTransactionDate());
		return new TransactionValuationResponseDto(base.getCode(), quote.getCode(), transaction.getTransactionDate(),
				matching ? transaction.getMarketRate() : null, matching ? transaction.getMarketRateSource() : null,
				matching ? transaction.getMarketRateObservedAt() : null,
				matching ? transaction.getMarketRateFetchedAt() : null,
				matching ? transaction.getMarketRateDailyHistorical() : null);
	}

	private void assertValuable(Transaction transaction, Asset base, Asset quote) {
		if (transaction.getStatus() != TransactionStatus.COMPLETED || transaction.getTransactionDate().isAfter(Instant.now())) {
			throw new OperationNotPermittedException("Only completed transactions dated no later than now can be valued");
		}
		if (base == null || quote == null || base.getCode().equalsIgnoreCase(quote.getCode())) {
			throw new OperationNotPermittedException("No separate market rate is needed or the display asset is not configured");
		}
	}
}
