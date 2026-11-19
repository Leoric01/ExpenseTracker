package org.leoric.expensetracker.holding.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.leoric.expensetracker.account.models.Account;
import org.leoric.expensetracker.asset.models.Asset;
import org.leoric.expensetracker.holding.dto.HoldingSummaryResponseDto;
import org.leoric.expensetracker.holding.models.Holding;
import org.leoric.expensetracker.institution.models.Institution;
import org.leoric.expensetracker.transaction.models.Transaction;
import org.leoric.expensetracker.transaction.models.constants.TransactionType;
import org.leoric.expensetracker.transaction.repositories.TransactionRepository;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HoldingSummaryBuilderImplTest {
	@Mock
	private TransactionRepository transactions;

	@Test
	void initialBalanceIsAFlowAtCreationRatherThanAnEarlierBalance() {
		Instant from = Instant.parse("2025-01-01T00:00:00Z");
		Instant created = Instant.parse("2025-01-10T12:00:00Z");
		Holding holding = holding("BTC", created, 100, 100);
		when(transactions.findCompletedByHoldingFrom(holding.getId(), from))
				.thenReturn(List.of());

		HoldingSummaryBuilderImpl builder = new HoldingSummaryBuilderImpl(transactions);
		HoldingSummaryResponseDto beforeCreation = builder.buildSummary(holding, from, created);
		HoldingSummaryResponseDto afterCreation = builder.buildSummary(holding, from, created.plusSeconds(1));

		assertThat(beforeCreation.startBalance()).isZero();
		assertThat(beforeCreation.endBalance()).isZero();
		assertThat(beforeCreation.totalIncome()).isZero();
		assertThat(afterCreation.startBalance()).isZero();
		assertThat(afterCreation.endBalance()).isEqualTo(100);
		assertThat(afterCreation.totalIncome()).isEqualTo(100);
	}

	@Test
	void exchangeUsesTargetSettledAmountAndExpenseUsesActualBalanceEffect() {
		Instant from = Instant.parse("2025-01-01T00:00:00Z");
		Instant to = Instant.parse("2025-02-01T00:00:00Z");
		Holding target = holding("BTC", Instant.parse("2024-01-01T00:00:00Z"), 0, 70);
		Holding source = holding("CZK", Instant.parse("2024-01-01T00:00:00Z"), 0, 0);
		Transaction exchange = Transaction.builder()
				.transactionType(TransactionType.TRANSFER)
				.sourceHolding(source)
				.targetHolding(target)
				.amount(10000)
				.settledAmount(100L)
				.transactionDate(from.plusSeconds(1))
				.build();
		Transaction expense = Transaction.builder()
				.transactionType(TransactionType.EXPENSE)
				.holding(target)
				.amount(25)
				.feeAmount(5)
				.transactionDate(from.plusSeconds(2))
				.build();
		when(transactions.findCompletedByHoldingFrom(target.getId(), from))
				.thenReturn(List.of(exchange, expense));

		HoldingSummaryResponseDto result = new HoldingSummaryBuilderImpl(transactions).buildSummary(target, from, to);

		assertThat(result.startBalance()).isZero();
		assertThat(result.endBalance()).isEqualTo(70);
		assertThat(result.totalTransferIn()).isEqualTo(100);
		assertThat(result.totalIncome()).isEqualTo(100);
		assertThat(result.totalExpense()).isEqualTo(30);
	}

	@Test
	void futureDatedTransactionsDoNotAppearInCurrentBalance() {
		Instant from = Instant.parse("2025-01-01T00:00:00Z");
		Holding holding = holding("BTC", Instant.parse("2024-01-01T00:00:00Z"), 100, 150);
		Transaction futureIncome = Transaction.builder()
				.transactionType(TransactionType.INCOME)
				.holding(holding)
				.amount(50)
				.transactionDate(Instant.now().plusSeconds(3600))
				.build();
		when(transactions.findCompletedByHoldingFrom(holding.getId(), from)).thenReturn(List.of(futureIncome));

		HoldingSummaryResponseDto result = new HoldingSummaryBuilderImpl(transactions)
				.buildSummary(holding, from, Instant.now());

		assertThat(result.startBalance()).isEqualTo(100);
		assertThat(result.endBalance()).isEqualTo(100);
		assertThat(result.totalIncome()).isZero();
	}

	@Test
	void crossCurrencyTransferWithoutReceivedAmountDoesNotFabricateBalance() {
		Instant from = Instant.parse("2025-01-01T00:00:00Z");
		Holding target = holding("BTC", Instant.parse("2024-01-01T00:00:00Z"), 0, 100);
		Holding source = holding("CZK", Instant.parse("2024-01-01T00:00:00Z"), 0, 0);
		Transaction transfer = Transaction.builder().id(UUID.randomUUID()).transactionType(TransactionType.TRANSFER)
				.sourceHolding(source).targetHolding(target).amount(10_000).transactionDate(from.plusSeconds(1)).build();
		when(transactions.findCompletedByHoldingFrom(target.getId(), from)).thenReturn(List.of(transfer));

		assertThatThrownBy(() -> new HoldingSummaryBuilderImpl(transactions).buildSummary(target, from, from.plusSeconds(20)))
				.hasMessageContaining("nemá zadanou přijatou částku");
	}

	private Holding holding(String code, Instant created, long initialAmount, long currentAmount) {
		return Holding.builder()
				.id(UUID.randomUUID())
				.createdDate(created)
				.initialAmount(initialAmount)
				.currentAmount(currentAmount)
				.asset(Asset.builder().code(code).scale(8).build())
				.account(Account.builder().name("Account")
						.institution(Institution.builder().name("Institution").build()).build())
				.build();
	}
}
