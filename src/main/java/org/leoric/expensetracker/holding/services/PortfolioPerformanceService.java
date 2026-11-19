package org.leoric.expensetracker.holding.services;

import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.asset.models.Asset;
import org.leoric.expensetracker.exchangerate.services.interfaces.ExchangeRateService;
import org.leoric.expensetracker.holding.dto.InvestmentPerformanceDto;
import org.leoric.expensetracker.holding.models.Holding;
import org.leoric.expensetracker.holding.models.InitialValuationMode;
import org.leoric.expensetracker.holding.repositories.HoldingRepository;
import org.leoric.expensetracker.transaction.models.Transaction;
import org.leoric.expensetracker.transaction.models.constants.BalanceAdjustmentDirection;
import org.leoric.expensetracker.transaction.repositories.TransactionRepository;
import org.leoric.expensetracker.transaction.services.TransactionValuationService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PortfolioPerformanceService {
	private final HoldingRepository holdings;
	private final TransactionRepository transactions;
	private final ExchangeRateService rates;
	private final TransactionValuationService transactionValuationService;

	public Result calculate(UUID trackerId, Asset display, Instant requestedTo) {
		Instant now = Instant.now();
		Instant to = requestedTo.isBefore(now) ? requestedTo : now;
		List<Holding> allHoldings = holdings.findAllByExpenseTrackerId(trackerId);
		List<Event> events = new ArrayList<>();
		Map<UUID, Long> balances = new HashMap<>();
		Map<String, Pool> pools = new LinkedHashMap<>();
		for (Holding holding : allHoldings) {
			balances.put(holding.getId(), 0L);
			pools.computeIfAbsent(key(holding.getAsset()), ignored -> new Pool(holding.getAsset()));
			if (holding.getCreatedDate() == null) {
				pools.get(key(holding.getAsset())).fail("Chybí čas založení pozice.");
			} else if (holding.getCreatedDate().isBefore(to)) {
				events.add(new Event(holding.getCreatedDate(), holding.getId(), holding, null));
			}
		}
		for (Transaction transaction : transactions.findCompletedBefore(trackerId, to)) {
			events.add(new Event(transaction.getTransactionDate(), transaction.getId(), null, transaction));
		}
		events.sort(Comparator.comparing(Event::at).thenComparing(e -> e.holding() == null ? 1 : 0)
				.thenComparing(Event::id));

		boolean unassignedTransaction = false;
		for (Event event : events) {
			if (event.holding() != null) {
				open(event.holding(), display, pools, balances);
			} else {
				Transaction transaction = event.transaction();
				if ((transaction.getTransactionType() == org.leoric.expensetracker.transaction.models.constants.TransactionType.TRANSFER
						&& (transaction.getSourceHolding() == null || transaction.getTargetHolding() == null))
						|| (transaction.getTransactionType() != org.leoric.expensetracker.transaction.models.constants.TransactionType.TRANSFER
						&& transaction.getHolding() == null)) {
					unassignedTransaction = true;
				}
				applyTransaction(event.transaction(), display, pools, balances);
			}
		}
		return buildResult(allHoldings, display, pools, balances, to, unassignedTransaction);
	}

	private void open(Holding holding, Asset display, Map<String, Pool> pools, Map<UUID, Long> balances) {
		long quantity = holding.getInitialAmount();
		addBalance(balances, holding, quantity);
		if (quantity == 0 || same(holding.getAsset(), display)) return;
		Pool pool = pools.get(key(holding.getAsset()));
		if (holding.getInitialValuationMode() == null || holding.getInitialValuationAmount() == null
				|| holding.getInitialValuationAsset() == null || holding.getInitialValuationAt() == null) {
			pool.fail("Chybí pořizovací hodnota počátečního stavu.");
			return;
		}
		Long value = convert(holding.getInitialValuationAmount(), holding.getInitialValuationAsset(), display,
				holding.getInitialValuationAt());
		if (value == null) {
			pool.fail("Chybí kurz pořizovací hodnoty počátečního stavu.");
			return;
		}
		pool.estimated |= holding.getInitialValuationMode() == InitialValuationMode.MARKET
				|| !same(holding.getInitialValuationAsset(), display);
		apply(pool, holding, quantity, value);
	}

	private void applyTransaction(Transaction t, Asset display, Map<String, Pool> pools, Map<UUID, Long> balances) {
		if (t.getTransactionType() == org.leoric.expensetracker.transaction.models.constants.TransactionType.TRANSFER) {
			transfer(t, display, pools, balances);
			return;
		}
		Holding holding = t.getHolding();
		if (holding == null || !pools.containsKey(key(holding.getAsset()))) return;
		Pool pool = pools.get(key(holding.getAsset()));
		checkDate(t, holding, pool);
		if (transactionValuationService.hasAmbiguousLegacyBalanceEffect(t, holding)) {
			pool.fail("Starší transakce má nejasný přepočet nebo poplatek; zkontrolujte a upravte ji.");
		}
		long amount = t.getSettledAmount() != null ? t.getSettledAmount() : t.getAmount();
		long change = switch (t.getTransactionType()) {
			case INCOME -> amount;
			case EXPENSE, TEST -> Math.negateExact(t.getSettledAmount() != null ? amount : Math.addExact(amount, t.getFeeAmount()));
			case BALANCE_ADJUSTMENT -> t.getBalanceAdjustmentDirection() == BalanceAdjustmentDirection.ADDITION
					? t.getAmount() : Math.negateExact(t.getAmount());
			default -> throw new IllegalStateException("Unsupported transaction type: " + t.getTransactionType());
		};
		addBalance(balances, holding, change);
		boolean income = t.getTransactionType() == org.leoric.expensetracker.transaction.models.constants.TransactionType.INCOME;
		boolean expense = t.getTransactionType() == org.leoric.expensetracker.transaction.models.constants.TransactionType.EXPENSE
				|| t.getTransactionType() == org.leoric.expensetracker.transaction.models.constants.TransactionType.TEST;
		long fee = income || expense ? (t.getSettledFeeAmount() != null ? t.getSettledFeeAmount() : t.getFeeAmount()) : 0;
		if (fee < 0 || (expense && fee > 0 && Math.negateExact(change) < fee)) {
			pool.fail("Neplatný poplatek transakce.");
			return;
		}
		if (change == 0 && fee == 0) return;
		if (same(holding.getAsset(), display)) {
			if (fee > 0) pool.realized.merge(holding.getId(), BigDecimal.valueOf(-fee), BigDecimal::add);
			return;
		}
		long principal = expense || income ? Math.addExact(change, fee) : change;
		if (principal != 0) {
			Long value = transactionValuationService.convert(t, Math.absExact(principal), holding.getAsset(), display);
			if (value == null) pool.fail("Chybí kurz transakce.");
			else {
				pool.estimated = true;
				apply(pool, holding, principal, value);
			}
		}
		if (fee > 0) apply(pool, holding, -fee, 0);
	}

	private void transfer(Transaction t, Asset display, Map<String, Pool> pools, Map<UUID, Long> balances) {
		Holding source = t.getSourceHolding();
		Holding target = t.getTargetHolding();
		if (source == null || target == null || source.getAsset() == null || target.getAsset() == null
				|| !pools.containsKey(key(source.getAsset())) || !pools.containsKey(key(target.getAsset()))) {
			if (source != null && source.getAsset() != null && pools.containsKey(key(source.getAsset()))) {
				pools.get(key(source.getAsset())).fail("Chybí zdrojová nebo cílová pozice převodu.");
			}
			if (target != null && target.getAsset() != null && pools.containsKey(key(target.getAsset()))) {
				pools.get(key(target.getAsset())).fail("Chybí zdrojová nebo cílová pozice převodu.");
			}
			return;
		}
		Pool from = pools.get(key(source.getAsset()));
		Pool to = pools.get(key(target.getAsset()));
		checkDate(t, source, from);
		checkDate(t, target, to);
		boolean sameAsset = same(source.getAsset(), target.getAsset());
		Long received = t.getSettledAmount();
		if (received == null && sameAsset) received = Math.subtractExact(t.getAmount(), t.getFeeAmount());
		if (received == null || received < 0 || t.getAmount() < 0) {
			from.fail("Chybí platné přijaté množství převodu.");
			to.fail("Chybí platné přijaté množství převodu.");
			return;
		}
		addBalance(balances, source, Math.negateExact(t.getAmount()));
		addBalance(balances, target, received);
		if (sameAsset) {
			long difference = Math.subtractExact(received, t.getAmount());
			if (same(source.getAsset(), display) && difference != 0) {
				from.realized.merge(source.getId(), BigDecimal.valueOf(difference), BigDecimal::add);
			} else if (difference < 0) apply(from, source, difference, 0);
			else if (!same(source.getAsset(), display) && difference > 0) {
				Long value = transactionValuationService.convert(t, difference, source.getAsset(), display);
				if (value == null) from.fail("Chybí kurz přebytku převodu.");
				else {
					from.estimated = true;
					apply(from, target, difference, value);
				}
			}
			return;
		}
		Long value;
		if (same(source.getAsset(), display)) value = t.getAmount();
		else if (same(target.getAsset(), display)) value = received;
		else {
			value = transactionValuationService.convert(t, received, target.getAsset(), display);
			from.estimated = true;
			to.estimated = true;
		}
		if (value == null) {
			from.fail("Chybí kurz protihodnoty směny.");
			to.fail("Chybí kurz protihodnoty směny.");
			return;
		}
		if (!same(source.getAsset(), display) && t.getAmount() != 0) apply(from, source, -t.getAmount(), value);
		if (!same(target.getAsset(), display) && received != 0) apply(to, target, received, value);
	}

	private Result buildResult(List<Holding> allHoldings, Asset display, Map<String, Pool> pools,
			Map<UUID, Long> balances, Instant to, boolean unassignedTransaction) {
		Map<UUID, InvestmentPerformanceDto> byHolding = new HashMap<>();
		long portfolioRealized = 0;
		long portfolioUnrealized = 0;
		boolean estimated = false;
		String unavailable = unassignedTransaction ? "Historie obsahuje transakci bez přiřazené pozice." : null;
		for (Pool pool : pools.values()) {
			List<Holding> group = allHoldings.stream().filter(h -> same(h.getAsset(), pool.asset))
					.sorted(Comparator.comparing(Holding::getId)).toList();
			if (same(pool.asset, display)) {
				for (Holding holding : group) {
					long realized = round(pool.realized.getOrDefault(holding.getId(), BigDecimal.ZERO));
					byHolding.put(holding.getId(), pool.reason == null
							? new InvestmentPerformanceDto(realized, 0L, realized, false, false, null, to)
							: new InvestmentPerformanceDto(null, null, null, false, false, pool.reason, to));
					portfolioRealized = Math.addExact(portfolioRealized, realized);
				}
				if (pool.reason != null) unavailable = "Výsledek portfolia není úplný; zkontrolujte ocenění jednotlivých pozic.";
				continue;
			}
			long positionQuantity = pool.position.quantity().longValueExact();
			Long market = positionQuantity == 0 ? 0L : convert(positionQuantity, pool.asset, display, to);
			if (market == null) pool.fail("Chybí kurz pro ocenění koncové pozice.");
			pool.estimated |= positionQuantity != 0;
			long unrealized = pool.reason == null ? round(pool.position.unrealized(BigDecimal.valueOf(market))) : 0;
			long absoluteTotal = 0;
			boolean positive = false;
			boolean negative = false;
			for (Holding holding : group) {
				long balance = balances.getOrDefault(holding.getId(), 0L);
				absoluteTotal = Math.addExact(absoluteTotal, Math.absExact(balance));
				positive |= balance > 0;
				negative |= balance < 0;
			}
			boolean artificial = positive && negative;
			long remaining = unrealized;
			long remainingWeight = absoluteTotal;
			for (Holding holding : group) {
				long weight = Math.absExact(balances.getOrDefault(holding.getId(), 0L));
				long share = weight == 0 || remainingWeight == 0 ? 0
						: BigDecimal.valueOf(remaining).multiply(BigDecimal.valueOf(weight))
								.divide(BigDecimal.valueOf(remainingWeight), 0, RoundingMode.HALF_UP).longValueExact();
				remaining = Math.subtractExact(remaining, share);
				remainingWeight = Math.subtractExact(remainingWeight, weight);
				long realized = round(pool.realized.getOrDefault(holding.getId(), BigDecimal.ZERO));
				InvestmentPerformanceDto item = pool.reason == null
						? new InvestmentPerformanceDto(realized, share, Math.addExact(realized, share), pool.estimated, artificial, null, to)
						: new InvestmentPerformanceDto(null, null, null, pool.estimated, artificial, pool.reason, to);
				byHolding.put(holding.getId(), item);
				portfolioRealized = Math.addExact(portfolioRealized, realized);
			}
			portfolioUnrealized = Math.addExact(portfolioUnrealized, unrealized);
			estimated |= pool.estimated;
			if (pool.reason != null) unavailable = "Výsledek portfolia není úplný; zkontrolujte ocenění jednotlivých pozic.";
		}
		InvestmentPerformanceDto portfolio = unavailable == null
				? new InvestmentPerformanceDto(portfolioRealized, portfolioUnrealized,
						Math.addExact(portfolioRealized, portfolioUnrealized), estimated, false, null, to)
				: new InvestmentPerformanceDto(null, null, null, estimated, false, unavailable, to);
		return new Result(byHolding, portfolio);
	}

	private void checkDate(Transaction t, Holding holding, Pool pool) {
		if (holding.getCreatedDate() == null || t.getTransactionDate().isBefore(holding.getCreatedDate())) {
			pool.fail("Transakce je datována před vznik pozice.");
		}
	}

	private void apply(Pool pool, Holding holding, long quantity, long value) {
		if (pool.reason != null || quantity == 0) return;
		BigDecimal realized = pool.position.apply(BigDecimal.valueOf(quantity), BigDecimal.valueOf(value));
		pool.realized.merge(holding.getId(), realized, BigDecimal::add);
	}

	private void addBalance(Map<UUID, Long> balances, Holding holding, long amount) {
		balances.merge(holding.getId(), amount, Math::addExact);
	}

	private Long convert(long amount, Asset from, Asset to, Instant at) {
		if (amount == 0) return 0L;
		if (same(from, to)) return amount;
		return rates.convertAmount(amount, from, to, at);
	}

	private boolean same(Asset left, Asset right) {
		return left.getCode().equalsIgnoreCase(right.getCode());
	}

	private String key(Asset asset) {
		return asset.getCode().toUpperCase(java.util.Locale.ROOT);
	}

	private long round(BigDecimal value) {
		return value.setScale(0, RoundingMode.HALF_UP).longValueExact();
	}

	public record Result(Map<UUID, InvestmentPerformanceDto> holdings, InvestmentPerformanceDto portfolio) {}

	private record Event(Instant at, UUID id, Holding holding, Transaction transaction) {}

	private static final class Pool {
		private final Asset asset;
		private final WeightedPosition position = new WeightedPosition();
		private final Map<UUID, BigDecimal> realized = new HashMap<>();
		private boolean estimated;
		private String reason;

		private Pool(Asset asset) {
			this.asset = asset;
		}

		private void fail(String reason) {
			if (this.reason == null) this.reason = reason;
		}
	}
}
