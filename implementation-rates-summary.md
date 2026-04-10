# Exchange Rate Integration — Implementace

## Co už bylo připraveno v projektu

Tvůj projekt měl skvělou přípravu:

1. **`ExpenseTracker.preferredDisplayAsset`** — pole pro preferovanou měnu (už existovalo)
2. **`Asset` entity** s `marketDataSource` (FRANKFURTER / COINGECKO / NONE / MANUAL) a `marketDataKey` (e.g. `"bitcoin"`, `"CZK"`)
3. **`AssetSeeder`** — 15 fiat měn (FRANKFURTER) + 10 kryptoměn (COINGECKO) se správnými klíči

## Co jsem implementoval

### Nový modul: `exchangerate/`

| Soubor | Účel |
|--------|------|
| `models/ExchangeRateCache.java` | JPA entita pro DB cache kurzů (`base_asset_code`, `quote_asset_code`, `rate_date`, `rate`) |
| `repositories/ExchangeRateCacheRepository.java` | Repository pro vyhledávání kešovaných kurzů |
| `config/MarketDataProperties.java` | `@ConfigurationProperties` pro URL a API klíče |
| `config/MarketDataConfig.java` | `RestClient` beany pro Frankfurter a CoinGecko |
| `clients/FrankfurterClient.java` | HTTP klient pro fiat→fiat kurzy (latest + historické) |
| `clients/CoinGeckoClient.java` | HTTP klient pro crypto ceny (current + historické) |
| `services/interfaces/ExchangeRateService.java` | Interface — `getRate()` + `convertAmount()` |
| `services/ExchangeRateServiceImpl.java` | Implementace s DB cachováním a routing podle typu assetu |

### Logika konverze

```
FIAT → FIAT:     Frankfurter API (GET /{date}?base=CZK&symbols=EUR)
CRYPTO → FIAT:   CoinGecko API  (GET /simple/price?ids=bitcoin&vs_currencies=czk)
FIAT → CRYPTO:   CoinGecko invertovaný (1 / crypto→fiat)
CRYPTO → CRYPTO: přes USD intermediary
```

### Konverze minor units

```
convertedAmount = nativeAmount × rate × 10^(displayScale − nativeScale)
```

Příklad: 9971035 satoshi BTC (scale=8) → CZK (scale=2), kurz 2 000 000:
```
9971035 × 2000000 × 10^(2-8) = 9971035 × 2000000 × 0.000001 = 19 942 070 haléřů = 199 420.70 CZK
```

### Upravené DTOs

- **`HoldingSummaryResponseDto`** — přidáno: `convertedStartBalance`, `convertedEndBalance`, `exchangeRate`
- **`AccountSummaryResponseDto`** — přidáno: `convertedTotalBalance`
- **`InstitutionSummaryResponseDto`** — přidáno: `convertedTotalBalance`
- **`InstitutionDashboardResponseDto`** — přidáno: `displayAssetCode`, `displayAssetScale`, `grandTotalConverted`

### Upravená služba `InstitutionServiceImpl`

- Dashboard nyní resolvne `preferredDisplayAsset` z trackeru
- Každý holding se obohacuje o konvertovanou hodnotu (`enrichWithConversion`)
- **Start balance** používá kurz z data začátku periody
- **End balance** používá kurz z konce periody (nebo dnešní)
- `totalBalance` na účtu/instituci = součet konvertovaných hodnot

### DB cache strategie

- Historické kurzy (minulé datum) se kešují **navždy** (kurz se nemění)
- Dnešní kurzy se kešují na **6 hodin** (konfigurovatelné `app.market-data.cache-hours`)
- Cache se ukládá s `@Transactional(REQUIRES_NEW)` — funguje i z `readOnly` dashboard transakce

## Konfigurace v `application.properties`

```properties
app.market-data.frankfurter.base-url=https://api.frankfurter.dev/v1
app.market-data.coingecko.base-url=https://api.coingecko.com/api/v3
app.market-data.coingecko.api-key=${COINGECKO_API_KEY:}
app.market-data.cache-hours=6
```

## Co zbývá udělat

1. **Nastavit `preferredDisplayAsset` na expense trackeru** — přes existující update endpoint (tam to pole už je, stačí poslat `preferredDisplayAssetId` v PATCH requestu)
2. **CoinGecko API klíč** (volitelné) — free tier funguje bez klíče (10-30 req/min), ale pro produkci je lepší demo klíč na [coingecko.com](https://www.coingecko.com/en/api)
3. Pokud chceš **přesnější historické kurzy pro jednotlivé transakce** (ne jen start/end periody), můžeš v budoucnu rozšířit `enrichWithConversion` nebo přidat konverzi přímo do `HoldingSummaryBuilderImpl` per-transakce