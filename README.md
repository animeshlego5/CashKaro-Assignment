# CashKaro — Bank SMS Parser

A React Native (Android) app with a **native Kotlin** module that parses Indian bank SMS messages into structured credit-card transactions. The parser is **conservative** (when unsure it excludes with a specific reason and low confidence — avoiding false positives matters more than catching every spend) and **config-driven** (banks, products, exclusion rules, currencies and date formats live in JSON, so adding a bank or rule is a data edit, not a code change).

All parsing, classification, bank detection and confidence scoring happen in **pure Kotlin**; the JavaScript layer only calls the native bridge and renders.

**Tech stack:** React Native 0.74.5 (TypeScript) · Kotlin classic bridge (`newArchEnabled=false`) · Yarn 3.6.4 (corepack) · JUnit4 + Gson. Built and verified on JDK 21 (the plan targeted JDK 17), Android `compileSdk 34` / `build-tools 35.0.0` / `minSdk 23`.

**Status:** over the 25 provided samples the parser produces **7 INCLUDE / 18 EXCLUDE** (INR debit ₹5,455 · INR credit/refund ₹450 · the USD sample excluded from INR totals). **164 Kotlin unit tests pass**, and the app was verified on a physical device (OnePlus, Android 15).

**Screen recording:** _<add link to the ≤2-minute walkthrough here>_

