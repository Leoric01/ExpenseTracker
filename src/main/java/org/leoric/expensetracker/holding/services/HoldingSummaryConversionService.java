package org.leoric.expensetracker.holding.services;

import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.asset.models.Asset;
import org.leoric.expensetracker.exchangerate.services.interfaces.ExchangeRateService;
import org.leoric.expensetracker.holding.dto.HoldingSummaryResponseDto;
import org.leoric.expensetracker.holding.models.Holding;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class HoldingSummaryConversionService {
	private final ExchangeRateService rates;
	private final HoldingPeriodPerformanceService periodPerformance;

	public HoldingSummaryResponseDto enrich(HoldingSummaryResponseDto summary, Holding holding, Asset display,
			PortfolioPerformanceService.Result performance) {
		Instant now = Instant.now();
		LocalDate end = (summary.periodTo().isBefore(now) ? summary.periodTo() : now)
				.atZone(ZoneOffset.UTC).toLocalDate();
		LocalDate start = summary.periodFrom().atZone(ZoneOffset.UTC).toLocalDate();
		Asset asset = holding.getAsset();
		boolean same = asset.getCode().equalsIgnoreCase(display.getCode());
		BigDecimal rate = same ? BigDecimal.ONE : rates.getRate(asset, display, end);
		Long convertedEnd = same ? summary.endBalance()
				: summary.endBalance() == 0 ? 0L : rates.convertAmount(summary.endBalance(), asset, display, end);
		Long convertedStart = same ? summary.startBalance()
				: summary.startBalance() == 0 ? 0L : rates.convertAmount(summary.startBalance(), asset, display, start);
		return new HoldingSummaryResponseDto(
				summary.holdingId(), summary.accountName(), summary.institutionName(),
				summary.assetCode(), summary.assetScale(), summary.periodFrom(), summary.periodTo(),
				summary.startBalance(), summary.endBalance(), summary.totalIncome(), summary.totalExpense(),
				summary.totalTransferIn(), summary.totalTransferOut(), summary.difference(),
				summary.incomeByCategory(), summary.expenseByCategory(), convertedStart, convertedEnd, rate,
				periodPerformance.calculate(holding, display, summary, convertedStart, convertedEnd),
				performance.holdings().get(holding.getId()),
				same ? null : rates.getRateMetadata(asset, display, end));
	}
}
