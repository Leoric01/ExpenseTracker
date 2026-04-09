# Food & Weight Tracking — Dokumentace

## TL;DR — Kompletní endpoint mapa

### Onboarding (jednorázový setup)

| # | Endpoint | Účel |
|---|----------|------|
| 1 | `PUT /api/nutrition-profile/{trackerId}` | Nastavit profil (pohlaví, výška, aktivita) |
| 2 | `POST /api/goal-plan/{trackerId}` | Založit plán (cut/bulk/maintenance) — **automaticky vygeneruje initial nutrition target** |
| 3 | `GET /api/nutrition-target/{trackerId}/current` | Zobrazit doporučené kalorie a makra |

### Denní rutina

| # | Endpoint | Účel |
|---|----------|------|
| 4 | `PUT /api/daily-checkin/{trackerId}` | **Unified daily check-in** — váha, kalorie, makra + volitelně obvody (waist/neck/hip) v jednom callu |
| 5 | `GET /api/nutrition-dashboard/{trackerId}` | Dashboard — grafy, trendy, aktuální stav |

> Pokud potřebuješ logovat nutrition a body measurements zvlášť, staré endpointy fungují dál:
> - `PUT /api/daily-nutrition-log/{trackerId}`
> - `PUT /api/daily-body-measurement-log/{trackerId}`

### Týdenní check-in

| # | Endpoint | Účel |
|---|----------|------|
| 6 | `POST /api/weekly-checkin/{trackerId}/{goalPlanId}/current` | Vygenerovat weekly checkin pro aktuální týden (backend dopočítá weekIndex) |
| 7 | `GET /api/nutrition-target/{trackerId}/current` | Zkontrolovat, jestli se target změnil (adaptive adjustment od 4. týdne) |

> Alternativně: `POST /api/weekly-checkin/{trackerId}/{goalPlanId}/current?date=2026-04-09` pro konkrétní datum.
> Legacy endpoint `POST /api/weekly-checkin/{trackerId}/{goalPlanId}/{weekIndex}` zůstává pro zpětnou kompatibilitu.

### Změna režimu

| # | Endpoint | Účel |
|---|----------|------|
| 8 | `POST /api/goal-plan/{trackerId}` | Založit nový plán (nový cut/bulk) — automaticky vygeneruje initial target |
| 9 | pokračovat denní rutinou | — |

### Advanced

| Endpoint | Účel |
|----------|------|
| `POST /api/nutrition-target/{trackerId}/{goalPlanId}/manual-override` | Ruční přepsání targetu (trenér, vlastní režim) |
| `POST /api/nutrition-target/{trackerId}/{goalPlanId}/initial` | Explicitní přegenerování initial targetu (např. po velké změně plánu) |

---

## Architektura — datový model

### NutritionProfile (1:1 na tracker)
Základní antropometrie a nastavení. Jeden tracker = jedna „nutriční identita".
- jednotky, pohlaví, výška, activity multiplier, auto BF výpočet

### GoalPlan (N na tracker, max 1 aktivní)
Časová vrstva — každý nový cíl (cut → maintenance → bulk) = nový plán. Nemodifikovat starý plán donekonečna, jinak se zničí historie.
- typ cíle, start/end date, start weight, BF, weekly target, macro strategie

### NutritionTarget (verzovaný snapshot)
Doporučení kalorií a maker v čase. Auditovatelná historie — vidíš, co systém kdy doporučoval.
- baseline TDEE, calorie adjustment, target calories, P/F/C, algorithm version

### DailyNutritionLog + DailyBodyMeasurementLog (oddělené záměrně)
Denní záznamy. Dvě tabulky, protože mají jiný životní cyklus (kalorie loguješ denně, obvody ob týden).
- nutrition: datum, váha, kalorie, makra
- body: datum, obvody (waist/neck/hip), dopočítané BF%

### WeeklyCheckin (materializovaný agregát)
Týdenní snapshot z denních dat. Uložený, ne počítaný on-demand — stabilní pro historii algoritmu.
- avg váha, avg kalorie, BF%, weight change, estimated TDEE

### TdeeAdjustmentEvent (audit)
Záznam každé adaptivní korekce. Jádro celé aplikace — vidíš co systém změnil a proč.
- previous/new target, observed vs expected change, applied adjustment

---

## Výpočetní logika

Interně všechno v **kg a cm**. Odpovídá Google Sheetu.

