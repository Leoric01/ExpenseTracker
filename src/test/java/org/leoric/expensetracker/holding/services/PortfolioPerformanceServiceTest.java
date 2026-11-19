package org.leoric.expensetracker.holding.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.leoric.expensetracker.asset.models.Asset;
import org.leoric.expensetracker.exchangerate.services.interfaces.ExchangeRateService;
import org.leoric.expensetracker.holding.dto.InvestmentPerformanceDto;
import org.leoric.expensetracker.holding.models.Holding;
import org.leoric.expensetracker.holding.models.InitialValuationMode;
import org.leoric.expensetracker.holding.repositories.HoldingRepository;
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
class PortfolioPerformanceServiceTest {
	@Mock HoldingRepository holdings;
	@Mock TransactionRepository transactions;
	@Mock ExchangeRateService rates;

	@Test
	void sellingOneTenthOfPositionMovesProfitFromUnrealizedToRealized() {
		UUID tracker = UUID.randomUUID();
		Instant created = Instant.parse("2024-01-01T00:00:00Z");
		Instant sold = Instant.parse("2025-01-02T00:00:00Z");
		Instant to = Instant.parse("2025-02-01T00:00:00Z");
		Asset btc = asset("BTC", 8);
		Asset czk = asset("CZK", 2);
		Holding crypto = holding(btc, created, 100_000_000L, 90_000_000L, czk);
		Holding cash = holding(czk, created, 0, null, null);
		Transaction sale = Transaction.builder().id(UUID.randomUUID()).transactionType(TransactionType.TRANSFER)
				.sourceHolding(crypto).targetHolding(cash).amount(10_000_000L).settledAmount(10_000_000L)
				.transactionDate(sold).build();
		when(holdings.findAllByExpenseTrackerId(tracker)).thenReturn(List.of(crypto, cash));
		when(transactions.findCompletedBefore(tracker, to)).thenReturn(List.of(sale));
		when(rates.convertAmount(90_000_000L, btc, czk, to)).thenReturn(90_000_000L);

		PortfolioPerformanceService.Result result = new PortfolioPerformanceService(holdings, transactions, rates, new org.leoric.expensetracker.transaction.services.TransactionValuationService(rates))
				.calculate(tracker, czk, to);

		assertThat(result.portfolio().realized()).isEqualTo(1_000_000L);
		assertThat(result.portfolio().unrealized()).isEqualTo(9_000_000L);
		assertThat(result.portfolio().total()).isEqualTo(10_000_000L);
		assertThat(result.holdings().get(crypto.getId()).total()).isEqualTo(10_000_000L);
		assertThat(result.holdings().get(cash.getId()).total()).isZero();
	}

	@Test
	void transferBetweenWalletsCountsOnlyFeeAndKeepsSharedCost() {
		UUID tracker = UUID.randomUUID();
		Instant created = Instant.parse("2024-01-01T00:00:00Z");
		Instant moved = Instant.parse("2025-01-02T00:00:00Z");
		Instant to = Instant.parse("2025-02-01T00:00:00Z");
		Asset btc = asset("BTC", 8);
		Asset czk = asset("CZK", 2);
		Holding source = holding(btc, created, 100, 1000L, czk);
		Holding target = holding(btc, created, 0, null, null);
		Transaction transfer = Transaction.builder().id(UUID.randomUUID()).transactionType(TransactionType.TRANSFER)
				.sourceHolding(source).targetHolding(target).amount(40).settledAmount(38L).feeAmount(2)
				.transactionDate(moved).build();
		when(holdings.findAllByExpenseTrackerId(tracker)).thenReturn(List.of(source, target));
		when(transactions.findCompletedBefore(tracker, to)).thenReturn(List.of(transfer));
		when(rates.convertAmount(98, btc, czk, to)).thenReturn(980L);

		PortfolioPerformanceService.Result result = new PortfolioPerformanceService(holdings, transactions, rates, new org.leoric.expensetracker.transaction.services.TransactionValuationService(rates))
				.calculate(tracker, czk, to);

		assertThat(result.portfolio().realized()).isEqualTo(-20);
		assertThat(result.portfolio().unrealized()).isZero();
		assertThat(result.portfolio().total()).isEqualTo(-20);
	}

