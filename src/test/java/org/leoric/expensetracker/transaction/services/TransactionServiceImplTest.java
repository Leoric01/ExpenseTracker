package org.leoric.expensetracker.transaction.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.leoric.expensetracker.account.models.Account;
import org.leoric.expensetracker.asset.models.Asset;
import org.leoric.expensetracker.asset.repositories.AssetRepository;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.leoric.expensetracker.handler.exceptions.OperationNotPermittedException;
import org.leoric.expensetracker.holding.models.Holding;
import org.leoric.expensetracker.holding.repositories.HoldingRepository;
import org.leoric.expensetracker.institution.models.Institution;
import org.leoric.expensetracker.transaction.dto.TransactionResponseDto;
import org.leoric.expensetracker.transaction.dto.UpdateTransactionRequestDto;
import org.leoric.expensetracker.transaction.mapstruct.TransactionMapper;
import org.leoric.expensetracker.transaction.models.Transaction;
import org.leoric.expensetracker.transaction.models.constants.TransactionStatus;
import org.leoric.expensetracker.transaction.models.constants.TransactionType;
import org.leoric.expensetracker.transaction.repositories.TransactionRepository;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceImplTest {

	@Mock
	private TransactionRepository transactionRepository;
	@Mock
	private HoldingRepository holdingRepository;
	@Mock
	private AssetRepository assetRepository;
	@Mock
	private TransactionMapper transactionMapper;

	@InjectMocks
	private TransactionServiceImpl service;

	private User user;
	private UUID trackerId;
	private ExpenseTracker tracker;
	private Holding holdingA;
	private Holding holdingB;

	@BeforeEach
	void setUp() {
		trackerId = UUID.randomUUID();
		user = User.builder().id(UUID.randomUUID()).email("test@example.com").build();
		tracker = ExpenseTracker.builder().id(trackerId).name("Tracker").build();

		Institution institution = Institution.builder().expenseTracker(tracker).build();
		Account accountA = Account.builder().institution(institution).name("A").build();
		Account accountB = Account.builder().institution(institution).name("B").build();

		holdingA = Holding.builder()
				.id(UUID.randomUUID())
				.account(accountA)
				.asset(org.leoric.expensetracker.asset.models.Asset.builder().code("CZK").build())
				.currentAmount(10_000)
				.active(true)
				.build();

		holdingB = Holding.builder()
				.id(UUID.randomUUID())
				.account(accountB)
				.asset(org.leoric.expensetracker.asset.models.Asset.builder().code("CZK").build())
				.currentAmount(20_000)
				.active(true)
				.build();
	}

	@Test
	void transactionUpdate_shouldAllowExpenseAmountAndHoldingCorrectionAndReapplyBalances() {
		UUID transactionId = UUID.randomUUID();
		Transaction transaction = Transaction.builder()
				.id(transactionId)
				.expenseTracker(tracker)
				.transactionType(TransactionType.EXPENSE)
				.status(TransactionStatus.COMPLETED)
				.holding(holdingA)
				.amount(100)
				.currencyCode("CZK")
				.feeAmount(0)
				.settledAmount(null)
				.transactionDate(Instant.now())
				.build();

		UpdateTransactionRequestDto request = new UpdateTransactionRequestDto(
				holdingB.getId(),
				150L,
				"CZK",
				null,
				0L,
				null,
				null,
				null,
				null,
				null
		);

		when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));
		when(holdingRepository.findById(holdingB.getId())).thenReturn(Optional.of(holdingB));
		when(assetRepository.findByCodeIgnoreCase("CZK"))
				.thenReturn(Optional.of(Asset.builder().code("CZK").scale(2).build()));
		when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));
		when(transactionMapper.toResponse(any(Transaction.class))).thenReturn(new TransactionResponseDto(
				transactionId,
				TransactionType.EXPENSE,
				TransactionStatus.COMPLETED,
				holdingB.getId(),
				"B",
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				150L,
				"CZK",
				2,
				null,
				0L,
				null,
				null,
				transaction.getTransactionDate(),
				null,
				null,
				null,
				java.util.List.of(),
				null,
				null
		));

		service.transactionUpdate(user, trackerId, transactionId, request);

		assertThat(transaction.getHolding().getId()).isEqualTo(holdingB.getId());
		assertThat(transaction.getAmount()).isEqualTo(150L);
		assertThat(transaction.getCurrencyCode()).isEqualTo("CZK");
		assertThat(holdingA.getCurrentAmount()).isEqualTo(10_100L);
		assertThat(holdingB.getCurrentAmount()).isEqualTo(19_850L);
		verify(holdingRepository).save(holdingA);
		verify(holdingRepository).save(holdingB);
	}

	@Test
	void transactionUpdate_shouldRejectFinancialPatchForTransfer() {
		UUID transactionId = UUID.randomUUID();
		Transaction transaction = Transaction.builder()
				.id(transactionId)
				.expenseTracker(tracker)
				.transactionType(TransactionType.TRANSFER)
				.status(TransactionStatus.COMPLETED)
				.sourceHolding(holdingA)
				.targetHolding(holdingB)
				.amount(100)
				.currencyCode("CZK")
				.transactionDate(Instant.now())
				.build();

		UpdateTransactionRequestDto request = new UpdateTransactionRequestDto(
				holdingA.getId(),
				200L,
				"CZK",
				null,
				0L,
				null,
				null,
				null,
				null,
				null
		);

		when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));

		assertThatThrownBy(() -> service.transactionUpdate(user, trackerId, transactionId, request))
				.isInstanceOf(OperationNotPermittedException.class)
				.hasMessageContaining("Financial fields");

		verify(transactionRepository, never()).save(any(Transaction.class));
	}
}