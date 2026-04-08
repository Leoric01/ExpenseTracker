package org.leoric.expensetracker.asset.seed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.leoric.expensetracker.asset.models.Asset;
import org.leoric.expensetracker.asset.models.constants.AssetType;
import org.leoric.expensetracker.asset.models.constants.MarketDataSource;
import org.leoric.expensetracker.asset.repositories.AssetRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Order(1)
@Slf4j
@RequiredArgsConstructor
public class AssetSeeder implements CommandLineRunner {

	private final AssetRepository assetRepository;

	@Override
	@Transactional
	public void run(String... args) {
		List<SeedEntry> seeds = List.of(
				// Fiat currencies
				new SeedEntry("CZK", "Czech Koruna", AssetType.FIAT, 2, MarketDataSource.FRANKFURTER, "CZK"),
				new SeedEntry("EUR", "Euro", AssetType.FIAT, 2, MarketDataSource.FRANKFURTER, "EUR"),
				new SeedEntry("USD", "US Dollar", AssetType.FIAT, 2, MarketDataSource.FRANKFURTER, "USD"),
				new SeedEntry("GBP", "British Pound", AssetType.FIAT, 2, MarketDataSource.FRANKFURTER, "GBP"),
				new SeedEntry("CHF", "Swiss Franc", AssetType.FIAT, 2, MarketDataSource.FRANKFURTER, "CHF"),
				new SeedEntry("PLN", "Polish Zloty", AssetType.FIAT, 2, MarketDataSource.FRANKFURTER, "PLN"),
				new SeedEntry("JPY", "Japanese Yen", AssetType.FIAT, 0, MarketDataSource.FRANKFURTER, "JPY"),
				new SeedEntry("CAD", "Canadian Dollar", AssetType.FIAT, 2, MarketDataSource.FRANKFURTER, "CAD"),
				new SeedEntry("AUD", "Australian Dollar", AssetType.FIAT, 2, MarketDataSource.FRANKFURTER, "AUD"),
				new SeedEntry("SEK", "Swedish Krona", AssetType.FIAT, 2, MarketDataSource.FRANKFURTER, "SEK"),
				new SeedEntry("NOK", "Norwegian Krone", AssetType.FIAT, 2, MarketDataSource.FRANKFURTER, "NOK"),
				new SeedEntry("DKK", "Danish Krone", AssetType.FIAT, 2, MarketDataSource.FRANKFURTER, "DKK"),
				new SeedEntry("HUF", "Hungarian Forint", AssetType.FIAT, 2, MarketDataSource.FRANKFURTER, "HUF"),
				new SeedEntry("RON", "Romanian Leu", AssetType.FIAT, 2, MarketDataSource.FRANKFURTER, "RON"),
				new SeedEntry("TRY", "Turkish Lira", AssetType.FIAT, 2, MarketDataSource.FRANKFURTER, "TRY"),

				// Crypto
				new SeedEntry("BTC", "Bitcoin", AssetType.CRYPTO, 8, MarketDataSource.COINGECKO, "bitcoin"),
				new SeedEntry("ETH", "Ethereum", AssetType.CRYPTO, 18, MarketDataSource.COINGECKO, "ethereum"),
				new SeedEntry("XMR", "Monero", AssetType.CRYPTO, 12, MarketDataSource.COINGECKO, "monero"),
				new SeedEntry("LTC", "Litecoin", AssetType.CRYPTO, 8, MarketDataSource.COINGECKO, "litecoin"),
				new SeedEntry("SOL", "Solana", AssetType.CRYPTO, 9, MarketDataSource.COINGECKO, "solana"),
				new SeedEntry("ADA", "Cardano", AssetType.CRYPTO, 6, MarketDataSource.COINGECKO, "cardano"),
				new SeedEntry("DOGE", "Dogecoin", AssetType.CRYPTO, 8, MarketDataSource.COINGECKO, "dogecoin"),
				new SeedEntry("DOT", "Polkadot", AssetType.CRYPTO, 10, MarketDataSource.COINGECKO, "polkadot"),
				new SeedEntry("USDT", "Tether", AssetType.CRYPTO, 6, MarketDataSource.COINGECKO, "tether"),
				new SeedEntry("USDC", "USD Coin", AssetType.CRYPTO, 6, MarketDataSource.COINGECKO, "usd-coin")
		);

		int created = 0;
		for (SeedEntry seed : seeds) {
			if (!assetRepository.existsByCodeIgnoreCase(seed.code)) {
				Asset asset = Asset.builder()
						.code(seed.code)
						.name(seed.name)
						.assetType(seed.assetType)
						.scale(seed.scale)
						.marketDataSource(seed.marketDataSource)
						.marketDataKey(seed.marketDataKey)
						.build();
				assetRepository.save(asset);
				created++;
			}
		}

		if (created > 0) {
			log.info("Seeded {} new assets", created);
		} else {
			log.debug("All assets already seeded, nothing to do");
		}
	}

	private record SeedEntry(String code, String name, AssetType assetType, int scale,
	                          MarketDataSource marketDataSource, String marketDataKey) {
	}
}