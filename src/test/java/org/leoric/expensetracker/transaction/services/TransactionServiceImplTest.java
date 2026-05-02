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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

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
				null,
				null,
				150L,
				"CZK",
				null,
				0L,
				null,
				null,
				null,
				null,
				null,
				null
		);

		when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));
		when(holdingRepository.findById(holdingB.getId())).thenReturn(Optional.of(holdingB));
		when(assetRepository.existsByCodeIgnoreCase("CZK")).thenReturn(true);
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
	void transactionUpdate_shouldAllowAmountPatchForBalanceAdjustmentAndReapplyHoldingBalance() {
		UUID transactionId = UUID.randomUUID();
		Transaction transaction = Transaction.builder()
				.id(transactionId)
				.expenseTracker(tracker)
				.transactionType(TransactionType.BALANCE_ADJUSTMENT)
				.status(TransactionStatus.COMPLETED)
				.holding(holdingA)
				.balanceAdjustmentDirection(org.leoric.expensetracker.transaction.models.constants.BalanceAdjustmentDirection.ADDITION)
				.amount(100)
				.currencyCode("CZK")
				.feeAmount(0)
				.transactionDate(Instant.now())
				.build();

		UpdateTransactionRequestDto request = new UpdateTransactionRequestDto(
				holdingA.getId(),
				null,
				null,
				200L,
				"CZK",
				null,
				0L,
				null,
				null,
				null,
				null,
				null,
				null
		);

		when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));

		when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));
		when(transactionMapper.toResponse(any(Transaction.class))).thenReturn(minimalResponse(transaction));

		service.transactionUpdate(user, trackerId, transactionId, request);

		assertThat(transaction.getAmount()).isEqualTo(200L);
		assertThat(holdingA.getCurrentAmount()).isEqualTo(10_100L);
		verify(holdingRepository).save(holdingA);
	}

	@Test
	void transactionUpdate_shouldRejectHoldingChangeForBalanceAdjustment() {
		UUID transactionId = UUID.randomUUID();
		Transaction transaction = Transaction.builder()
				.id(transactionId)
				.expenseTracker(tracker)
				.transactionType(TransactionType.BALANCE_ADJUSTMENT)
				.status(TransactionStatus.COMPLETED)
				.holding(holdingA)
				.balanceAdjustmentDirection(org.leoric.expensetracker.transaction.models.constants.BalanceAdjustmentDirection.ADDITION)
				.amount(100)
				.currencyCode("CZK")
				.feeAmount(0)
				.transactionDate(Instant.now())
				.build();

		UpdateTransactionRequestDto request = new UpdateTransactionRequestDto(
				holdingB.getId(),
				null,
				null,
				200L,
				"CZK",
				null,
				0L,
				null,
				null,
				null,
				null,
				null,
				null
		);

		when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));

		assertThatThrownBy(() -> service.transactionUpdate(user, trackerId, transactionId, request))
				.isInstanceOf(OperationNotPermittedException.class)
				.hasMessageContaining("Holding cannot be changed");
	}

	@Test
	void transactionUpdate_shouldAllowSameAssetTransferPatchWithAmountAndSettled() {
		UUID transactionId = UUID.randomUUID();
		Transaction transaction = Transaction.builder()
				.id(transactionId)
				.expenseTracker(tracker)
				.transactionType(TransactionType.TRANSFER)
				.status(TransactionStatus.COMPLETED)
				.sourceHolding(holdingA)
				.targetHolding(holdingB)
				.amount(100L)
				.settledAmount(98L)
				.feeAmount(2L)
				.currencyCode("CZK")
				.exchangeRate(null)
				.transactionDate(Instant.now())
				.build();

		UpdateTransactionRequestDto request = new UpdateTransactionRequestDto(
				null,
				holdingA.getId(),
				holdingB.getId(),
				120L,
				null,
				null,
				null,
				117L,
				null,
				null,
				null,
				null,
				null
		);

		when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));
		when(holdingRepository.findById(holdingA.getId())).thenReturn(Optional.of(holdingA));
		when(holdingRepository.findById(holdingB.getId())).thenReturn(Optional.of(holdingB));
		when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));
		when(transactionMapper.toResponse(any(Transaction.class))).thenReturn(minimalResponse(transaction));

		service.transactionUpdate(user, trackerId, transactionId, request);

		assertThat(transaction.getAmount()).isEqualTo(120L);
		assertThat(transaction.getSettledAmount()).isEqualTo(117L);
		assertThat(transaction.getFeeAmount()).isEqualTo(3L);
		assertThat(transaction.getExchangeRate()).isNull();
		assertThat(holdingA.getCurrentAmount()).isEqualTo(9_980L);
		assertThat(holdingB.getCurrentAmount()).isEqualTo(20_019L);
	}

	@Test
	void transactionUpdate_shouldComputeSettledFromAmountAndFeeWhenSettledMissingOnTransfer() {
		UUID transactionId = UUID.randomUUID();
		Transaction transaction = Transaction.builder()
				.id(transactionId)
				.expenseTracker(tracker)
				.transactionType(TransactionType.TRANSFER)
				.status(TransactionStatus.COMPLETED)
				.sourceHolding(holdingA)
				.targetHolding(holdingB)
				.amount(100L)
				.settledAmount(100L)
				.feeAmount(0L)
				.currencyCode("CZK")
				.exchangeRate(null)
				.transactionDate(Instant.now())
				.build();

		UpdateTransactionRequestDto request = new UpdateTransactionRequestDto(
				null,
				null,
				null,
				130L,
				null,
				null,
				5L,
				null,
				null,
				null,
				null,
				null,
				null
		);

		when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));
		when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));
		when(transactionMapper.toResponse(any(Transaction.class))).thenReturn(minimalResponse(transaction));

		service.transactionUpdate(user, trackerId, transactionId, request);

		assertThat(transaction.getAmount()).isEqualTo(130L);
		assertThat(transaction.getSettledAmount()).isEqualTo(125L);
		assertThat(transaction.getFeeAmount()).isEqualTo(5L);
		assertThat(holdingA.getCurrentAmount()).isEqualTo(9_970L);
		assertThat(holdingB.getCurrentAmount()).isEqualTo(20_025L);
	}

	@Test
	void transactionUpdate_shouldAllowCrossAssetTransferPatchAndReapplyBalances() {
		Holding eurHolding = Holding.builder()
				.id(UUID.randomUUID())
				.account(holdingB.getAccount())
				.asset(org.leoric.expensetracker.asset.models.Asset.builder().code("EUR").build())
				.currentAmount(5_000L)
				.active(true)
				.build();

		UUID transactionId = UUID.randomUUID();
		Transaction transaction = Transaction.builder()
				.id(transactionId)
				.expenseTracker(tracker)
				.transactionType(TransactionType.TRANSFER)
				.status(TransactionStatus.COMPLETED)
				.sourceHolding(holdingA)
				.targetHolding(eurHolding)
				.amount(100L)
				.settledAmount(50L)
				.feeAmount(2L)
				.currencyCode("CZK")
				.exchangeRate(new java.math.BigDecimal("0.50000000"))
				.transactionDate(Instant.now())
				.build();

		UpdateTransactionRequestDto request = new UpdateTransactionRequestDto(
				null,
				null,
				null,
				120L,
				null,
				new java.math.BigDecimal("0.58000000"),
				3L,
				70L,
				null,
				null,
				null,
				null,
				null
		);

		when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));
		when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));
		when(transactionMapper.toResponse(any(Transaction.class))).thenReturn(minimalResponse(transaction));

		service.transactionUpdate(user, trackerId, transactionId, request);

		assertThat(transaction.getAmount()).isEqualTo(120L);
		assertThat(transaction.getFeeAmount()).isEqualTo(3L);
		assertThat(transaction.getSettledAmount()).isEqualTo(70L);
		assertThat(transaction.getExchangeRate()).isEqualByComparingTo("0.58000000");
		assertThat(holdingA.getCurrentAmount()).isEqualTo(9_979L);
		assertThat(eurHolding.getCurrentAmount()).isEqualTo(5_020L);
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
		when(transactionRepository.findAll(anyTransactionSpec())).thenReturn(List.of(btcTx, eurTx, czkTx));
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
		assertThat(result.content().getFirst().id()).isEqualTo(btcTx.getId());
		assertThat(result.content().getFirst().convertedAmount()).isEqualTo(5_000L);
		assertThat(result.content().get(0).convertedInto()).isEqualTo("CZK");
		assertThat(result.content().get(0).convertedAssetScale()).isEqualTo(2);
		assertThat(result.content().get(1).id()).isEqualTo(eurTx.getId());
		assertThat(result.content().get(1).convertedAmount()).isEqualTo(1_000L);
		assertThat(result.content().get(1).convertedInto()).isEqualTo("CZK");
		assertThat(result.content().get(1).convertedAssetScale()).isEqualTo(2);
		assertThat(result.content().get(2).id()).isEqualTo(czkTx.getId());
		assertThat(result.content().get(2).convertedAmount()).isEqualTo(300L);
		assertThat(result.content().get(2).convertedInto()).isEqualTo("CZK");
		assertThat(result.content().get(2).convertedAssetScale()).isEqualTo(2);
		assertThat(result.totals().byAsset()).hasSize(3);
		assertThat(result.totals().byAsset().get(0).assetScale()).isEqualTo(8);
		assertThat(result.totals().byAsset().get(1).assetScale()).isEqualTo(2);
		assertThat(result.totals().byAsset().get(2).assetScale()).isEqualTo(2);
		assertThat(result.totals().converted().expenseAmount()).isEqualTo(6_300L);
		assertThat(result.totals().converted().incomeAmount()).isEqualTo(0L);
		assertThat(result.totals().converted().netAmount()).isEqualTo(-6_300L);
		assertThat(result.totals().converted().convertedInto()).isEqualTo("CZK");
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
		when(expenseTrackerRepository.findById(trackerId)).thenReturn(Optional.of(tracker));
		when(transactionRepository.findAll(anyTransactionSpec(), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(tx), pageable, 1));
		when(transactionRepository.findAll(anyTransactionSpec())).thenReturn(List.of(tx));
		when(assetRepository.findAllByCodeUpperIn(Set.of("CZK"))).thenReturn(Set.of());
		when(transactionMapper.toResponse(tx)).thenReturn(minimalResponse(tx));

		TransactionPageResponseDto result = service.transactionFindAllPageable(
				user,
				trackerId,
				new TransactionFilter(null, null, null, null, null, null, null, TransactionAmountRateMode.NOW),
				pageable
		);

		assertThat(result.content()).hasSize(1);
		assertThat(result.content().getFirst().convertedAmount()).isNull();
		assertThat(result.content().getFirst().convertedInto()).isNull();
		assertThat(result.content().getFirst().convertedAssetScale()).isNull();
		assertThat(result.totals().byAsset()).hasSize(1);
		assertThat(result.totals().byAsset().getFirst().assetCode()).isEqualTo("CZK");
		assertThat(result.totals().byAsset().getFirst().assetScale()).isNull();
		assertThat(result.totals().byAsset().getFirst().expenseAmount()).isEqualTo(100L);
		assertThat(result.totals().converted().convertedInto()).isNull();
		assertThat(result.totals().converted().incomeAmount()).isNull();
		verifyNoInteractions(exchangeRateService);
	}

	@Test
	void transactionFindAllPageable_shouldFillConvertedFieldsWhenDisplayAssetKnownEvenWithoutAmountSort() {
		Asset czk = Asset.builder().code("CZK").scale(2).build();
		tracker.setPreferredDisplayAsset(czk);

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
		when(expenseTrackerRepository.findById(trackerId)).thenReturn(Optional.of(tracker));
		when(transactionRepository.findAll(anyTransactionSpec(), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(tx), pageable, 1));
		when(transactionRepository.findAll(anyTransactionSpec())).thenReturn(List.of(tx));
		when(assetRepository.findAllByCodeUpperIn(Set.of("CZK"))).thenReturn(Set.of(czk));
		when(transactionMapper.toResponse(tx)).thenReturn(minimalResponse(tx));

		TransactionPageResponseDto result = service.transactionFindAllPageable(
				user,
				trackerId,
				new TransactionFilter(null, null, null, null, null, null, null, TransactionAmountRateMode.NOW),
				pageable
		);

		assertThat(result.content()).hasSize(1);
		assertThat(result.content().getFirst().convertedAmount()).isEqualTo(100L);
		assertThat(result.content().getFirst().convertedSourceAmount()).isNull();
		assertThat(result.content().getFirst().convertedTargetAmount()).isNull();
		assertThat(result.content().getFirst().convertedInto()).isEqualTo("CZK");
		assertThat(result.content().getFirst().convertedAssetScale()).isEqualTo(2);
		verifyNoInteractions(exchangeRateService);
	}

	@Test
	void transactionFindAllPageable_shouldNormalizeTransferExchangeRateToTargetAssetScale() {
		Asset czk = Asset.builder().code("CZK").scale(2).build();
		tracker.setPreferredDisplayAsset(czk);

		Holding btcHolding = Holding.builder()
				.id(UUID.randomUUID())
				.account(holdingA.getAccount())
				.asset(Asset.builder().code("BTC").build())
				.currentAmount(10_000L)
				.active(true)
				.build();

		Transaction transfer = Transaction.builder()
				.id(UUID.randomUUID())
				.expenseTracker(tracker)
				.transactionType(TransactionType.TRANSFER)
				.status(TransactionStatus.COMPLETED)
				.sourceHolding(btcHolding)
				.targetHolding(holdingB)
				.amount(10_000_000L)
				.settledAmount(160_000_600L)
				.feeAmount(1_000L)
				.currencyCode("BTC")
				.exchangeRate(new java.math.BigDecimal("1600160.02000000"))
				.transactionDate(Instant.parse("2026-05-01T12:36:00Z"))
				.build();

		Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Order.desc("transactionDate")));

		when(categoryRepository.findByExpenseTrackerIdAndActiveTrue(trackerId)).thenReturn(List.of());
		when(expenseTrackerRepository.findById(trackerId)).thenReturn(Optional.of(tracker));
		when(transactionRepository.findAll(anyTransactionSpec(), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(transfer), pageable, 1));
		when(transactionRepository.findAll(anyTransactionSpec())).thenReturn(List.of(transfer));
		when(assetRepository.findAllByCodeUpperIn(Set.of("BTC", "CZK"))).thenReturn(Set.of(czk));

		when(transactionMapper.toResponse(transfer)).thenReturn(new TransactionResponseDto(
				transfer.getId(),
				transfer.getTransactionType(),
				transfer.getStatus(),
				null,
				null,
				btcHolding.getId(),
				"A",
				"BTC",
				8,
				holdingB.getId(),
				"B",
				"CZK",
				2,
				null,
				null,
				null,
				null,
				transfer.getAmount(),
				"BTC",
				8,
				new java.math.BigDecimal("1600160.02000000"),
				transfer.getFeeAmount(),
				transfer.getSettledAmount(),
				null,
				transfer.getTransactionDate(),
				null,
				null,
				null,
				List.of(),
				null,
				null
		));

		TransactionPageResponseDto result = service.transactionFindAllPageable(
				user,
				trackerId,
				new TransactionFilter(null, null, null, null, null, null, null, TransactionAmountRateMode.NOW),
				pageable
		);

		assertThat(result.content()).hasSize(1);
		assertThat(result.content().getFirst().exchangeRate()).isEqualByComparingTo("1600160.02");
		assertThat(result.content().getFirst().exchangeRate().scale()).isEqualTo(2);
	}

	@Test
	void transactionFindAllPageable_shouldSplitCrossAssetTransferIntoSourceExpenseAndTargetIncomeOnTrackerTotals() {
		Asset czk = Asset.builder().code("CZK").scale(2).build();
		Asset eur = Asset.builder().code("EUR").scale(2).build();
		tracker.setPreferredDisplayAsset(czk);

		Holding eurHolding = Holding.builder()
				.id(UUID.randomUUID())
				.account(holdingB.getAccount())
				.asset(Asset.builder().code("EUR").build())
				.currentAmount(20_000)
				.active(true)
				.build();

		Transaction transfer = Transaction.builder()
				.id(UUID.randomUUID())
				.expenseTracker(tracker)
				.transactionType(TransactionType.TRANSFER)
				.status(TransactionStatus.COMPLETED)
				.sourceHolding(holdingA)
				.targetHolding(eurHolding)
				.amount(100L)
				.settledAmount(4L)
				.feeAmount(0L)
				.currencyCode("CZK")
				.transactionDate(Instant.parse("2026-04-22T10:00:00Z"))
				.build();

		Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Order.desc("transactionDate")));

		when(categoryRepository.findByExpenseTrackerIdAndActiveTrue(trackerId)).thenReturn(List.of());
		when(expenseTrackerRepository.findById(trackerId)).thenReturn(Optional.of(tracker));
		when(transactionRepository.findAll(anyTransactionSpec(), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(transfer), pageable, 1));
		when(transactionRepository.findAll(anyTransactionSpec())).thenReturn(List.of(transfer));
		when(assetRepository.findAllByCodeUpperIn(Set.of("CZK", "EUR"))).thenReturn(Set.of(czk, eur));
		when(exchangeRateService.convertAmount(eq(4L), eq(eur), eq(czk), any(Instant.class))).thenReturn(120L);
		when(transactionMapper.toResponse(transfer)).thenReturn(minimalResponse(transfer));

		TransactionPageResponseDto result = service.transactionFindAllPageable(
				user,
				trackerId,
				new TransactionFilter(null, null, null, null, null, null, null, TransactionAmountRateMode.NOW),
				pageable
		);

		assertThat(result.totals().byAsset()).hasSize(2);
		assertThat(result.totals().byAsset().getFirst().assetCode()).isEqualTo("CZK");
		assertThat(result.totals().byAsset().get(0).incomeAmount()).isEqualTo(0L);
		assertThat(result.totals().byAsset().get(0).expenseAmount()).isEqualTo(100L);
		assertThat(result.totals().byAsset().get(1).assetCode()).isEqualTo("EUR");
		assertThat(result.totals().byAsset().get(1).incomeAmount()).isEqualTo(4L);
		assertThat(result.totals().converted().incomeAmount()).isEqualTo(120L);
		assertThat(result.totals().converted().expenseAmount()).isEqualTo(100L);

		assertThat(result.content()).hasSize(1);
		assertThat(result.content().getFirst().convertedAmount()).isEqualTo(100L);
		assertThat(result.content().getFirst().convertedSourceAmount()).isEqualTo(100L);
		assertThat(result.content().getFirst().convertedTargetAmount()).isEqualTo(120L);
	}

	@Test
	void transactionFindAllPageable_shouldCountOnlyFeeEffectForInternalSameAssetTransferOnTrackerTotals() {
		Asset czk = Asset.builder().code("CZK").scale(2).build();

		Transaction transfer = Transaction.builder()
				.id(UUID.randomUUID())
				.expenseTracker(tracker)
				.transactionType(TransactionType.TRANSFER)
				.status(TransactionStatus.COMPLETED)
				.sourceHolding(holdingA)
				.targetHolding(holdingB)
				.amount(100L)
				.settledAmount(98L)
				.feeAmount(40L)
				.currencyCode("CZK")
				.exchangeRate(java.math.BigDecimal.ONE)
				.transactionDate(Instant.parse("2026-04-22T10:00:00Z"))
				.build();

		Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Order.desc("transactionDate")));

		when(categoryRepository.findByExpenseTrackerIdAndActiveTrue(trackerId)).thenReturn(List.of());
		when(expenseTrackerRepository.findById(trackerId)).thenReturn(Optional.of(tracker));
		when(transactionRepository.findAll(anyTransactionSpec(), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(transfer), pageable, 1));
		when(transactionRepository.findAll(anyTransactionSpec())).thenReturn(List.of(transfer));
		when(assetRepository.findAllByCodeUpperIn(Set.of("CZK"))).thenReturn(Set.of(czk));
		when(transactionMapper.toResponse(transfer)).thenReturn(minimalResponse(transfer));

		TransactionPageResponseDto result = service.transactionFindAllPageable(
				user,
				trackerId,
				new TransactionFilter(null, null, null, null, null, null, null, TransactionAmountRateMode.NOW),
				pageable
		);

		assertThat(result.totals().byAsset()).hasSize(1);
		assertThat(result.totals().byAsset().getFirst().assetCode()).isEqualTo("CZK");
		assertThat(result.totals().byAsset().getFirst().incomeAmount()).isEqualTo(0L);
		assertThat(result.totals().byAsset().getFirst().expenseAmount()).isEqualTo(2L);
		assertThat(result.totals().byAsset().getFirst().netAmount()).isEqualTo(-2L);
	}

	@Test
	void transactionFindAllPageable_shouldUseSettledAmountForIncomingHoldingOnInternalSameAssetTransfer() {
		Asset czk = Asset.builder().code("CZK").scale(2).build();

		Transaction transfer = Transaction.builder()
				.id(UUID.randomUUID())
				.expenseTracker(tracker)
				.transactionType(TransactionType.TRANSFER)
				.status(TransactionStatus.COMPLETED)
				.sourceHolding(holdingA)
				.targetHolding(holdingB)
				.amount(100L)
				.settledAmount(98L)
				.feeAmount(40L)
				.currencyCode("CZK")
				.exchangeRate(java.math.BigDecimal.ONE)
				.transactionDate(Instant.parse("2026-04-22T10:00:00Z"))
				.build();

		Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Order.desc("transactionDate")));

		when(categoryRepository.findByExpenseTrackerIdAndActiveTrue(trackerId)).thenReturn(List.of());
		when(expenseTrackerRepository.findById(trackerId)).thenReturn(Optional.of(tracker));
		when(transactionRepository.findAll(anyTransactionSpec(), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(transfer), pageable, 1));
		when(transactionRepository.findAll(anyTransactionSpec())).thenReturn(List.of(transfer));
		when(assetRepository.findAllByCodeUpperIn(Set.of("CZK"))).thenReturn(Set.of(czk));
		when(transactionMapper.toResponse(transfer)).thenReturn(minimalResponse(transfer));

		TransactionPageResponseDto result = service.transactionFindAllPageable(
				user,
				trackerId,
				new TransactionFilter(null, null, holdingB.getId(), null, null, null, null, TransactionAmountRateMode.NOW),
				pageable
		);

		assertThat(result.totals().byAsset()).hasSize(1);
		assertThat(result.totals().byAsset().getFirst().assetCode()).isEqualTo("CZK");
		assertThat(result.totals().byAsset().getFirst().incomeAmount()).isEqualTo(98L);
		assertThat(result.totals().byAsset().getFirst().expenseAmount()).isEqualTo(0L);
		assertThat(result.totals().byAsset().getFirst().netAmount()).isEqualTo(98L);
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

	@SuppressWarnings("unchecked")
	private Specification<Transaction> anyTransactionSpec() {
		return any(Specification.class);
	}
}