	@Test
	void unknownInitialCostIsNotReportedAsZeroProfit() {
		UUID tracker = UUID.randomUUID();
		Instant created = Instant.parse("2024-01-01T00:00:00Z");
		Instant to = Instant.parse("2025-02-01T00:00:00Z");
		Asset btc = asset("BTC", 8);
		Asset czk = asset("CZK", 2);
		Holding crypto = holding(btc, created, 100, null, null);
		when(holdings.findAllByExpenseTrackerId(tracker)).thenReturn(List.of(crypto));
		when(transactions.findCompletedBefore(tracker, to)).thenReturn(List.of());

		InvestmentPerformanceDto result = new PortfolioPerformanceService(holdings, transactions, rates, new org.leoric.expensetracker.transaction.services.TransactionValuationService(rates))
				.calculate(tracker, czk, to).portfolio();

		assertThat(result.total()).isNull();
		assertThat(result.unavailableReason()).isNotBlank();
	}

	@Test
	void displayCurrencyTransferFeeReducesPortfolioResult() {
		UUID tracker = UUID.randomUUID();
		Instant created = Instant.parse("2024-01-01T00:00:00Z");
		Instant to = Instant.parse("2025-02-01T00:00:00Z");
		Asset czk = asset("CZK", 2);
		Holding source = holding(czk, created, 1000, null, null);
		Holding target = holding(czk, created, 0, null, null);
		Transaction transfer = Transaction.builder().id(UUID.randomUUID()).transactionType(TransactionType.TRANSFER)
				.sourceHolding(source).targetHolding(target).amount(100).settledAmount(98L).feeAmount(2)
				.transactionDate(created.plusSeconds(1)).build();
		when(holdings.findAllByExpenseTrackerId(tracker)).thenReturn(List.of(source, target));
		when(transactions.findCompletedBefore(tracker, to)).thenReturn(List.of(transfer));

		InvestmentPerformanceDto result = new PortfolioPerformanceService(holdings, transactions, rates,
				new org.leoric.expensetracker.transaction.services.TransactionValuationService(rates))
				.calculate(tracker, czk, to).portfolio();

		assertThat(result.realized()).isEqualTo(-2);
		assertThat(result.total()).isEqualTo(-2);
	}

	@Test
	void sellingBeyondLongPositionOpensShortAndMarksItAtEnd() {
		UUID tracker = UUID.randomUUID();
		Instant created = Instant.parse("2024-01-01T00:00:00Z");
		Instant to = Instant.parse("2025-02-01T00:00:00Z");
		Asset btc = asset("BTC", 8);
		Asset czk = asset("CZK", 2);
		Holding crypto = holding(btc, created, 100, 1000L, czk);
		Holding cash = holding(czk, created, 0, null, null);
		Transaction sale = Transaction.builder().id(UUID.randomUUID()).transactionType(TransactionType.TRANSFER)
				.sourceHolding(crypto).targetHolding(cash).amount(150).settledAmount(1800L)
				.transactionDate(created.plusSeconds(1)).build();
		when(holdings.findAllByExpenseTrackerId(tracker)).thenReturn(List.of(crypto, cash));
		when(transactions.findCompletedBefore(tracker, to)).thenReturn(List.of(sale));
		when(rates.convertAmount(-50, btc, czk, to)).thenReturn(-500L);

		InvestmentPerformanceDto result = new PortfolioPerformanceService(holdings, transactions, rates,
				new org.leoric.expensetracker.transaction.services.TransactionValuationService(rates))
				.calculate(tracker, czk, to).portfolio();

		assertThat(result.realized()).isEqualTo(200);
		assertThat(result.unrealized()).isEqualTo(100);
		assertThat(result.total()).isEqualTo(300);
	}

	@Test
	void mixedWalletBalancesUseClearlyMarkedAllocationWithExactSum() {
		UUID tracker = UUID.randomUUID();
		Instant created = Instant.parse("2024-01-01T00:00:00Z");
		Instant to = Instant.parse("2025-02-01T00:00:00Z");
		Asset btc = asset("BTC", 8);
		Asset czk = asset("CZK", 2);
		Holding positive = holding(btc, created, 100, 1000L, czk);
		Holding negative = holding(btc, created, -50, 500L, czk);
		when(holdings.findAllByExpenseTrackerId(tracker)).thenReturn(List.of(positive, negative));
		when(transactions.findCompletedBefore(tracker, to)).thenReturn(List.of());
		when(rates.convertAmount(50, btc, czk, to)).thenReturn(600L);

		PortfolioPerformanceService.Result result = new PortfolioPerformanceService(holdings, transactions, rates,
				new org.leoric.expensetracker.transaction.services.TransactionValuationService(rates))
				.calculate(tracker, czk, to);

		InvestmentPerformanceDto first = result.holdings().get(positive.getId());
		InvestmentPerformanceDto second = result.holdings().get(negative.getId());
		assertThat(first.artificialAllocation()).isTrue();
		assertThat(second.artificialAllocation()).isTrue();
		assertThat(first.unrealized() + second.unrealized()).isEqualTo(100);
		assertThat(result.portfolio().unrealized()).isEqualTo(100);
	}