| Výpočet | Metoda |
|---------|--------|
| BMR | Katch-McArdle (370 + 21.6 × LBM) |
| Baseline TDEE | BMR × activity multiplier |
| Daily calorie adjustment | weekly weight change × 7700 / 7 |
| Target calories | baseline TDEE + daily adjustment |
| Protein | heuristika podle BF% (lean/mid/high) |
| Fat | heuristika podle BF% (lean/higher) |
| Carbs | zbytek kalorií |
| BF% z obvodů | US Navy rovnice |
| Adaptive adjustment | od 4. checkinu, observed vs expected weekly change |

### Co se snapshotuje vs dopočítává

**Snapshotovat (uložit):**
- NutritionTarget, WeeklyCheckin, TdeeAdjustmentEvent
- BF% při uložení DailyBodyMeasurementLog

**Dopočítávat on-demand:**
- acceptable macro ranges, warningy, chart projections, dashboard summary, BF preview

---

## Service boundaries

| Service | Odpovědnost |
|---------|-------------|
| `NutritionProfileService` | CRUD profilu, validace antropometrie |
| `GoalPlanService` | CRUD plánů, max 1 active, **auto-generování initial target při create** |
| `NutritionTargetService` | Orchestrace — natáhne profil/plán, zavolá calculation services, uloží snapshot |
| `DailyNutritionLogService` | Upsert denního logu, vazba na aktivní GoalPlan |
| `DailyBodyMeasurementLogService` | Upsert obvodů, BF výpočet přes BodyFatCalculationService |
| `DailyCheckinService` | **Unified endpoint** — deleguje na oba log services v jedné transakci |
| `WeeklyCheckinService` | Sestavení týdenního snapshotu, adaptive recalculation po 4. týdnu |
| `NutritionDashboardService` | Read-only summary, grafy, timeline |

> `NutritionTargetService` **nepočítá** TDEE ani makra sám. Volá `TdeeCalculationService` a `MacroCalculationService`. Matematika je oddělená od orchestrace.

---

## Kompletní API reference

### NutritionProfile

| Method | Endpoint | Popis |
|--------|----------|-------|
| `PUT` | `/api/nutrition-profile/{trackerId}` | Upsert profilu (jednotky, pohlaví, výška, activity multiplier, auto BF) |
| `GET` | `/api/nutrition-profile/{trackerId}` | Načíst aktuální profil |

### GoalPlan

| Method | Endpoint | Popis |
|--------|----------|-------|
| `POST` | `/api/goal-plan/{trackerId}` | Založit plán + **auto-generate initial nutrition target** (vrácen v response jako `initialNutritionTarget`) |
| `GET` | `/api/goal-plan/{trackerId}` | Seznam plánů (`?search=` volitelný) |
| `GET` | `/api/goal-plan/{trackerId}/{goalPlanId}` | Detail plánu |
| `PATCH` | `/api/goal-plan/{trackerId}/{goalPlanId}` | Upravit plán |
| `POST` | `/api/goal-plan/{trackerId}/{goalPlanId}/activate` | Aktivovat plán |
| `DELETE` | `/api/goal-plan/{trackerId}/{goalPlanId}` | Deaktivovat plán (soft delete) |

### NutritionTarget

| Method | Endpoint | Popis |
|--------|----------|-------|
| `POST` | `/api/nutrition-target/{trackerId}/{goalPlanId}/initial` | Explicitní přegenerování initial targetu |
| `GET` | `/api/nutrition-target/{trackerId}/current` | Aktuální platný target |
| `GET` | `/api/nutrition-target/{trackerId}` | Historie všech targetů (pageable) |
| `POST` | `/api/nutrition-target/{trackerId}/{goalPlanId}/manual-override` | Ruční override targetu |

### Daily Check-in (unified)

| Method | Endpoint | Popis |
|--------|----------|-------|
| `PUT` | `/api/daily-checkin/{trackerId}` | Unified daily log — váha, kalorie, makra, poznámka + volitelně waist/neck/hip. Pokud jsou obvody `null`, body measurement se přeskočí. |

### Daily Nutrition Log (standalone)

| Method | Endpoint | Popis |
|--------|----------|-------|
| `PUT` | `/api/daily-nutrition-log/{trackerId}` | Upsert denního nutrition logu |
| `GET` | `/api/daily-nutrition-log/{trackerId}/{logDate}` | Log pro konkrétní den |
| `GET` | `/api/daily-nutrition-log/{trackerId}` | Seznam logů (`?from=&to=` volitelné, pageable) |
| `DELETE` | `/api/daily-nutrition-log/{trackerId}/{logDate}` | Smazat log |

