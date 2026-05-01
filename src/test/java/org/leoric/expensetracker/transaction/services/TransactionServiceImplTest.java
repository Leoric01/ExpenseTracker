package org.leoric.expensetracker.transaction.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.leoric.expensetracker.account.models.Account;
import org.leoric.expensetracker.asset.models.Asset;
import org.leoric.expensetracker.asset.repositories.AssetRepository;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.category.repositories.CategoryRepository;
import org.leoric.expensetracker.exchangerate.services.interfaces.ExchangeRateService;
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.leoric.expensetracker.expensetracker.repositories.ExpenseTrackerRepository;
import org.leoric.expensetracker.handler.exceptions.OperationNotPermittedException;
import org.leoric.expensetracker.holding.models.Holding;
import org.leoric.expensetracker.holding.repositories.HoldingRepository;
import org.leoric.expensetracker.institution.models.Institution;
import org.leoric.expensetracker.transaction.dto.TransactionAmountRateMode;
import org.leoric.expensetracker.transaction.dto.TransactionFilter;
import org.leoric.expensetracker.transaction.dto.TransactionPageResponseDto;
import org.leoric.expensetracker.transaction.dto.TransactionResponseDto;
import org.leoric.expensetracker.transaction.dto.UpdateTransactionRequestDto;
import org.leoric.expensetracker.transaction.mapstruct.TransactionMapper;
import org.leoric.expensetracker.transaction.models.Transaction;
import org.leoric.expensetracker.transaction.models.constants.TransactionStatus;
import org.leoric.expensetracker.transaction.models.constants.TransactionType;
import org.leoric.expensetracker.transaction.repositories.TransactionRepository;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
	private CategoryRepository categoryRepository;
	@Mock
	private ExpenseTrackerRepository expenseTrackerRepository;
	@Mock
	private ExchangeRateService exchangeRateService;
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

	@Test
	void transactionFindAllPageable_shouldSortByConvertedAmountWhenAmountSortRequested() {
		Asset czk = Asset.builder().code("CZK").scale(2).build();
		Asset btc = Asset.builder().code("BTC").scale(8).build();
		Asset eur = Asset.builder().code("EUR").scale(2).build();

		tracker.setPreferredDisplayAsset(czk);

		Transaction btcTx = Transaction.builder()
				.id(UUID.randomUUID())
				.expenseTracker(tracker)
				.transactionType(TransactionType.EXPENSE)
				.status(TransactionStatus.COMPLETED)
				.amount(100L)
				.currencyCode("BTC")
				.transactionDate(Instant.parse("2026-04-20T10:00:00Z"))
				.build();

		Transaction eurTx = Transaction.builder()
				.id(UUID.randomUUID())
				.expenseTracker(tracker)
				.transactionType(TransactionType.EXPENSE)
				.status(TransactionStatus.COMPLETED)
				.amount(200L)
				.currencyCode("EUR")
				.transactionDate(Instant.parse("2026-04-21T10:00:00Z"))
				.build();

		Transaction czkTx = Transaction.builder()
				.id(UUID.randomUUID())
				.expenseTracker(tracker)
				.transactionType(TransactionType.EXPENSE)
				.status(TransactionStatus.COMPLETED)
				.amount(300L)
				.currencyCode("CZK")
				.transactionDate(Instant.parse("2026-04-22T10:00:00Z"))
				.build();

		when(categoryRepository.findByExpenseTrackerIdAndActiveTrue(trackerId)).thenReturn(List.of());
		when(transactionRepository.findAll(any(Specification.class))).thenReturn(List.of(btcTx, eurTx, czkTx));
		when(expenseTrackerRepository.findById(trackerId)).thenReturn(Optional.of(tracker));
		when(assetRepository.findAllByCodeUpperIn(Set.of("BTC", "EUR", "CZK"))).thenReturn(Set.of(btc, eur, czk));
		when(exchangeRateService.convertAmount(eq(100L), eq(btc), eq(czk), any(Instant.class))).thenReturn(5_000L);
		when(exchangeRateService.convertAmount(eq(200L), eq(eur), eq(czk), any(Instant.class))).thenReturn(1_000L);

		when(transactionMapper.toResponse(btcTx)).thenReturn(minimalResponse(btcTx));
		when(transactionMapper.toResponse(eurTx)).thenReturn(minimalResponse(eurTx));
		when(transactionMapper.toResponse(czkTx)).thenReturn(minimalResponse(czkTx));

		TransactionPageResponseDto result = service.transactionFindAllPageable(
				user,
				trackerId,
				new TransactionFilter(null, null, null, null, null, null, null, TransactionAmountRateMode.NOW),
				PageRequest.of(0, 100, Sort.by(Sort.Order.desc("amount")))
		);

		assertThat(result.content()).hasSize(3);
		assertThat(result.content().get(0).id()).isEqualTo(btcTx.getId());
		assertThat(result.content().get(0).convertedAmount()).isEqualTo(5_000L);
		assertThat(result.content().get(0).convertedInto()).isEqualTo("CZK");
		assertThat(result.content().get(1).id()).isEqualTo(eurTx.getId());
		assertThat(result.content().get(1).convertedAmount()).isEqualTo(1_000L);
		assertThat(result.content().get(1).convertedInto()).isEqualTo("CZK");
		assertThat(result.content().get(2).id()).isEqualTo(czkTx.getId());
		assertThat(result.content().get(2).convertedAmount()).isEqualTo(300L);
		assertThat(result.content().get(2).convertedInto()).isEqualTo("CZK");
	}

	@Test
	void transactionFindAllPageable_shouldKeepConvertedAmountNullWhenNotSortingByAmount() {
		Transaction tx = Transaction.builder()
				.id(UUID.randomUUID())
				.expenseTracker(tracker)
				.transactionType(TransactionType.EXPENSE)
				.status(TransactionStatus.COMPLETED)
				.amount(100L)
				.currencyCode("CZK")
				.transactionDate(Instant.parse("2026-04-22T10:00:00Z"))
				.build();

		Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Order.desc("transactionDate")));

		when(categoryRepository.findByExpenseTrackerIdAndActiveTrue(trackerId)).thenReturn(List.of());
		when(transactionRepository.findAll(any(Specification.class), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(tx), pageable, 1));
		when(transactionRepository.findAll(any(Specification.class))).thenReturn(List.of(tx));
		when(transactionMapper.toResponse(tx)).thenReturn(minimalResponse(tx));

		TransactionPageResponseDto result = service.transactionFindAllPageable(
				user,
				trackerId,
				new TransactionFilter(null, null, null, null, null, null, null, TransactionAmountRateMode.NOW),
				pageable
		);

		assertThat(result.content()).hasSize(1);
		assertThat(result.content().get(0).convertedAmount()).isNull();
		assertThat(result.content().get(0).convertedInto()).isNull();
		verifyNoInteractions(exchangeRateService);
	}

	private TransactionResponseDto minimalResponse(Transaction transaction) {
		return new TransactionResponseDto(
				transaction.getId(),
				transaction.getTransactionType(),
				transaction.getStatus(),
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				transaction.getAmount(),
				transaction.getCurrencyCode(),
				2,
				null,
				0L,
				null,
				null,
				transaction.getTransactionDate(),
				null,
				null,
				null,
				List.of(),
				null,
				null
		);
	}
}