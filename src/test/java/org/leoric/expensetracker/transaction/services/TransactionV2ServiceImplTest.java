package org.leoric.expensetracker.transaction.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.leoric.expensetracker.account.models.Account;
import org.leoric.expensetracker.asset.models.Asset;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.exchangerate.services.interfaces.ExchangeRateService;
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.leoric.expensetracker.expensetracker.repositories.ExpenseTrackerRepository;
import org.leoric.expensetracker.handler.exceptions.AssetExchangeSameAssetException;
import org.leoric.expensetracker.handler.exceptions.AssetExchangeAmountLessThanFeeException;
import org.leoric.expensetracker.handler.exceptions.AssetExchangeSettledAmountRequiredException;
import org.leoric.expensetracker.handler.exceptions.OperationNotPermittedException;
import org.leoric.expensetracker.handler.exceptions.TransferAmountInputMissingException;
import org.leoric.expensetracker.holding.models.Holding;
import org.leoric.expensetracker.holding.repositories.HoldingRepository;
import org.leoric.expensetracker.institution.models.Institution;
import org.leoric.expensetracker.transaction.dto.AssetExchangeRateQuoteRequestDto;
import org.leoric.expensetracker.transaction.dto.AssetExchangeRateQuoteResponseDto;
import org.leoric.expensetracker.transaction.dto.CreateAssetExchangeV2RequestDto;
import org.leoric.expensetracker.transaction.dto.CreateAssetExchangeV2ResponseDto;
import org.leoric.expensetracker.transaction.dto.CreateWalletTransferV2RequestDto;
import org.leoric.expensetracker.transaction.dto.CreateWalletTransferV2ResponseDto;
import org.leoric.expensetracker.transaction.dto.TransferAmountCalculationMode;
import org.leoric.expensetracker.transaction.models.Transaction;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionV2ServiceImplTest {

	@Mock
	private TransactionRepository transactionRepository;
	@Mock
	private ExpenseTrackerRepository expenseTrackerRepository;
	@Mock
	private HoldingRepository holdingRepository;
	@Mock
	private ExchangeRateService exchangeRateService;

	@InjectMocks
	private TransactionV2ServiceImpl service;

	private User user;
	private UUID trackerId;
	private Holding source;
	private Holding target;
	private ExpenseTracker tracker;

	@BeforeEach
	void setUp() {
		trackerId = UUID.randomUUID();
		user = User.builder().id(UUID.randomUUID()).email("test@example.com").build();
		tracker = ExpenseTracker.builder().id(trackerId).name("Tracker").build();

		Institution institution = Institution.builder().expenseTracker(tracker).build();
		Account sourceAccount = Account.builder().name("Source").institution(institution).build();
		Account targetAccount = Account.builder().name("Target").institution(institution).build();

		source = Holding.builder()
				.id(UUID.randomUUID())
				.account(sourceAccount)
				.asset(Asset.builder().code("CZK").build())
				.currentAmount(1_000)
				.active(true)
				.build();

		target = Holding.builder()
				.id(UUID.randomUUID())
				.account(targetAccount)
				.asset(Asset.builder().code("CZK").build())
				.currentAmount(200)
				.active(true)
				.build();
	}

	@Test
	void createWalletTransfer_shouldDefaultSettledAndFeeWhenOnlyAmountProvided() {
		CreateWalletTransferV2RequestDto request = new CreateWalletTransferV2RequestDto(
				source.getId(),
				target.getId(),
				100L,
				null,
				Instant.parse("2026-05-01T10:00:00Z"),
				"Transfer",
				null,
				null
		);

		when(expenseTrackerRepository.findById(trackerId)).thenReturn(Optional.of(tracker));
		when(holdingRepository.findById(source.getId())).thenReturn(Optional.of(source));
		when(holdingRepository.findById(target.getId())).thenReturn(Optional.of(target));
		when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
			Transaction tx = invocation.getArgument(0);
			tx.setId(UUID.randomUUID());
			return tx;
		});

		CreateWalletTransferV2ResponseDto response = service.createWalletTransfer(user, trackerId, request);

		assertThat(response.amount()).isEqualTo(100L);
		assertThat(response.settledAmount()).isEqualTo(100L);
		assertThat(response.feeAmount()).isEqualTo(0L);
		assertThat(response.calculationMode()).isEqualTo(TransferAmountCalculationMode.AMOUNT_ONLY_DEFAULTED);
		assertThat(source.getCurrentAmount()).isEqualTo(900L);
		assertThat(target.getCurrentAmount()).isEqualTo(300L);
	}

	@Test
	void createWalletTransfer_shouldFailWhenAmountAndSettledMissing() {
		CreateWalletTransferV2RequestDto request = new CreateWalletTransferV2RequestDto(
				source.getId(),
				target.getId(),
				null,
				null,
				null,
				null,
				null,
				null
		);

		assertThatThrownBy(() -> service.createWalletTransfer(user, trackerId, request))
				.isInstanceOf(TransferAmountInputMissingException.class);
	}

	@Test
	void createWalletTransfer_shouldDefaultAmountAndFeeWhenOnlySettledProvided() {
		CreateWalletTransferV2RequestDto request = new CreateWalletTransferV2RequestDto(
				source.getId(),
				target.getId(),
				null,
				95L,
				Instant.parse("2026-05-01T10:00:00Z"),
				"Settled only transfer",
				null,
				null
		);

		when(expenseTrackerRepository.findById(trackerId)).thenReturn(Optional.of(tracker));
		when(holdingRepository.findById(source.getId())).thenReturn(Optional.of(source));
		when(holdingRepository.findById(target.getId())).thenReturn(Optional.of(target));
		when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
			Transaction tx = invocation.getArgument(0);
			tx.setId(UUID.randomUUID());
			return tx;
		});

		CreateWalletTransferV2ResponseDto response = service.createWalletTransfer(user, trackerId, request);

		assertThat(response.amount()).isEqualTo(95L);
		assertThat(response.settledAmount()).isEqualTo(95L);
		assertThat(response.feeAmount()).isEqualTo(0L);
		assertThat(response.calculationMode()).isEqualTo(TransferAmountCalculationMode.SETTLED_ONLY_DEFAULTED);
		assertThat(source.getCurrentAmount()).isEqualTo(905L);
		assertThat(target.getCurrentAmount()).isEqualTo(295L);
	}

	@Test
	void createWalletTransfer_shouldDeductOnlyAmountAndComputeFeeFromAmountAndSettled() {
		CreateWalletTransferV2RequestDto request = new CreateWalletTransferV2RequestDto(
				source.getId(),
				target.getId(),
				100L,
				98L,
				Instant.parse("2026-05-01T10:00:00Z"),
				"Transfer with fee",
				null,
				null
		);

		when(expenseTrackerRepository.findById(trackerId)).thenReturn(Optional.of(tracker));
		when(holdingRepository.findById(source.getId())).thenReturn(Optional.of(source));
		when(holdingRepository.findById(target.getId())).thenReturn(Optional.of(target));
		when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
			Transaction tx = invocation.getArgument(0);
			tx.setId(UUID.randomUUID());
			return tx;
		});

		CreateWalletTransferV2ResponseDto response = service.createWalletTransfer(user, trackerId, request);

		assertThat(response.amount()).isEqualTo(100L);
		assertThat(response.settledAmount()).isEqualTo(98L);
		assertThat(response.feeAmount()).isEqualTo(2L);
		assertThat(response.sourceDeduction()).isEqualTo(100L);
		assertThat(response.targetAddition()).isEqualTo(98L);
		assertThat(source.getCurrentAmount()).isEqualTo(900L);
		assertThat(target.getCurrentAmount()).isEqualTo(298L);
	}

	@Test
	void createAssetExchange_shouldFailWhenAssetsAreSame() {
		CreateAssetExchangeV2RequestDto request = new CreateAssetExchangeV2RequestDto(
				source.getId(),
				target.getId(),
				100L,
				2L,
				98L,
				new java.math.BigDecimal("1.25"),
				Instant.now(),
				null,
				null,
				null
		);

		when(expenseTrackerRepository.findById(trackerId)).thenReturn(Optional.of(tracker));
		when(holdingRepository.findById(source.getId())).thenReturn(Optional.of(source));
		when(holdingRepository.findById(target.getId())).thenReturn(Optional.of(target));

		assertThatThrownBy(() -> service.createAssetExchange(user, trackerId, request))
				.isInstanceOf(AssetExchangeSameAssetException.class);
	}

	@Test
	void createAssetExchange_shouldComputeSettledFromAmountFeeAndExchangeRate() {
		source.setAsset(Asset.builder().code("CZK").scale(2).build());
		target.setAsset(Asset.builder().code("EUR").scale(2).build());

		CreateAssetExchangeV2RequestDto request = new CreateAssetExchangeV2RequestDto(
				source.getId(),
				target.getId(),
				100L,
				5L,
				null,
				new java.math.BigDecimal("2.00"),
				Instant.parse("2026-05-01T10:00:00Z"),
				"exchange",
				null,
				null
		);

		when(expenseTrackerRepository.findById(trackerId)).thenReturn(Optional.of(tracker));
		when(holdingRepository.findById(source.getId())).thenReturn(Optional.of(source));
		when(holdingRepository.findById(target.getId())).thenReturn(Optional.of(target));
		when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
			Transaction tx = invocation.getArgument(0);
			tx.setId(UUID.randomUUID());
			return tx;
		});

		CreateAssetExchangeV2ResponseDto response = service.createAssetExchange(user, trackerId, request);

		assertThat(response.amount()).isEqualTo(100L);
		assertThat(response.feeAmount()).isEqualTo(5L);
		assertThat(response.settledAmount()).isEqualTo(190L);
		assertThat(response.sourceDeduction()).isEqualTo(100L);
		assertThat(response.targetAddition()).isEqualTo(190L);
		assertThat(response.sourceHoldingName()).isEqualTo("Source");
		assertThat(response.targetHoldingName()).isEqualTo("Target");
		assertThat(response.assetCode()).isEqualTo("CZK");
		assertThat(response.sourceAssetScale()).isEqualTo(2);
		assertThat(response.targetAssetScale()).isEqualTo(2);
		assertThat(source.getCurrentAmount()).isEqualTo(900L);
		assertThat(target.getCurrentAmount()).isEqualTo(390L);
	}

	@Test
	void createAssetExchange_shouldDeriveExchangeRateWhenSettledProvidedWithoutRate() {
		source.setAsset(Asset.builder().code("BTC").scale(8).build());
		target.setAsset(Asset.builder().code("CZK").scale(2).build());

		CreateAssetExchangeV2RequestDto request = new CreateAssetExchangeV2RequestDto(
				source.getId(),
				target.getId(),
				1_000_000L,
				1_000L,
				1_000L,
				null,
				Instant.parse("2026-05-01T10:58:00Z"),
				"t1",
				null,
				null
		);

		when(expenseTrackerRepository.findById(trackerId)).thenReturn(Optional.of(tracker));
		when(holdingRepository.findById(source.getId())).thenReturn(Optional.of(source));
		when(holdingRepository.findById(target.getId())).thenReturn(Optional.of(target));
		when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
			Transaction tx = invocation.getArgument(0);
			tx.setId(UUID.randomUUID());
			return tx;
		});

		CreateAssetExchangeV2ResponseDto response = service.createAssetExchange(user, trackerId, request);

		assertThat(response.exchangeRate()).isNotNull();
		assertThat(response.exchangeRate()).isEqualByComparingTo("1001.00");
		assertThat(response.amount()).isEqualTo(1_000_000L);
		assertThat(response.feeAmount()).isEqualTo(1_000L);
		assertThat(response.settledAmount()).isEqualTo(1_000L);
	}

	@Test
	void createAssetExchange_shouldFailWhenSettledAndExchangeRateMissing() {
		target.setAsset(Asset.builder().code("EUR").build());

		CreateAssetExchangeV2RequestDto request = new CreateAssetExchangeV2RequestDto(
				source.getId(),
				target.getId(),
				100L,
				2L,
				null,
				null,
				Instant.now(),
				null,
				null,
				null
		);

		when(expenseTrackerRepository.findById(trackerId)).thenReturn(Optional.of(tracker));
		when(holdingRepository.findById(source.getId())).thenReturn(Optional.of(source));
		when(holdingRepository.findById(target.getId())).thenReturn(Optional.of(target));

		assertThatThrownBy(() -> service.createAssetExchange(user, trackerId, request))
				.isInstanceOf(AssetExchangeSettledAmountRequiredException.class);
	}

	@Test
	void createAssetExchange_shouldFailWhenAmountLessThanFee() {
		target.setAsset(Asset.builder().code("EUR").build());

		CreateAssetExchangeV2RequestDto request = new CreateAssetExchangeV2RequestDto(
				source.getId(),
				target.getId(),
				99L,
				100L,
				null,
				new java.math.BigDecimal("1.10"),
				Instant.now(),
				null,
				null,
				null
		);

		when(expenseTrackerRepository.findById(trackerId)).thenReturn(Optional.of(tracker));
		when(holdingRepository.findById(source.getId())).thenReturn(Optional.of(source));
		when(holdingRepository.findById(target.getId())).thenReturn(Optional.of(target));

		assertThatThrownBy(() -> service.createAssetExchange(user, trackerId, request))
				.isInstanceOf(AssetExchangeAmountLessThanFeeException.class);
	}

	@Test
	void assetExchangeRateQuote_shouldReturnRateByHoldingAssets() {
		source.setAsset(Asset.builder().code("BTC").scale(8).build());
		target.setAsset(Asset.builder().code("CZK").scale(2).build());

		AssetExchangeRateQuoteRequestDto request = new AssetExchangeRateQuoteRequestDto(
				source.getId(),
				target.getId()
		);

		when(expenseTrackerRepository.findById(trackerId)).thenReturn(Optional.of(tracker));
		when(holdingRepository.findById(source.getId())).thenReturn(Optional.of(source));
		when(holdingRepository.findById(target.getId())).thenReturn(Optional.of(target));
		when(exchangeRateService.getRate(any(Asset.class), any(Asset.class), any(java.time.LocalDate.class)))
				.thenReturn(new java.math.BigDecimal("2234567.89"));

		AssetExchangeRateQuoteResponseDto response = service.assetExchangeRateQuote(user, trackerId, request);

		assertThat(response.sourceHoldingId()).isEqualTo(source.getId());
		assertThat(response.targetHoldingId()).isEqualTo(target.getId());
		assertThat(response.sourceAssetCode()).isEqualTo("BTC");
		assertThat(response.targetAssetCode()).isEqualTo("CZK");
		assertThat(response.exchangeRate()).isEqualByComparingTo("2234567.89");
	}

	@Test
	void assetExchangeRateQuote_shouldUsePreferredDisplayAssetWhenTargetHoldingMissing() {
		source.setAsset(Asset.builder().code("BTC").scale(8).build());
		tracker.setPreferredDisplayAsset(Asset.builder().code("USD").scale(2).build());

		AssetExchangeRateQuoteRequestDto request = new AssetExchangeRateQuoteRequestDto(
				source.getId(),
				null
		);

		when(expenseTrackerRepository.findById(trackerId)).thenReturn(Optional.of(tracker));
		when(holdingRepository.findById(source.getId())).thenReturn(Optional.of(source));
		when(exchangeRateService.getRate(any(Asset.class), any(Asset.class), any(java.time.LocalDate.class)))
				.thenReturn(new java.math.BigDecimal("93000.12"));

		AssetExchangeRateQuoteResponseDto response = service.assetExchangeRateQuote(user, trackerId, request);

		assertThat(response.sourceHoldingId()).isEqualTo(source.getId());
		assertThat(response.targetHoldingId()).isNull();
		assertThat(response.sourceAssetCode()).isEqualTo("BTC");
		assertThat(response.targetAssetCode()).isEqualTo("USD");
		assertThat(response.exchangeRate()).isEqualByComparingTo("93000.12");
	}

	@Test
	void assetExchangeRateQuote_shouldFailWhenTargetHoldingMissingAndPreferredDisplayAssetNotSet() {
		source.setAsset(Asset.builder().code("BTC").scale(8).build());
		tracker.setPreferredDisplayAsset(null);

		AssetExchangeRateQuoteRequestDto request = new AssetExchangeRateQuoteRequestDto(
				source.getId(),
				null
		);

		when(expenseTrackerRepository.findById(trackerId)).thenReturn(Optional.of(tracker));
		when(holdingRepository.findById(source.getId())).thenReturn(Optional.of(source));

		assertThatThrownBy(() -> service.assetExchangeRateQuote(user, trackerId, request))
				.isInstanceOf(OperationNotPermittedException.class)
				.hasMessageContaining("preferredDisplayAsset");
	}
}