### Daily Body Measurement Log (standalone)

| Method | Endpoint | Popis |
|--------|----------|-------|
| `PUT` | `/api/daily-body-measurement-log/{trackerId}` | Upsert obvodů + auto BF% výpočet |
| `GET` | `/api/daily-body-measurement-log/{trackerId}/{logDate}` | Measurement pro konkrétní den |
| `GET` | `/api/daily-body-measurement-log/{trackerId}` | Seznam measurements (`?from=&to=` volitelné, pageable) |
| `DELETE` | `/api/daily-body-measurement-log/{trackerId}/{logDate}` | Smazat measurement |

### Weekly Check-in

| Method | Endpoint | Popis |
|--------|----------|-------|
| `POST` | `/api/weekly-checkin/{trackerId}/{goalPlanId}/current` | Vygenerovat checkin pro aktuální týden (`?date=` volitelné, default dnes). Backend dopočítá weekIndex. |
| `POST` | `/api/weekly-checkin/{trackerId}/{goalPlanId}/{weekIndex}` | Vygenerovat checkin pro konkrétní weekIndex (legacy) |
| `GET` | `/api/weekly-checkin/{trackerId}/{goalPlanId}/{weekIndex}` | Detail checkinu |
| `GET` | `/api/weekly-checkin/{trackerId}/{goalPlanId}` | Všechny checkiny plánu (pageable) |

### Dashboard

| Method | Endpoint | Popis |
|--------|----------|-------|
| `GET` | `/api/nutrition-dashboard/{trackerId}` | Dashboard — current target, latest checkin, weight/calorie timeline (`?from=&to=`, default 90 dní) |
| `GET` | `/api/nutrition-dashboard/{trackerId}/{goalPlanId}/summary` | Summary plánu — týdenní a měsíční přehledy |

---

## User flows

### A) První setup
```
1. PUT  /api/nutrition-profile/{trackerId}          → nastavit profil
2. POST /api/goal-plan/{trackerId}                  → založit plán (auto-generuje initial target)
3. GET  /api/nutrition-target/{trackerId}/current    → zobrazit doporučení
```

### B) Denní používání
```
1. GET  /api/nutrition-target/{trackerId}/current    → kolik mám jíst
2. PUT  /api/daily-checkin/{trackerId}               → zapsat váhu, kalorie, makra (+ volitelně obvody)
3. GET  /api/nutrition-dashboard/{trackerId}          → aktualizovaný dashboard
```

### C) Konec týdne
```
1. POST /api/weekly-checkin/{trackerId}/{goalPlanId}/current  → vygenerovat weekly snapshot
   → od 4. týdne může automaticky vzniknout nový target (adaptive adjustment)
2. GET  /api/nutrition-target/{trackerId}/current              → zkontrolovat nový target
```

### D) Změna režimu (cut → maintenance → bulk)
```
1. POST /api/goal-plan/{trackerId}                  → založit nový plán (auto-generuje initial target)
2. pokračovat denní rutinou
```

### E) Ruční override targetu
```
1. POST /api/nutrition-target/{trackerId}/{goalPlanId}/manual-override  → vlastní kalorie/makra
2. GET  /api/nutrition-target/{trackerId}/current                        → zobrazit override
```

---

## Poznámky k implementaci

- **GoalPlan create automaticky generuje initial NutritionTarget.** Není potřeba volat `/initial` endpoint zvlášť. Response obsahuje pole `initialNutritionTarget`.
- **Weekly checkin `/current`** — frontend nemusí znát weekIndex. Stačí zavolat endpoint a backend si weekIndex dopočítá ze startDate plánu. Volitelně lze zadat `?date=`.
- **Daily checkin** je convenience endpoint. Interně deleguje na `DailyNutritionLogService` + `DailyBodyMeasurementLogService`. Standalone endpointy zůstávají pro případy, kdy chceš logovat/mazat zvlášť.
- **Adaptive adjustment** probíhá automaticky při generování weekly checkinu od 4. týdne. Pokud je aktuální target `manualOverride`, adjustment se přeskočí.
- **DELETE na GoalPlan** je soft delete (deaktivace), ne fyzické smazání. Historie zůstává.