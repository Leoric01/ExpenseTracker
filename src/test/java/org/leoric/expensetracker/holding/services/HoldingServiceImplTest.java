package org.leoric.expensetracker.holding.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.leoric.expensetracker.account.models.Account;
import org.leoric.expensetracker.account.repositories.AccountRepository;
import org.leoric.expensetracker.asset.models.Asset;
import org.leoric.expensetracker.asset.repositories.AssetRepository;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.exchangerate.services.interfaces.ExchangeRateService;
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.leoric.expensetracker.handler.exceptions.OperationNotPermittedException;
import org.leoric.expensetracker.holding.dto.CreateHoldingRequestDto;
import org.leoric.expensetracker.holding.mapstruct.HoldingMapper;
import org.leoric.expensetracker.holding.models.Holding;
import org.leoric.expensetracker.holding.models.InitialValuationMode;
import org.leoric.expensetracker.holding.repositories.HoldingRepository;
import org.leoric.expensetracker.holding.services.interfaces.HoldingSummaryBuilder;
import org.leoric.expensetracker.institution.models.Institution;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HoldingServiceImplTest {
	@Mock HoldingRepository holdings;
	@Mock AccountRepository accounts;
	@Mock AssetRepository assets;
	@Mock HoldingSummaryBuilder summaries;
	@Mock HoldingMapper mapper;
	@Mock ExchangeRateService rates;
	@InjectMocks HoldingServiceImpl service;

	UUID trackerId;
	UUID accountId;
	UUID btcId;
	UUID czkId;
	Asset btc;
	Asset czk;
	Account account;
	User user;

	@BeforeEach
	void setUp() {
		trackerId = UUID.randomUUID();
		accountId = UUID.randomUUID();
		btcId = UUID.randomUUID();
		czkId = UUID.randomUUID();
		btc = Asset.builder().id(btcId).code("BTC").scale(8).build();
		czk = Asset.builder().id(czkId).code("CZK").scale(2).build();
		ExpenseTracker tracker = ExpenseTracker.builder().id(trackerId).name("Tracker").preferredDisplayAsset(czk).build();
		account = Account.builder().id(accountId).name("Account")
				.institution(Institution.builder().name("Institution").expenseTracker(tracker).build()).build();
		user = User.builder().email("test@example.com").build();
		when(accounts.findById(accountId)).thenReturn(Optional.of(account));
		when(assets.findById(btcId)).thenReturn(Optional.of(btc));
	}

	@Test
	void marketInitialValuationPersistsCapturedQuote() {
		when(rates.convertAmount(eq(100L), eq(btc), eq(czk), any(Instant.class))).thenReturn(10_000L);
		when(holdings.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		CreateHoldingRequestDto request = new CreateHoldingRequestDto(accountId, btcId, 100L,
				InitialValuationMode.MARKET, null, null, null);

		service.holdingCreate(user, trackerId, request);

		ArgumentCaptor<Holding> saved = ArgumentCaptor.forClass(Holding.class);
		verify(holdings).save(saved.capture());
		assertThat(saved.getValue().getInitialValuationAmount()).isEqualTo(10_000);
		assertThat(saved.getValue().getInitialValuationAsset()).isSameAs(czk);
		assertThat(saved.getValue().getOpeningMarketValue()).isEqualTo(10_000);
		assertThat(saved.getValue().getOpeningMarketValueObservedAt()).isNotNull();
	}

	@Test
	void manualInitialValuationKeepsCostSeparateFromOpeningMarketValue() {
		Instant purchasedAt = Instant.parse("2024-01-01T12:00:00Z");
		when(rates.convertAmount(eq(100L), eq(btc), eq(czk), any(Instant.class))).thenReturn(11_000L);
		when(assets.findById(czkId)).thenReturn(Optional.of(czk));
		when(holdings.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		CreateHoldingRequestDto request = new CreateHoldingRequestDto(accountId, btcId, 100L,
				InitialValuationMode.MANUAL, 9_000L, czkId, purchasedAt);

		service.holdingCreate(user, trackerId, request);

		ArgumentCaptor<Holding> saved = ArgumentCaptor.forClass(Holding.class);
		verify(holdings).save(saved.capture());
		assertThat(saved.getValue().getInitialValuationAmount()).isEqualTo(9_000);
		assertThat(saved.getValue().getInitialValuationAt()).isEqualTo(purchasedAt);
		assertThat(saved.getValue().getOpeningMarketValue()).isEqualTo(11_000);
	}

	@Test
	void marketInitialValuationRejectsUnavailableRate() {
		when(rates.convertAmount(eq(100L), eq(btc), eq(czk), any(Instant.class))).thenReturn(null);
		CreateHoldingRequestDto request = new CreateHoldingRequestDto(accountId, btcId, 100L,
				InitialValuationMode.MARKET, null, null, null);

		assertThatThrownBy(() -> service.holdingCreate(user, trackerId, request))
				.isInstanceOf(OperationNotPermittedException.class)
				.hasMessageContaining("unavailable");
		verify(holdings, never()).save(any());
	}
}
