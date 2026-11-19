package org.leoric.expensetracker.holding.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.leoric.expensetracker.asset.models.Asset;
import org.leoric.expensetracker.exchangerate.services.interfaces.ExchangeRateService;
import org.leoric.expensetracker.holding.dto.HoldingPeriodPerformanceDto;
import org.leoric.expensetracker.holding.dto.HoldingSummaryResponseDto;
import org.leoric.expensetracker.holding.models.Holding;
import org.leoric.expensetracker.transaction.models.Transaction;
import org.leoric.expensetracker.transaction.models.constants.TransactionType;
import org.leoric.expensetracker.transaction.repositories.TransactionRepository;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HoldingPeriodPerformanceServiceTest {
	@Mock TransactionRepository transactions;
	@Mock ExchangeRateService rates;

	@Test
	void openingDepositIsNotProfit() {
		Instant from = Instant.parse("2025-01-01T00:00:00Z");
		Instant created = Instant.parse("2025-01-10T12:00:00Z");
		Instant to = Instant.parse("2025-02-01T00:00:00Z");
		Asset btc = asset("BTC", 8);
		Asset czk = asset("CZK", 2);
		Holding holding = Holding.builder().id(UUID.randomUUID()).asset(btc).initialAmount(100).createdDate(created).build();
		when(rates.convertAmount(100, btc, czk, created)).thenReturn(1000L);
		when(transactions.findCompletedByHoldingAndDateRange(holding.getId(), from, to)).thenReturn(List.of());

		HoldingPeriodPerformanceDto result = new HoldingPeriodPerformanceService(transactions, rates, new org.leoric.expensetracker.transaction.services.TransactionValuationService(rates))
				.calculate(holding, czk, summary(holding, from, to), 0L, 1100L);

		assertThat(result.convertedIncome()).isEqualTo(1000);
		assertThat(result.result()).isEqualTo(100);
		assertThat(result.estimated()).isTrue();
	}

	@Test
	void purchaseUsesActualDisplayCurrencyPaidRatherThanClosingRate() {
		Instant from = Instant.parse("2025-01-01T00:00:00Z");
		Instant to = Instant.parse("2025-02-01T00:00:00Z");
		Asset btc = asset("BTC", 8);
		Asset czk = asset("CZK", 2);
		Holding target = Holding.builder().id(UUID.randomUUID()).asset(btc)
				.createdDate(Instant.parse("2024-01-01T00:00:00Z")).build();
		Holding source = Holding.builder().id(UUID.randomUUID()).asset(czk).build();
		Transaction purchase = Transaction.builder().transactionType(TransactionType.TRANSFER)
				.sourceHolding(source).targetHolding(target).amount(10_000).settledAmount(50L)
				.transactionDate(from.plusSeconds(10)).build();
		when(transactions.findCompletedByHoldingAndDateRange(target.getId(), from, to)).thenReturn(List.of(purchase));

		HoldingPeriodPerformanceDto result = new HoldingPeriodPerformanceService(transactions, rates, new org.leoric.expensetracker.transaction.services.TransactionValuationService(rates))
				.calculate(target, czk, summary(target, from, to), 0L, 12_000L);

		assertThat(result.convertedIncome()).isEqualTo(10_000);
		assertThat(result.result()).isEqualTo(2_000);
	}

	@Test
	void missingMovementRateMakesResultUnavailable() {
		Instant from = Instant.parse("2025-01-01T00:00:00Z");
		Instant to = Instant.parse("2025-02-01T00:00:00Z");
		Instant at = from.plusSeconds(10);
		Asset btc = asset("BTC", 8);
		Asset czk = asset("CZK", 2);
		Holding holding = Holding.builder().id(UUID.randomUUID()).asset(btc)
				.createdDate(Instant.parse("2024-01-01T00:00:00Z")).build();
		Transaction income = Transaction.builder().transactionType(TransactionType.INCOME).holding(holding)
				.amount(100).transactionDate(at).build();
		when(transactions.findCompletedByHoldingAndDateRange(holding.getId(), from, to)).thenReturn(List.of(income));
		when(rates.convertAmount(100, btc, czk, at)).thenReturn(null);

		HoldingPeriodPerformanceDto result = new HoldingPeriodPerformanceService(transactions, rates, new org.leoric.expensetracker.transaction.services.TransactionValuationService(rates))
				.calculate(holding, czk, summary(holding, from, to), 0L, 1000L);

		assertThat(result.result()).isNull();
		assertThat(result.unavailableReason()).isNotBlank();
	}

	@Test
	void walletTransferFeeIsARealPeriodLossRatherThanAnExternalFlow() {
		Instant from = Instant.parse("2025-01-01T00:00:00Z");
		Instant to = Instant.parse("2025-02-01T00:00:00Z");
		Instant at = from.plusSeconds(10);
		Asset btc = asset("BTC", 8);
		Asset czk = asset("CZK", 2);
		Holding source = Holding.builder().id(UUID.randomUUID()).asset(btc)
				.createdDate(Instant.parse("2024-01-01T00:00:00Z")).build();
		Holding target = Holding.builder().id(UUID.randomUUID()).asset(btc)
				.createdDate(Instant.parse("2024-01-01T00:00:00Z")).build();
		Transaction transfer = Transaction.builder().transactionType(TransactionType.TRANSFER)
				.sourceHolding(source).targetHolding(target).amount(40).settledAmount(38L)
				.feeAmount(2).transactionDate(at).build();
		when(transactions.findCompletedByHoldingAndDateRange(source.getId(), from, to)).thenReturn(List.of(transfer));
		when(rates.convertAmount(-38L, btc, czk, at)).thenReturn(-380L);

		HoldingPeriodPerformanceDto result = new HoldingPeriodPerformanceService(
				transactions, rates, new org.leoric.expensetracker.transaction.services.TransactionValuationService(rates))
				.calculate(source, czk, summary(source, from, to), 1000L, 600L);

		assertThat(result.convertedExpense()).isEqualTo(380);
		assertThat(result.result()).isEqualTo(-20);
	}

	@Test
	void incomeFeeIsARealPeriodLossRatherThanReducingGrossInflow() {
		Instant from = Instant.parse("2025-01-01T00:00:00Z");
		Instant to = Instant.parse("2025-02-01T00:00:00Z");
		Asset czk = asset("CZK", 2);
		Holding holding = Holding.builder().id(UUID.randomUUID()).asset(czk)
				.createdDate(Instant.parse("2024-01-01T00:00:00Z")).build();
		Transaction income = Transaction.builder().transactionType(TransactionType.INCOME).holding(holding)
				.amount(100).feeAmount(10).settledAmount(90L).settledFeeAmount(10L).balanceEffectVersion(1)
				.currencyCode("CZK").transactionDate(from.plusSeconds(10)).build();
		when(transactions.findCompletedByHoldingAndDateRange(holding.getId(), from, to)).thenReturn(List.of(income));

		HoldingPeriodPerformanceDto result = new HoldingPeriodPerformanceService(
				transactions, rates, new org.leoric.expensetracker.transaction.services.TransactionValuationService(rates))
				.calculate(holding, czk, summary(holding, from, to), 0L, 90L);

		assertThat(result.convertedIncome()).isEqualTo(100);
		assertThat(result.result()).isEqualTo(-10);
	}

	private Asset asset(String code, int scale) {
		return Asset.builder().code(code).scale(scale).build();
	}

	private HoldingSummaryResponseDto summary(Holding holding, Instant from, Instant to) {
		return new HoldingSummaryResponseDto(holding.getId(), "Account", "Institution", holding.getAsset().getCode(),
				holding.getAsset().getScale(), from, to, 0, 0, 0, 0, 0, 0, 0,
				List.of(), List.of(), null, null, null);
	}
}
