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
- `convertedTotalBalance` na účtu/instituci je součet dostupných přepočtených hodnot;
  pokud některá hodnota chybí, součet je nedostupný

### DB cache strategie

- Historické kurzy (minulé datum) se kešují **navždy** (kurz se nemění)
- Dnešní kurzy se kešují na **6 hodin** (konfigurovatelné `app.market-data.cache-hours`)
- Živá kotace uložená v daný den se další den znovu načte jako historický denní kurz,
  místo aby byla automaticky považována za historickou kotaci.
- Cache se ukládá s `@Transactional(REQUIRES_NEW)` — funguje i z `readOnly` dashboard transakce

## Výsledek pozic v zobrazovací měně

Dashboard rozlišuje dva údaje:

- **Výsledek období** je koncová hodnota minus počáteční hodnota minus jednotlivé
  přítoky plus jednotlivé odtoky, oceněné v čase pohybu. Počáteční vklad pozice
  založené během období je přítok; zůstatek před založením je nula.
- **Investiční výsledek od počátku** je součet realizovaného a nerealizovaného
  výsledku ke konci zvoleného období, nejpozději k okamžiku výpočtu. Stejné
  aktivum má v rámci trackeru společnou váženou pořizovací cenu. Záporná čistá
  pozice funguje jako short; přechod přes nulu pozici nejprve uzavírá.

Vlastní převody stejného aktiva samy o sobě nejsou prodej; poplatek snižuje
výsledek. Směna proti zobrazovací měně používá skutečně přijatou nebo vydanou
protihodnotu. Ostatní příjmy jsou oceněné vklady a výdaje oceněná vyřazení.
Realizovaný výsledek je přiřazen zdrojové peněžence, nerealizovaný je rozdělen
podle zůstatků. Pokud jsou současně kladné i záporné, používá se umělé
rozdělení podle absolutních hodnot a UI na to upozorňuje.

Výsledky účtu a instituce jsou součtem jejich aktivních zobrazených pozic.
Součet výsledku období na dashboardu také zahrnuje jen aktivní pozice.
Souhrnné přítoky a odtoky v API jsou hrubé součty jednotlivých pozic, takže
zahrnují i převody uvnitř skupiny; při výpočtu výsledku se protistrany vyruší.

U běžného příjmu, výdaje nebo `TEST` je poplatek zadaný v měně částky
transakce. Při jiné měně pozice se částka i poplatek jednotlivě přepočítají
kurzem transakce se zohledněním počtu desetinných míst obou aktiv; u příjmu se
poplatek odečte, u výdaje přičte. Zapsané dopady starých transakcí se nemění
automaticky. Starší cizoměnové transakce a příjmy s poplatkem se označí jako
nejednoznačné; při jejich finanční úpravě nebo výslovném zaškrtnutí přepočtu
v editaci transakce se stav přepočítá novým způsobem a zůstatek pozice se může
změnit, proto je nutné nejprve ověřit původní doklad. Samotná úprava popisu či
jiných nefinančních údajů historický dopad nemění.

Počáteční stav lze ocenit odhadem tržní hodnoty při založení nebo ručně zadanou
pořizovací hodnotou v jiné měně a datu. Tržní hodnota pro výsledek období a
pořizovací hodnota pro investiční výsledek se ukládají odděleně. Staré pozice
s neznámou pořizovací hodnotou je možné opravit v dashboardu; do té doby se
investiční výsledek označí jako neúplný, nikoli nulový.

Přepnutí zobrazovací měny vyvolá nový přepočet historie k datům jednotlivých
událostí. Nové transakce zachovávají dostupný kurz pro tehdejší zobrazovací
měnu a shodné datum transakce; jinak se použije dostupný historický kurz.
V editaci transakce lze také samostatně doplnit kurz mezi oceňovaným aktivem a
aktuální zobrazovací měnou. Tento kurz nemění zůstatky ani skutečný kurz směny;
použije se pouze pro odpovídající pár a datum transakce. Změna data, oceněného
aktiva či zobrazovací měny může vyžadovat nové ocenění. Ruční zadání nahrazuje
uložený kurz pro danou transakci; výsledek stále může obsahovat jiné odhady.
Kurzy CoinGecko a Frankfurter ani čas načtení cache nejsou zárukou přesné
kotace v okamžiku transakce: denní a živé/cache hodnoty jsou proto odhady.
Při chybějícím kurzu nebo přetečení nelze vrátit spolehlivý výsledek.

Do investiční historie se zahrnují i neaktivní pozice. Součet současných
zůstatků na dashboardu naproti tomu zahrnuje jen aktivní hierarchii; obě
hodnoty proto nemusí popisovat stejnou množinu peněženek. Jde o orientační
ekonomický výsledek, nikoli daňový výpočet.

U staré směny bez uložené přijaté částky nelze bezpečně rekonstruovat
zůstatek: dashboard zobrazí chybu s identifikátorem transakce místo odhadu.
Transakci je třeba opravit podle původního dokladu v historii transakcí.
Kontrola nerozpozná všechny možné historické nesoulady uložených zůstatků;
zvláště po opravách starých transakcí je vhodné porovnat zůstatky s doklady.

### Databáze

Před nasazením zálohujte databázi. Aplikace používá Hibernate
`spring.jpa.hibernate.ddl-auto=update`; nová pole ocenění v tabulkách `holding`
a `transaction` jsou nullable a existující řádky se automaticky neoceňují.
Ověřte dokončení aktualizace schématu a cizích klíčů v logu před zpřístupněním
aplikace uživatelům. Aktualizaci na MariaDB je vhodné nejprve vyzkoušet na
kopii vlastní databáze; dosavadní chyby historických zápisů nelze automaticky
poznat ani opravit bez podkladů.

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