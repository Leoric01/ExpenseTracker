package org.leoric.expensetracker.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.leoric.expensetracker.asset.models.Asset;
import org.leoric.expensetracker.asset.repositories.AssetRepository;
import org.leoric.expensetracker.budget.models.BudgetPlan;
import org.leoric.expensetracker.category.models.Category;
import org.leoric.expensetracker.category.models.constants.CategoryKind;
import org.leoric.expensetracker.category.repositories.CategoryRepository;
import org.leoric.expensetracker.exchangerate.services.interfaces.ExchangeRateService;
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.leoric.expensetracker.transaction.models.Transaction;
import org.leoric.expensetracker.transaction.models.constants.TransactionType;
import org.leoric.expensetracker.transaction.repositories.TransactionRepository;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BudgetPlanSpentCalculatorTest {

	@Mock
	private TransactionRepository transactionRepository;
	@Mock
	private AssetRepository assetRepository;
	@Mock
	private ExchangeRateService exchangeRateService;
	@Mock
	private CategoryRepository categoryRepository;

	@InjectMocks
	private BudgetPlanSpentCalculator calculator;

	@Test
	void computeAlreadySpent_shouldIncludeFeeAndConvertByCurrentDateForExpensePlan() {
		UUID trackerId = UUID.randomUUID();
		Category root = Category.builder().id(UUID.randomUUID()).categoryKind(CategoryKind.EXPENSE).build();
		ExpenseTracker tracker = ExpenseTracker.builder().id(trackerId).build();
		BudgetPlan plan = BudgetPlan.builder()
				.expenseTracker(tracker)
				.category(root)
				.currencyCode("CZK")
				.validFrom(LocalDate.of(2026, 5, 1))
				.validTo(LocalDate.of(2026, 5, 31))
				.build();

		Transaction tx = Transaction.builder()
				.id(UUID.randomUUID())
				.transactionType(TransactionType.EXPENSE)
				.amount(100L)
				.feeAmount(5L)
				.currencyCode("BTC")
				.transactionDate(Instant.parse("2026-05-10T10:00:00Z"))
				.build();

		Asset czk = Asset.builder().code("CZK").scale(2).build();
		Asset btc = Asset.builder().code("BTC").scale(8).build();
		LocalDate today = LocalDate.now(ZoneOffset.UTC);

		when(categoryRepository.findByExpenseTrackerIdAndActiveTrue(trackerId)).thenReturn(List.of(root));
		when(transactionRepository.findCompletedByCategoryIdsAndDateRange(
				trackerId,
				TransactionType.EXPENSE,
				Set.of(root.getId()),
				Instant.parse("2026-05-01T00:00:00Z"),
				Instant.parse("2026-06-01T00:00:00Z")))
				.thenReturn(List.of(tx));
		when(assetRepository.findByCodeIgnoreCase("CZK")).thenReturn(Optional.of(czk));
		when(assetRepository.findByCodeIgnoreCase("BTC")).thenReturn(Optional.of(btc));
		when(exchangeRateService.convertAmount(105L, btc, czk, today)).thenReturn(170_000L);

		long result = calculator.computeAlreadySpent(plan);

		assertThat(result).isEqualTo(170_000L);
	}
}