	@Test
	void multiplePurchasesUseWeightedAverageAndActualSaleProceeds() {
		UUID tracker = UUID.randomUUID();
		Instant created = Instant.parse("2024-01-01T00:00:00Z");
		Instant to = Instant.parse("2025-02-01T00:00:00Z");
		Asset btc = asset("BTC", 8);
		Asset czk = asset("CZK", 2);
		Holding crypto = holding(btc, created, 0, null, null);
		Holding cash = holding(czk, created, 0, null, null);
		Transaction buy1 = exchange(cash, crypto, 10_000, 500, created.plusSeconds(1));
		Transaction buy2 = exchange(cash, crypto, 10_000, 400, created.plusSeconds(2));
		Transaction sale = exchange(crypto, cash, 300, 7_500, created.plusSeconds(3));
		when(holdings.findAllByExpenseTrackerId(tracker)).thenReturn(List.of(crypto, cash));
		when(transactions.findCompletedBefore(tracker, to)).thenReturn(List.of(buy1, buy2, sale));
		when(rates.convertAmount(600, btc, czk, to)).thenReturn(15_000L);

		InvestmentPerformanceDto result = new PortfolioPerformanceService(holdings, transactions, rates,
				new org.leoric.expensetracker.transaction.services.TransactionValuationService(rates))
				.calculate(tracker, czk, to).portfolio();

		assertThat(result.realized()).isEqualTo(833);
		assertThat(result.unrealized()).isEqualTo(1667);
		assertThat(result.total()).isEqualTo(2500);
	}

	@Test
	void changingDisplayCurrencyConvertsHistoricalCostAndProceedsAtTheirOwnDates() {
		UUID tracker = UUID.randomUUID();
		Instant created = Instant.parse("2024-01-01T00:00:00Z");
		Instant sold = created.plusSeconds(10);
		Instant to = Instant.parse("2025-02-01T00:00:00Z");
		Asset btc = asset("BTC", 8);
		Asset czk = asset("CZK", 2);
		Asset eur = asset("EUR", 2);
		Holding crypto = holding(btc, created, 100_000_000L, 90_000_000L, czk);
		Holding cash = holding(czk, created, 0, null, null);
		Transaction sale = exchange(crypto, cash, 10_000_000L, 10_000_000L, sold);
		when(holdings.findAllByExpenseTrackerId(tracker)).thenReturn(List.of(crypto, cash));
		when(transactions.findCompletedBefore(tracker, to)).thenReturn(List.of(sale));
		when(rates.convertAmount(90_000_000L, czk, eur, created)).thenReturn(3_600_000L);
		when(rates.convertAmount(10_000_000L, czk, eur, sold)).thenReturn(400_000L);
		when(rates.convertAmount(90_000_000L, btc, eur, to)).thenReturn(3_600_000L);
		when(rates.convertAmount(10_000_000L, czk, eur, to)).thenReturn(400_000L);

		InvestmentPerformanceDto result = new PortfolioPerformanceService(holdings, transactions, rates,
				new org.leoric.expensetracker.transaction.services.TransactionValuationService(rates))
				.calculate(tracker, eur, to).portfolio();

		assertThat(result.realized()).isEqualTo(40_000);
		assertThat(result.unrealized()).isEqualTo(360_000);
		assertThat(result.total()).isEqualTo(400_000);
	}

	@Test
	void transferWithMissingDestinationDoesNotProduceCompletePortfolioResult() {
		UUID tracker = UUID.randomUUID();
		Instant created = Instant.parse("2024-01-01T00:00:00Z");
		Instant to = Instant.parse("2025-02-01T00:00:00Z");
		Asset czk = asset("CZK", 2);
		Holding cash = holding(czk, created, 1000, null, null);
		Transaction transfer = Transaction.builder().id(UUID.randomUUID()).transactionType(TransactionType.TRANSFER)
				.sourceHolding(cash).amount(100).transactionDate(created.plusSeconds(1)).build();
		when(holdings.findAllByExpenseTrackerId(tracker)).thenReturn(List.of(cash));
		when(transactions.findCompletedBefore(tracker, to)).thenReturn(List.of(transfer));

		PortfolioPerformanceService.Result result = new PortfolioPerformanceService(holdings, transactions, rates,
				new org.leoric.expensetracker.transaction.services.TransactionValuationService(rates))
				.calculate(tracker, czk, to);

		assertThat(result.portfolio().total()).isNull();
		assertThat(result.holdings().get(cash.getId()).total()).isNull();
		assertThat(result.portfolio().unavailableReason()).isNotBlank();
	}

