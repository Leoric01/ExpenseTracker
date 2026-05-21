package org.leoric.expensetracker.institution.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.leoric.expensetracker.asset.models.Asset;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.exchangerate.services.interfaces.ExchangeRateService;
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.leoric.expensetracker.expensetracker.repositories.ExpenseTrackerRepository;
import org.leoric.expensetracker.holding.dto.HoldingSummaryResponseDto;
import org.leoric.expensetracker.holding.models.Holding;
import org.leoric.expensetracker.holding.repositories.HoldingRepository;
import org.leoric.expensetracker.holding.services.interfaces.HoldingSummaryBuilder;
import org.leoric.expensetracker.institution.dto.FinanceHeaderBalancesResponse;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstitutionServiceImplTest {

	@Mock
	private ExpenseTrackerRepository expenseTrackerRepository;
	@Mock
	private HoldingRepository holdingRepository;
	@Mock
	private HoldingSummaryBuilder holdingSummaryBuilder;
	@Mock
	private ExchangeRateService exchangeRateService;

	@InjectMocks
	private InstitutionServiceImpl institutionService;

	@Test
	void institutionHeaderBalances_shouldUseOnlyFullyActiveHierarchyHoldings() {
		UUID trackerId = UUID.randomUUID();
		User user = User.builder().id(UUID.randomUUID()).email("tester@example.com").build();
		Instant from = Instant.parse("2026-05-01T00:00:00Z");
		Instant to = Instant.parse("2026-05-31T23:59:59Z");

		ExpenseTracker tracker = ExpenseTracker.builder()
				.id(trackerId)
				.preferredDisplayAsset(null)
				.build();

		Asset czk = Asset.builder().code("CZK").scale(2).build();
		Asset btc = Asset.builder().code("BTC").scale(8).build();
		Holding h1 = Holding.builder().id(UUID.randomUUID()).asset(czk).build();
		Holding h2 = Holding.builder().id(UUID.randomUUID()).asset(czk).build();
		Holding h3 = Holding.builder().id(UUID.randomUUID()).asset(btc).build();

		when(expenseTrackerRepository.findById(trackerId)).thenReturn(Optional.of(tracker));
		when(holdingRepository.findByExpenseTrackerIdAndFullyActiveHierarchy(trackerId)).thenReturn(List.of(h1, h2, h3));
		when(holdingSummaryBuilder.buildSummary(h1, from, to)).thenReturn(summary(h1.getId(), "CZK", 2, 1_500L, from, to));
		when(holdingSummaryBuilder.buildSummary(h2, from, to)).thenReturn(summary(h2.getId(), "CZK", 2, -250L, from, to));
		when(holdingSummaryBuilder.buildSummary(h3, from, to)).thenReturn(summary(h3.getId(), "BTC", 8, 99L, from, to));

		FinanceHeaderBalancesResponse result = institutionService.institutionHeaderBalances(user, trackerId, from, to);

		assertThat(result.displayAssetCode()).isNull();
		assertThat(result.displayAssetScale()).isNull();
		assertThat(result.grandTotalConverted()).isNull();
		assertThat(result.nativeBalances()).hasSize(2);
		assertThat(result.nativeBalances().getFirst().assetCode()).isEqualTo("BTC");
		assertThat(result.nativeBalances().getFirst().assetScale()).isEqualTo(8);
		assertThat(result.nativeBalances().getFirst().totalMinorUnits()).isEqualTo(99L);
		assertThat(result.nativeBalances().get(1).assetCode()).isEqualTo("CZK");
		assertThat(result.nativeBalances().get(1).assetScale()).isEqualTo(2);
		assertThat(result.nativeBalances().get(1).totalMinorUnits()).isEqualTo(1_250L);
	}

	@Test
	void institutionHeaderBalances_shouldUseFastPathWhenDisplayAssetMatchesHoldingAsset() {
		UUID trackerId = UUID.randomUUID();
		User user = User.builder().id(UUID.randomUUID()).email("tester@example.com").build();
		Instant from = Instant.parse("2026-05-01T00:00:00Z");
		Instant to = Instant.parse("2026-05-31T23:59:59Z");

		Asset czk = Asset.builder().code("CZK").scale(2).build();
		ExpenseTracker tracker = ExpenseTracker.builder().id(trackerId).preferredDisplayAsset(czk).build();
		Holding h1 = Holding.builder().id(UUID.randomUUID()).asset(czk).build();
		Holding h2 = Holding.builder().id(UUID.randomUUID()).asset(czk).build();

		when(expenseTrackerRepository.findById(trackerId)).thenReturn(Optional.of(tracker));
		when(holdingRepository.findByExpenseTrackerIdAndFullyActiveHierarchy(trackerId)).thenReturn(List.of(h1, h2));
		when(holdingSummaryBuilder.buildSummary(h1, from, to)).thenReturn(summary(h1.getId(), "CZK", 2, 1_500L, from, to));
		when(holdingSummaryBuilder.buildSummary(h2, from, to)).thenReturn(summary(h2.getId(), "CZK", 2, -250L, from, to));

		FinanceHeaderBalancesResponse result = institutionService.institutionHeaderBalances(user, trackerId, from, to);

		assertThat(result.displayAssetCode()).isEqualTo("CZK");
		assertThat(result.grandTotalConverted()).isEqualTo(1_250L);
		verify(exchangeRateService, never()).getRate(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
		verify(exchangeRateService, never()).convertAmount(
				org.mockito.ArgumentMatchers.anyLong(),
				org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any(LocalDate.class)
		);
	}

	@Test
	void institutionHeaderBalances_shouldConvertOnlyEndBalanceForNonMatchingAsset() {
		UUID trackerId = UUID.randomUUID();
		User user = User.builder().id(UUID.randomUUID()).email("tester@example.com").build();
		Instant from = Instant.parse("2026-05-01T00:00:00Z");
		Instant to = Instant.parse("2026-05-31T23:59:59Z");

		Asset displayAsset = Asset.builder().code("CZK").scale(2).build();
		Asset btc = Asset.builder().code("BTC").scale(8).build();
		ExpenseTracker tracker = ExpenseTracker.builder().id(trackerId).preferredDisplayAsset(displayAsset).build();
		Holding h1 = Holding.builder().id(UUID.randomUUID()).asset(btc).build();

		when(expenseTrackerRepository.findById(trackerId)).thenReturn(Optional.of(tracker));
		when(holdingRepository.findByExpenseTrackerIdAndFullyActiveHierarchy(trackerId)).thenReturn(List.of(h1));
		when(holdingSummaryBuilder.buildSummary(h1, from, to)).thenReturn(summary(h1.getId(), "BTC", 8, 100L, from, to));
		when(exchangeRateService.getRate(btc, displayAsset, LocalDate.of(2026, 5, 31)))
				.thenReturn(java.math.BigDecimal.ONE);
		when(exchangeRateService.convertAmount(100L, btc, displayAsset, LocalDate.of(2026, 5, 31)))
				.thenReturn(250_000L);

		FinanceHeaderBalancesResponse result = institutionService.institutionHeaderBalances(user, trackerId, from, to);

		assertThat(result.grandTotalConverted()).isEqualTo(250_000L);
		verify(exchangeRateService).getRate(btc, displayAsset, LocalDate.of(2026, 5, 31));
		verify(exchangeRateService).convertAmount(100L, btc, displayAsset, LocalDate.of(2026, 5, 31));
	}

	private HoldingSummaryResponseDto summary(UUID holdingId, String assetCode, int assetScale, long endBalance, Instant from, Instant to) {
		return new HoldingSummaryResponseDto(
				holdingId,
				"Account",
				"Institution",
				assetCode,
				assetScale,
				from,
				to,
				0L,
				endBalance,
				0L,
				0L,
				0L,
				0L,
				0L,
				List.of(),
				List.of(),
				null,
				null,
				null
		);
	}
}