**Download APK:** [CashKaro-v1.0.0.apk](https://github.com/animeshlego5/CashKaro-Assignment/releases/tag/v1.0.0) — install on Android 6.0+ (enable *Install from unknown sources*)

> **Assignment-core vs. v2-additive.** Sections **1–7** below document the **graded assignment**: the pure-Kotlin parser, the `parseSms` contract, and the React Native screen. **Section 8 ("Beyond the assignment")** documents a **v2 layer built additively on top** — an iOS-26 "Liquid Glass" UI redesign, a runtime SMS file-import flow, and a stateful **contextual engine**. None of it changes the graded path: **`parseSms` is byte-for-byte unchanged and remains the graded entry point**, the frozen `ParsedResult` schema is untouched, and the 25-sample golden oracle (`ParserGoldenTest`) still shows **zero diffs**. The contextual engine is a separate sidecar method (`parseSmsSession`) with its own schema — it never feeds the graded contract. If you only care about the assignment, read 1–7 and stop.

---

## 1. How to run

**Prerequisites**

- **Node 18+** and **Yarn 3.6.4** (enable via `corepack enable` — the version is pinned in `package.json`).
- **JDK 17+** (built and verified on JDK 21).
- **Android SDK** (compileSdk 34, build-tools 35.0.0, minSdk 23) with a connected device or running emulator.

**Install dependencies**

```bash
yarn install          # installs JS deps; native libs resolve during the Gradle build
```

**Run on Android** (app id `com.cashkaro.smsparser`)

```bash
yarn android          # debug build + install; auto-launches Metro and loads JS from it
```

`yarn android` starts the Metro dev server for you. If it isn't already running, keep it up in a separate terminal:

```bash
yarn start            # Metro JS bundler (leave running)
```

**Run the required Kotlin parser unit tests** (164 JUnit4 tests, JVM-only — no emulator needed)

```bash
cd android && ./gradlew test        # macOS/Linux
cd android; ./gradlew test          # Windows PowerShell
```

**Optional checks**

```bash
yarn jest             # JS/UI smoke tests (also aliased as `yarn test`)
yarn tsc --noEmit     # TypeScript type-check
```

---

## 2. Parsing architecture

All parsing, classification, bank detection, and scoring live in a **pure-Kotlin core** under `android/app/src/main/java/com/cashkaro/smsparser/parser/`. That core has **zero `android.*` imports**, so the whole pipeline runs on the plain JVM and is covered by 164 JUnit tests with no emulator. `SmsParser.kt` is the orchestrator; its `create(config)` factory wires the stages as injected interfaces (`Contracts.kt`), which keeps every stage independently testable and swappable.

### Call path

```
JS:  src/native/SmsParser.ts  ──▶ NativeModules.SmsParser.parseSms(string[])
        │  (typed wrapper; JS only calls + renders)
        ▼
KT:  SmsParserModule.kt  (classic RN bridge, @ReactMethod, Promise)
        │  AssetConfigSource → JsonConfigParser → ParserConfig (loaded once, lazy)
        ▼
     SmsParser.parseAll(...)  ── pure core ──┐
        │                                     │
        ▼                                     │
     WritableArray ◀── ResultMapper ──────────┘  (back to JS)
```

`SmsParserModule` (with `AssetConfigSource`) is the **only** Android-aware layer: it reads config from assets, runs the pure parser, and transcribes results to a `WritableArray`. It does no parsing of its own.

### Pipeline (C3: exclusion-first)

```
normalize → malformed gate → ExclusionEngine → InclusionClassifier
          → [extract: amount, currency, date, card, merchant, bank] → ConfidenceScorer
```

**Exclusion is fully separated from extraction.** The config-driven `DefaultExclusionEngine` runs first and returns the *first matching* ordered rule; if anything matches, the SMS is excluded with that reason and **no extractor ever runs**. Extraction executes only for `INCLUDE` candidates — and even then, a missing amount or currency downgrades to `EXCLUDE/LOW_CONFIDENCE` rather than emitting junk. Keeping conservative filtering isolated and ordered means false positives are cheap to prevent and easy to reason about.

### Bank detection

`DefaultBankResolver` reads the **SMS body, never the sender**. It tries **co-brands first** via `card-products.json` (e.g. Jupiter/Edge → Federal Bank, BOBCARD One → Bank of Baroda), then falls back to direct issuer patterns in `banks.json`. When several banks appear, it uses **proximity to the transaction** as a tie-break, and returns `null` when nothing is confident.

### Config & extensibility (no code change)

All rules live in `android/app/src/main/assets/parser-config/*.json` (`banks`, `card-products`, `products`, `exclusion-rules`, `currencies`, `merchants`, `dates`), mirrored to `src/test/resources/parser-config/` and loaded once through `ConfigSource`/`JsonConfigParser` into `ParserConfig`.

- **Add a bank:** append `{ "canonical": "...", "patterns": ["..."] }` to `banks.json` and mirror to test resources.
- **Add an exclusion rule:** append an ordered rule to `exclusion-rules.json` with qualifiers — `any` (trigger phrases), `unless` (suppressors), `withCard` / `notCreditCard` (gate on `CardSignal`).

This shape is deliberate: dependency injection plus `ConfigSource` makes the core JVM-testable; exclusion is isolated so conservative filtering stays ordered and auditable; config-driven rules let us adapt to hidden samples without touching parser code; and the shared `CardSignal` helper gives the exclusion engine and inclusion classifier one consistent definition of "credit-card-ness."

---

## 3. Confidence scoring

Every result carries a `confidence` in `[0, 1]` produced by `DefaultConfidenceScorer` (`confidence/ConfidenceScorer.kt`). It is a calibrated heuristic, not a probability: the scorer maps the decision plus a small `Signals` struct to an additive score, then clamps it. Tests assert **bands**, never exact equality.

**INCLUDE.** Scoring starts at a base of `0.77` and adds bonuses for evidence. The dominant signal is *how* the card was recognised:

- Literal `"Credit Card"` text adds `+0.14` (the strong, unambiguous case).
- A bare `"Card"` recognised only via limit-language (available-limit / forex-markup phrasing) adds just `+0.08` — credible, but weaker.

Field completeness layers on small bonuses: resolved bank `+0.03`, date `+0.02`, merchant `+0.02`, card last-four `+0.02`. So an explicit credit-card spend with all fields present lands near the `INCLUDE_CAP` of `0.97` (typically ~0.90–0.97); a limit-language inclusion with the same fields sits around ~0.85–0.93. A **co-brand-resolved issuer** (e.g. Jupiter→Federal, BOBCARD→Bank of Baroda) subtracts `−0.08`, since body-driven issuer resolution is the least-certain step. Missing bank/date/merchant/card simply forgo their bonuses, pulling the score down.

**EXCLUDE.** Confidence reflects how cleanly the exclusion fired:

- Keyword-strong exclusions (OTP, debit card, UPI, balance alert, bill-due, offer, declined, fee, card-payment) → `0.93`.
- Softer/generic reasons: savings catch-all `0.78`, EMI `0.86`, salary/auto-debit/insurance `0.90`, investment `0.92`.
- Ambiguous **default-deny** — a bare `"Card"` with no credit-card signal — yields `LOW_CONFIDENCE` at `0.60`, the deliberate low-confidence exclusion that keeps the parser conservative.

**MALFORMED.** As a C8 fail-safe, a malformed SMS short-circuits to `0.10` with `transaction=null` before any other scoring runs.

---

## 4. Samples you struggled with

The sample set is deliberately adversarial. A few cases forced the parser's design rather than just its config:

- **Sample 1 — the "block CC" trap.** *"Sent ... From HDFC Bank A/C ... To BIGBASKET ... To block CC ..."* The trailing fraud-helpline footer mentions "CC", which naively reads as a credit-card signal. But the body is an account-to-merchant debit, so we resolve it as an account spend, not a card spend. The oracle accepts `SAVINGS_ACCOUNT | UPI_BANK_ACCOUNT | BANK_ACCOUNT` here; the card-signal logic must ignore footer noise.

- **Sample 5 — bare "Card no." with no "Credit Card".** There is no literal "Credit Card" string, so the credit-card signal comes from limit-language: the presence of **"Available Limit"**. This is a genuine card spend, but because the signal is indirect it scores a touch lower than an explicit-CC message.

- **Sample 8 — "jUPIter".** The substring `upi` lives inside the co-brand name **jUPIter**, which falsely tripped the `UPI_BANK_ACCOUNT` exclusion. Fixed by switching token matching from substring to **word-boundary** matching (a Phase 2 integration fix) so `upi` only matches as a standalone word.

- **Sample 17 — EMI conversion.** An *existing* spend "converted to EMI" is not a new transaction. Including it would double-count the original purchase, so it is excluded with `EMI_CONVERSION`.

- **Sample 21 — refund to a bare "HDFC Card".** No "credit card" or limit phrase, just "Card". The rule: a refund that survives exclusion (so it is not a debit or account refund) and references a card is a credit-card `REFUND` → INCLUDE.

- **Sample 24 — UPI debit with an "Avl Bal" footer.** Both UPI and balance-alert signals are present. Because the pipeline runs UPI before `BALANCE_ALERT`, the account/UPI nature correctly wins over the trailing balance line.

**Deliberate choice:** "cashback" is treated as a promotional `OFFER` (excluded), so the `CREDIT` type is exercised only by genuine "reward credited to card" wording — a conservative call that avoids inflating spend.

---

## 5. What I would do differently with a full week

The prototype is deliberately conservative and config-driven, but a few corners were cut under time. In rough priority order:

**1. A richer rule engine.** Today the `ExclusionEngine` is pure first-match-wins over config-ordered rules — correct but brittle. I'd add per-rule **priority and scoring** (highest-confidence match wins, not first), mix **regex and token rules** in one schema, and ship **per-rule unit fixtures** so each rule carries its own labelled SMS that proves what it catches and what it must not.

**2. Stronger date parsing.** `DefaultDateExtractor` uses `SimpleDateFormat` purely to dodge core-library desugaring at minSdk 23. With a week I'd enable **desugaring and move to `java.time`** (`LocalDate`/`DateTimeFormatter`), add **relative dates** ("today", "yesterday") and **timezone**-aware handling.

**3. More robust merchant extraction.** Merchant cleanup is best-effort and explicitly not graded. I'd add a **known-merchant dictionary**, light **NER-style normalization** (drop boilerplate, canonicalize casing), and broaden **currency/locale** coverage beyond the single USD case.

**4. Far wider test coverage.** Beyond the 164 unit tests, I'd build a **labelled corpus**, add **property-based and fuzz tests** (malformed input must always EXCLUDE/`MALFORMED_SMS`), and expand **hidden-sample** breadth.

**5. The real production flow.** Wire up `RECEIVE_SMS`/`READ_SMS` permissions and a **background broadcast-receiver parse path** (per the production note below) instead of the demo paste box.

**6. Operability.** Add **telemetry for parser misses**, a **confidence-calibration feedback loop**, and proper **config management** — versioned/remote config, JSON-schema validation, and a small admin tool so adding a bank or rule stays a non-engineer edit.

---

## 6. Production Android design note

This prototype is a pure, synchronous Kotlin core: `SmsParserModule.parseSms(string[])` feeds the 25 sample strings through the pipeline. Shipping it to read **real** SMS on a device is a design problem, not a parser problem — the parser stays exactly as is. The notes below are design only.

### Permissions and graceful degradation
Reading inbox history needs `READ_SMS`; catching live messages needs `RECEIVE_SMS`. Both are runtime (dangerous) permissions, requested with a rationale screen before the system dialog. **If denied, we never block the app.** We fall back to a manual path: paste-an-SMS, share-sheet **forwarding** (the user shares a bank SMS into us), or simply disabling the real-time feature while the rest of the app works. The denial state is sticky and re-promptable from settings.

### Google Play policy risk
This is the real risk. Play restricts `READ_SMS`/`RECEIVE_SMS` to apps that are the user's **default SMS handler** or fit a narrow approved-use list, gated by a Permissions Declaration Form. A reward/comparison app is **likely rejected**, so we design for the deny path first: **user-initiated import** (manual paste/forward, fully compliant), or a **NotificationListenerService** that reads bank notification text instead of SMS (no SMS permission, but fragile — depends on the bank posting notifications and on notification format). RCS/transaction APIs are emerging but not yet a reliable substitute. We ship the compliant import path as primary and treat SMS read as an enhancement requested only where eligible.

### Incremental processing and dedupe
We never re-scan the whole inbox. We keep a **high-water mark** (last-processed `_id`/`date`) and query the SMS `ContentResolver` only for rows newer than it. Each message gets an **idempotency key = hash(address, body, timestamp)** stored in a local dedupe table, so the same SMS is parsed at most once even if a re-scan and a live event overlap.

### Background execution trade-offs
- **BroadcastReceiver on `SMS_RECEIVED`** — lowest latency, real-time, but receiver execution is short-lived and constrained.
- **WorkManager** — reliable and battery-friendly, but has a minimum-latency floor and is deferrable by Doze, so it **cannot** reliably hit a tight budget.
- **ContentObserver** — catches inbox changes while our process is alive, but is weak once the process is killed.

### Meeting the 30-second budget
For a notification like *"You used HDFC for grocery; ICICI would have earned ₹50 more,"* the only path that **realistically** meets 30s is a high-priority `SMS_RECEIVED` **BroadcastReceiver** that immediately hands off to a **foreground/expedited service**, with the **parser running in-process on that receiver/service path** — no round trip, no deferral. Plain WorkManager, periodic jobs, or anything subject to Doze/app-standby **cannot** reliably meet 30s. The parser must live **on the live ingestion path**, not behind a deferrable queue.

### Process death and retry
The watermark and a **durable queue** are persisted to disk. On restart we replay anything unprocessed (at-least-once), and **idempotent dedupe** makes replays safe. An **expedited WorkManager** job is the reliability backstop that sweeps anything the live receiver missed.

### Indian OEM background-kill
Aggressive OEM battery managers will silently kill us. Mechanisms span autostart restrictions, app-standby buckets, Doze, and OEM "app lock"/background limits:
- **Xiaomi/MIUI/HyperOS** — autostart toggle + battery-saver "no restrictions" + lock in recents.
- **Realme** and **OPPO/ColorOS** — Startup Manager autostart + allow background activity.
- **Vivo/FuntouchOS** — autostart + high-background-power-consumption allowance.
- **OnePlus/OxygenOS** — "don't optimise" battery + advanced auto-launch (verified on a OnePlus, Android 15).

We **detect** kills via a periodic heartbeat / missed-work check, then **deep-link** users into the exact per-OEM autostart/battery screen with illustrated steps, **explain** why (real-time rewards need us alive), and surface a **status indicator**: "Real-time updates may be delayed — fix in settings."

### Privacy and local processing
Everything parses **fully on-device**. No SMS content ever leaves the phone; the parser makes **no network calls**. We keep **minimal retention** (the watermark, dedupe hashes, and derived transactions only), **encrypt at rest**, and are transparent about exactly what is read and stored.

---

## 7. AI tool usage

This project was built with **Claude Code** (an agentic CLI) acting as a build **orchestrator** that executed a phased plan: scaffold → freeze architecture contracts + build the sample oracle → a 6-way **parallel fan-out** of sub-agents for the parser components → central integration + confidence tuning → React Native UI → test hardening → on-device verification → docs. The orchestrator spawned sub-agents for the parallel work, then integrated and verified everything centrally.

**What worked well.** Freezing the stage interfaces (`Contracts.kt`) *first* let six component agents (normalizer/gate, exclusion, classifier, extractors, bank, scorer) build in parallel without stepping on each other. The expected-output **oracle** for the 25 samples was derived by an *independent* agent and reconciled against the orchestrator's reading — a cross-check, not a single trusted pass. Crucially, the sub-agents did **not** run Gradle; the full suite was run centrally after the fan-out, which caught real integration bugs the agents missed.

**Bugs AI introduced, caught by review + central tests.** (1) the substring `"upi"` matched inside *"jupiter"* and falsely tripped a UPI-account exclusion (fixed with word-boundary matching); (2) co-brand product names were matched against lowercased text without lowercasing; (3) the noun *"spends"* tripped a bare-`spend` guard and vetoed a legitimate CREDIT; (4) an OTP's digits were returned as an amount (fixed by requiring a money-shape). A refund to a bare "HDFC Card" (sample 21) also needed a hand-added rule.

**What was human-owned.** AI wrote the bulk of the Kotlin/TS under direction; the architecture, the frozen contracts, the confidence-model tuning, the four integration fixes, and the sample-21 / cashback judgment calls were directed and verified by the human-in-the-loop.

**Verified manually/objectively.** Re-ran 164 Kotlin tests + `assembleDebug` + `tsc` + Jest; verified on a physical device via screenshots (summary 7/18, both modal types); ran an anti-cheat grep — **no hard-coded sample strings in logic, no SMS permissions, no network/LLM calls**. Every part of the code is explainable; nothing is a hard-coded sample hack.

---

# 8. Beyond the assignment (v2-additive)

Everything in sections 1–7 is the graded assignment and is unchanged. This section documents a **v2 layer built strictly on top** of it. The guiding rule throughout was **additive-only**: no v2 change may alter `parseSms` output for the 25 samples. The 25-sample golden oracle (`ParserGoldenTest`) is the regression gate and shows **zero diffs** after all v2 work. The full v2 plan and per-workstream checklist live in [buildphase-v2.md](buildphase-v2.md) and the **Build Plan v2** section of [Progress.md](Progress.md).

**What stays frozen (the assignment contract):**

- `parseSms(samples: string[]): ParsedResult[]` — the pure, stateless, 1:1, order-independent graded entry point — is **byte-for-byte unchanged**.
- The frozen `ParsedResult` schema and `ResultMapper`'s five core keys are **untouched** (`FieldNameSnapshotTest` still passes).
- All new knowledge stays **config-driven JSON** under `android/app/src/main/assets/parser-config/`; all new logic stays in **Kotlin** (RN only calls the bridge and renders).

## 8.1 Contextual engine

A pure-Kotlin sidecar (`parser/session/`) exposed as a **new** native method `parseSmsSession` — never replacing `parseSms`. It reuses the unchanged stateless parser per message, then layers three enrichments on top:

- **Merchant canonicalisation** (`MerchantCanonicalizer`, config-driven via `merchant-categories.json`): variant spellings collapse to one canonical name + category; `null` when no token hits.
- **Lifecycle threading** (`TransactionThreader`): groups messages by strong signature (`cardLastFour` + amount + canonical merchant within a time window). No strong match → standalone thread of one. Explicit back-references (refund "against original txn", EMI conversion) link to their originating spend.
- **Recurring detection:** a merchant is flagged `recurring` when it appears ≥2× across threads or matches a known-subscription token set.

Enrichment fields (`threadId`, `merchantCanonical`, `category`, `recurring`, `linkedTo`, `receivedAt`) are added in a **separate schema** (`SessionResult`) mapped by a separate `SessionResultMapper`. The core `ParsedResult` and `ResultMapper` are byte-identical to the graded path.

## 8.3 Import your own SMS

An **Import** action opens the Android system file picker (SAF — no extra permissions) and appends messages to the rendered set. Accepts `.json` (`[{ "text": "..." }]` or bare string array, optional `receivedAt`/`sender`) and `.txt` (one SMS per line). Imported records are fed to `parseSmsSession` over the combined set. Malformed files show a non-blocking error banner; very large imports are capped with a visible notice.
