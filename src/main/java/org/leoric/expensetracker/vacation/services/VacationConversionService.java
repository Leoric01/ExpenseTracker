package org.leoric.expensetracker.vacation.services;

import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.asset.models.Asset;
import org.leoric.expensetracker.asset.models.constants.AssetType;
import org.leoric.expensetracker.asset.repositories.AssetRepository;
import org.leoric.expensetracker.exchangerate.services.interfaces.ExchangeRateService;
import org.leoric.expensetracker.handler.exceptions.OperationNotPermittedException;
import org.leoric.expensetracker.vacation.dtos.VacationDtos.Expense;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

@Service @RequiredArgsConstructor
public class VacationConversionService {
	private static final BigDecimal MAX_SAFE = BigDecimal.valueOf(9007199254740991L);
	private final VacationService vacationService;
	private final AssetRepository assets;
	private final ExchangeRateService rates;
	public record Converted(String id, String currencyCode, Long amountMinor, Long convertedMinor,
			BigDecimal rate, String error) {}
	public record Conversion(String targetCurrency, int targetScale, LocalDate requestedDate, boolean estimated,
			String note, Converted total, List<Converted> expenses, Long expensesSumMinor) {}
	public Conversion convert(UUID trackerId, UUID vacationId, String targetCode, String mode) {
		var detail = vacationService.detail(trackerId, vacationId);
		var v = detail.vacation();
		Asset target = asset(targetCode);
		LocalDate today = LocalDate.now(ZoneOffset.UTC);
		LocalDate date;
		boolean estimated;
		if ("current".equals(mode)) { date = today; estimated = true; }
		else if ("historical".equals(mode)) {
			if (v.startDate() == null || v.approximate()) throw new OperationNotPermittedException("Exact start date is required for historical conversion");
			estimated = v.startDate().isAfter(today);
			date = estimated ? today : v.startDate();
		} else throw new OperationNotPermittedException("Invalid conversion mode");
		var cachedRates = new HashMap<String, Optional<BigDecimal>>();
		Converted total = v.totalMinor() == null ? null : convertOne("total", v.currencyCode(), v.currencyScale(), v.totalMinor(), target, date, cachedRates);
		var converted = new ArrayList<Converted>();
		long sum = 0;
		boolean complete = true;
		for (Expense item : detail.expenses()) {
			Converted value = convertOne(item.id().toString(), item.currencyCode(), item.currencyScale(), item.amountMinor(), target, date, cachedRates);
			converted.add(value);
			if (value.convertedMinor() == null || sum > 9007199254740991L - value.convertedMinor()) complete = false;
			else sum += value.convertedMinor();
		}
		return new Conversion(target.getCode(), target.getScale(), date, estimated,
				estimated ? "Aktuální orientační kurz; kurz může být uložen v cache." : "Požadované datum; skutečné datum kurzu poskytovatele nemusí být dostupné.",
				total, List.copyOf(converted), !converted.isEmpty() && complete ? sum : null);
	}
	private Converted convertOne(String id, String code, int scale, long amount, Asset target, LocalDate date,
			Map<String,Optional<BigDecimal>> cache) {
		var source = assets.findByCodeIgnoreCase(code).filter(a -> a.getAssetType() == AssetType.FIAT).orElse(null);
		if (source == null) return new Converted(id,code,amount,null,null,"Měna již není dostupná");
		Optional<BigDecimal> rate = cache.computeIfAbsent(code, ignored -> Optional.ofNullable(rates.getRate(source,target,date)));
		if (rate.isEmpty() || rate.get().signum() <= 0) return new Converted(id,code,amount,null,null,"Kurz není dostupný");
		BigDecimal result = BigDecimal.valueOf(amount).multiply(rate.get()).scaleByPowerOfTen(target.getScale() - scale).setScale(0, RoundingMode.HALF_UP);
		if (result.compareTo(MAX_SAFE) > 0) return new Converted(id,code,amount,null,rate.get(),"Přepočtená částka je příliš vysoká");
		return new Converted(id,code,amount,result.longValueExact(),rate.get(),null);
	}
	private Asset asset(String code) {
		return assets.findByCodeIgnoreCase(code).filter(a -> a.getAssetType() == AssetType.FIAT)
				.orElseThrow(() -> new OperationNotPermittedException("Unknown target currency"));
	}
}