	@Test
	void legacyCrossCurrencyTransactionIsNotTreatedAsReliable() {
		UUID tracker = UUID.randomUUID();
		Instant created = Instant.parse("2024-01-01T00:00:00Z");
		Instant to = Instant.parse("2025-02-01T00:00:00Z");
		Asset btc = asset("BTC", 8);
		Asset czk = asset("CZK", 2);
		Holding crypto = holding(btc, created, 0, null, null);
		Transaction legacy = Transaction.builder().id(UUID.randomUUID()).transactionType(TransactionType.EXPENSE)
				.holding(crypto).amount(1000).currencyCode("CZK").settledAmount(1L)
				.transactionDate(created.plusSeconds(1)).build();
		when(holdings.findAllByExpenseTrackerId(tracker)).thenReturn(List.of(crypto));
		when(transactions.findCompletedBefore(tracker, to)).thenReturn(List.of(legacy));

		PortfolioPerformanceService.Result result = new PortfolioPerformanceService(holdings, transactions, rates,
				new org.leoric.expensetracker.transaction.services.TransactionValuationService(rates))
				.calculate(tracker, czk, to);

		assertThat(result.portfolio().total()).isNull();
		assertThat(result.holdings().get(crypto.getId()).unavailableReason()).contains("Starší transakce");
	}

	@Test
	void crossCurrencyIncomeFeeIsAppliedInHoldingUnitsOnlyOnce() {
		UUID tracker = UUID.randomUUID();
		Instant created = Instant.parse("2024-01-01T00:00:00Z");
		Instant at = created.plusSeconds(1);
		Instant to = Instant.parse("2025-02-01T00:00:00Z");
		Asset btc = asset("BTC", 8);
		Asset czk = asset("CZK", 2);
		Holding crypto = holding(btc, created, 0, null, null);
		Transaction income = Transaction.builder().id(UUID.randomUUID()).transactionType(TransactionType.INCOME)
				.holding(crypto).amount(10_000).currencyCode("CZK").feeAmount(1000)
				.settledAmount(4500L).settledFeeAmount(500L).balanceEffectVersion(1).transactionDate(at).build();
		when(holdings.findAllByExpenseTrackerId(tracker)).thenReturn(List.of(crypto));
		when(transactions.findCompletedBefore(tracker, to)).thenReturn(List.of(income));
		when(rates.convertAmount(5000, btc, czk, at)).thenReturn(10_000L);
		when(rates.convertAmount(4500, btc, czk, to)).thenReturn(9_000L);

		PortfolioPerformanceService.Result result = new PortfolioPerformanceService(holdings, transactions, rates,
				new org.leoric.expensetracker.transaction.services.TransactionValuationService(rates))
				.calculate(tracker, czk, to);

		assertThat(result.portfolio().realized()).isEqualTo(-1000);
		assertThat(result.portfolio().unrealized()).isZero();
		assertThat(result.portfolio().total()).isEqualTo(-1000);
	}

	private Transaction exchange(Holding source, Holding target, long amount, long settled, Instant at) {
		return Transaction.builder().id(UUID.randomUUID()).transactionType(TransactionType.TRANSFER)
				.sourceHolding(source).targetHolding(target).amount(amount).settledAmount(settled)
				.transactionDate(at).build();
	}

	private Asset asset(String code, int scale) {
		return Asset.builder().id(UUID.randomUUID()).code(code).scale(scale).build();
	}

	private Holding holding(Asset asset, Instant created, long initial, Long value, Asset valueAsset) {
		return Holding.builder().id(UUID.randomUUID()).asset(asset).createdDate(created).initialAmount(initial)
				.initialValuationMode(value == null ? null : InitialValuationMode.MANUAL)
				.initialValuationAmount(value).initialValuationAsset(valueAsset)
				.initialValuationAt(value == null ? null : created).build();
	}
}
