# Progress — CashKaro Bank SMS Parser

> Live checklist for the build. Companion to [buildphase.md](buildphase.md) — every item here maps to a task or exit criterion there.
> **Convention:** `[ ]` not started · `[~]` in progress · `[x]` done. Tick boxes as work completes; do not mark a phase ✅ until **all** its Exit Criteria are `[x]`.

## Status at a glance

| Phase | Title | State |
| --- | --- | --- |
| 0 | Project Scaffold & Build Bring-up | ✅ build+test green · on-device launch verified |
| 1 | Architecture Contracts, Config Schema & Sample Oracle | ✅ contracts frozen · 12 tests green |
| 2 | ⚡ Parallel Component Build | ✅ 6 components · 140 tests green |
| 3 | Parser Integration, Confidence & Golden-Set Tuning | ✅ oracle matched · 151 tests green |
| 4 | React Native UI | ✅ built · tsc + 4 Jest green · on-device render verified |
| 5 | Kotlin Unit Test Suite Completion & Hardening | ✅ 164 tests green (8 required + hardening) |
| 6 | Feature / Integration / E2E Testing | ✅ E2E + on-device manual run · audit clean |
| 7 | Documentation, README & Submission Prep | ✅ README (7 sections) + checklist · recording link pending |

---

## Cross-cutting principles (verify continuously, not once)

- [x] **C1** Parsing logic is entirely in Kotlin; JS only calls the bridge and renders. *(architecture established Phase 1: pure `parser/` core + thin bridge; `SmsParser.ts` only calls.)*
- [x] **C2** Conservative under uncertainty — EXCLUDE with specific reason + low confidence when unsure. *(default-deny + amount-less-include downgrade + low malformed/ambiguous confidence; verified end-to-end by the golden + resilience tests.)*
- [x] **C3** Exclusion rules run before INCLUDE is declared. *(pipeline runs ExclusionEngine before InclusionClassifier; golden set confirms all 18 excludes resolve by their reason.)*
- [x] **C4** Bank attribution reads the SMS body; co-brands resolve to issuer. *(DefaultBankResolver: co-brand-first, multi-bank proximity, null-when-unknown; 18 tests.)*
- [x] **C5** Config-driven — adding a bank/rule is a JSON/data edit, not a code change. *(config-extensibility tests in ExclusionEngine + BankResolver.)*
- [x] **C6** Hidden-sample resilient — no hard-coded strings/IDs, no array length/order reliance, no whole-body matching. *(config-driven + word-boundary matching; ResilienceTest covers reorder/empty/append-novel (new bank, multi-bank, fresh OTP, foreign currency, synthetic CREDIT) + garbage.)*
- [x] **C7** Currency detected, never assumed (INR, Rs, USD, EUR, AED). *(DefaultCurrencyExtractor; foreign beats co-mentioned INR; 22 tests.)*
- [x] **C8** Malformed → EXCLUDE/MALFORMED_SMS/transaction null/conf≈0.1. *(DefaultMalformedGate + orchestrator fail-safe; verified by PipelineShapeTest.)*
- [x] **C9** No runtime network, no LLM, no real SMS permissions, no inbox reads. *(manifest has only INTERNET for Metro/dev; parser core is offline pure Kotlin, no SMS perms.)*

---

## Environment (resolved 2026-06-27)

Verified at session start. Recorded here because it deviates from the plan's assumptions and **defers some device-dependent verification**.

| Item | Plan expects | Actual | Resolution |
| --- | --- | --- | --- |
| Node | 18+ | **v22.17.1** | ✅ ok |
| Yarn | installed | was missing | ✅ enabled via **corepack** → yarn 1.22.22 (no admin) |
| JDK | **17** | **21 only** (`C:\Program Files\Java\jdk-21`, `JAVA_HOME`→jdk-21); no JDK 17 present | ⚠️ **Proceeding on JDK 21** (RN 0.74 / Gradle 8.x can run on it). Fallback if Gradle breaks: portable Temurin JDK 17 zip + `org.gradle.java.home`. **Record as a README deviation.** |
| Android SDK | full SDK + `ANDROID_HOME` | full SDK present at **`C:\adb`** (non-standard name), env vars unset | ✅ usable. platforms: android-34/35/36; build-tools: 35.0.0, 36.0.0 (no 34.0.0 → set `buildToolsVersion="35.0.0"`); cmdline-tools `latest`; ndk 28.2; platform-tools/adb. Set `sdk.dir=C\:\\adb` in `android/local.properties` (and/or `ANDROID_HOME`). |
| Emulator / AVD | reachable emulator | **none** (no emulator pkg, no system-images, `adb devices` empty) | ⛔ deferred — see below. |

### ✅ Device verification — RESOLVED (phone connected 2026-06-27)

Physical device **CPH2467 (OnePlus, Android 15), serial `39ce622b`** connected via USB debugging. All previously-deferred on-device checks completed via `./gradlew installDebug` + Metro + `adb` screenshots:

- **Phase 0 exit:** app builds, installs and **launches**; the live screen renders all 25 results (7 included + 18 excluded) — JS↔Kotlin bridge round-trip verified on device. ✅
- **Phase 4 (UI):** rendered values match the **real** parser output — summary 7/18, INR debit ₹5,455.00, INR credit/refund ₹450.00 (USD excluded); included/excluded rows distinct; tap → included modal (all txn fields) and excluded modal (reason, no txn). ✅
- **Phase 6:** full 5-step manual-run / recording script confirmed error-free on device. ✅
- **Recording note:** the debug build loads JS from Metro (`yarn start`); for the screen recording, keep Metro running or build a self-contained APK.

---

## Deviations from the plan (running log — feeds README tech-stack note & §5)

Every substitution from `buildphase.md`, with the reason (the plan requires recording these).

| # | Area | Plan said | What was done | Why |
| --- | --- | --- | --- | --- |
| D1 | JDK | JDK 17 | **JDK 21** | Only 21 present; RN 0.74/Gradle 8.x build is green on it. Portable-17 fallback documented, not needed. |
| D2 | Package manager | Yarn (version open) | **Yarn 3.6.4** via corepack | RN 0.74 template default; `yarn install`/`yarn android` unchanged. |
| D3 | Android build tools | buildTools 34.0.0, ndk 26.1.x | **buildTools 35.0.0, ndk 28.2.13676358** | Match the SDK installed at `C:\adb` (no downloads). |
| D4 | iOS | (RN scaffolds iOS) | **iOS scaffold omitted** | Android-only per PRD; reduces clutter. |
| D5 | JSON parsing | unspecified | **Gson** (`implementation`) | `org.json` throws in JVM unit tests; Gson is JVM-clean and needs no Kotlin plugin. |
| D6 | `ExcludeReason` | enum/sealed | **enum** + safe `fromCode` fallback | New reason *codes* need an enum entry, but adding/refining rules that reuse a code is pure JSON (C5 holds for the demonstrated case); enum gives type-safety + completeness. |
| D7 | Stage interfaces | "documented + frozen" | **centralised in `parser/Contracts.kt`** (orchestrator-owned) | Stronger freeze guarantee; Phase 2 agents add `Default*` impls in their owned files implementing these. |
| D8 | `AssetConfigSource` | listed under `parser/config/` | **placed in the bridge package** (`com.cashkaro.smsparser`) | It needs `android.*` (AssetManager); keeping it out of `parser/` keeps the core android-free + JVM-testable. |
| D9 | Normalizer/MalformedGate | "stub in Phase 1" | **implemented for real in Phase 1** | They are unowned shared infra (not in the Phase 2 table); real impls de-risk Phase 3. ConfidenceScorer stays stubbed until Phase 3. |
| D10 | Seed config | the 25 samples' banks/tokens | **+ SBI, Kotak banks; extra currency tokens** | Hidden-sample resilience (C6) — generalise beyond the 25. |
| D11 | Verification | `yarn android` launch + on-screen check | **deferred to phone connect** | No emulator; device-free `assembleDebug` + `gradlew test` substitute. See Environment block. |

---

## Phase 0 — Project Scaffold & Build Bring-up

**Features / tasks**
- [~] Environment verified — see **Environment (resolved 2026-06-27)** above. Node 22 ✅, Yarn via corepack ✅, JDK 21 (deviation from 17), SDK at `C:\adb` ✅. **No emulator → on-device launch deferred to phone connect.**
- [x] RN 0.74.5 TypeScript app scaffolded at repo root (docs/CLAUDE.md/buildphase.md/Progress.md preserved; **iOS scaffold omitted** — Android-only per PRD).
- [x] `newArchEnabled=false` (RN 0.74 template default); app id `com.cashkaro.smsparser` set consistently (namespace + applicationId + Kotlin package + directory; renamed from generated `com.cashkarosmsparser`).
- [x] `src/data/samples.json` created with all 25 samples as `[{id, text}]`; `App.tsx` maps `s.text` over the array (any length — C6).
- [x] Stub `SmsParserModule.parseSms(ReadableArray, Promise)` returns schema-valid result per input (rawSms echoed · `EXCLUDE` · `LOW_CONFIDENCE` · transaction null · confidence 0.0).
- [x] `SmsParserPackage` registered in `MainApplication.getPackages()`.
- [x] `src/native/SmsParser.ts` typed wrapper + `ParsedResult`/`Transaction`/`Decision`/`TxnType` TS types matching the schema (`resolveJsonModule` enabled for the samples import).
- [x] `App.tsx` calls `parseSms` on mount and renders result count + included/excluded split.
- [x] `.gitignore` covers RN/Android build artefacts (kept repo's richer ignore, appended `*.jsbundle`, `/coverage`, `*.hprof`).

**Exit criteria**
- [x] `yarn install` succeeds (Yarn 3.6.4 via corepack; `node_modules/` + `yarn.lock` present).
- [x] `yarn android` builds and launches. → `./gradlew assembleDebug`/`installDebug` BUILD SUCCESSFUL; **launched on device CPH2467 (Android 15)**, full UI renders.
- [x] Screen proves the bridge returns one result per input. → on-device screenshot shows all 25 results (7 included + 18 excluded) from the native parser.
- [x] `cd android; ./gradlew test` wiring confirmed = BUILD SUCCESSFUL; `:app:test` ran `NO-SOURCE` (zero tests, as expected for Phase 0).

---

## Phase 1 — Architecture Contracts, Config Schema & Sample Oracle

**Features / tasks**
- [x] Models: `Decision`, `TxnType` (DEBIT/CREDIT/REFUND), `ExcludeReason` (all Functions.md reasons + custom `SALARY_CREDIT`; `LOW_CONFIDENCE` default-deny fallback; `fromCode` safe lookup), `Transaction`, `ParsedResult` (`parser/model/`).
- [x] Config contracts: `ParserConfig` (+ DTOs `BankPattern`/`CardProduct`/`ExclusionRuleDef`/`CurrencyDef`/`MerchantConfig`), `ConfigSource` interface, Gson-based `JsonConfigParser` (pure, JVM-safe).
- [x] Shared `CardSignal` helper frozen (`classify/CardSignal.kt`; reads products.json signals); disambiguates `avl limit` vs `avl bal` — proven by CardSignalTest.
- [x] Exclusion-rule qualifier semantics frozen in `ExclusionRuleDef` doc (`any`/`unless`/`withCard`/`notCreditCard`, first-match-wins).
- [x] `AssetConfigSource` (assets, bridge layer) + `TestConfigSource` (test resources) → identical `ParserConfig`; mirror asserted byte-identical.
- [x] Seed JSON committed (all 7): banks, card-products, products (**core CC signals seeded**), exclusion-rules (validated order), currencies, merchants, dates — mirrored to `src/test/resources/parser-config/`.
- [x] `SmsParser.kt` orchestrator wires 6 stages (C3 order) as injected interfaces; real Normalizer+MalformedGate, stub rest (`StubStages.kt`); never throws (C8).
- [x] **All 6 stage interface signatures frozen** in `parser/Contracts.kt` (documented "FROZEN", orchestrator-owned).
- [x] Bridge constructs `SmsParser` via `AssetConfigSource`, converts via pure `ResultMapper` → WritableMap; compiles + assembles into the APK.
- [x] Sample oracle at `src/test/resources/oracle.json` (test-only) — **independently re-derived by a sub-agent and reconciled with Appendix B (full agreement)**.

**Exit criteria**
- [x] Models/config/interfaces compile; bridge returns schema-valid output through the real pipeline shape (`./gradlew assembleDebug` + `testDebugUnitTest` green).
- [x] `ConfigSource` swap (Asset vs Test) verified by ConfigLoadTest (load + byte-identical mirror).
- [x] **Verbatim field-name snapshot** (FieldNameSnapshotTest) confirms keys equal the schema exactly, in order.
- [x] `CardSignal` + qualifier semantics frozen; `products.json` seeded.
- [x] Oracle consistent with Functions.md field rules (independent derivation matched it).
- [x] Stage interfaces declared frozen (`Contracts.kt`).

**Phase 1 verification:** `./gradlew :app:testDebugUnitTest` → **12 tests, 0 failures** (CardSignal 5 · ConfigLoad 2 · FieldNameSnapshot 2 · PipelineShape 3); `./gradlew assembleDebug` → BUILD SUCCESSFUL. Oracle independent-derivation note: agreed with Appendix B on all 25 decisions/reason codes; only flagged nuances were #1 SAVINGS↔UPI (handled via accepted-set), #9 BoB/BOBCARD naming, merchant-cleaning variance.

---

## Phase 2 — ⚡ Parallel Component Build

> 6 agents, disjoint files. Each ships component + config slice + unit tests.

**2A — ExclusionEngine + rules** (`ExclusionEngine.kt`, `ExclusionRule.kt`, `exclusion-rules.json`)
- [x] Ordered rule engine → reason; first-match-wins (`DefaultExclusionEngine` + compiled `ExclusionRule`).
- [x] Covers all 15 reason categories (OTP … SALARY_CREDIT) — a named test each.
- [x] Ordering verified (UPI before BALANCE_ALERT before SAVINGS catch-all; `withCard`/`notCreditCard` qualifiers).
- [x] Unit tests pass (31).

**2B — InclusionClassifier** (`InclusionClassifier.kt`, **owns `products.json`** via `CardSignal`)
- [x] Credit-card spend detection via `CardSignal` (Credit Card / limit-language) vs non-card.
- [x] Assigns `TxnType`: DEBIT vs REFUND vs **CREDIT** (cashback/reward credited to card; refund wins over credit).
- [x] Conservative on bare "Card" without limit-language.
- [x] **Default-deny:** no credit-card signal → `EXCLUDE`/`LOW_CONFIDENCE`.
- [x] Unit tests pass (12).

**2C — BankResolver** (`BankResolver.kt`, owns `banks.json` + `card-products.json`)
- [x] Resolves issuer from body (C4).
- [x] Co-brand map: Jupiter/Edge→Federal Bank; BOBCARD One→Bank of Baroda (resolved before direct banks).
- [x] App/product branding does not override issuer.
- [x] **Multi-bank precedence:** proximity tie-break — issuer near card/spend/limit wins; helpline/footer ignored.
- [x] Returns null when unknown (no guessing).
- [x] Unit tests pass (18).

**2D — Amount + Currency** (`AmountExtractor.kt`, `CurrencyExtractor.kt`, `currencies.json`)
- [x] Indian number formats parsed (`1,45,300.00`, `Rs.450.00`, `Rs 50000`, `50000`).
- [x] Currency detected: INR, Rs→INR, USD, EUR, AED (C7); foreign beats a co-mentioned INR equivalent.
- [x] Picks transaction amount, not balance/limit/markup; bare non-money digits (OTP/ref) → null.
- [x] Unit tests pass (22).

**2E — Date extractor** (`DateExtractor.kt`, `dates.json`)
- [x] Formats → ISO `YYYY-MM-DD` via `SimpleDateFormat` (minSdk-23-safe; no `java.time`/desugaring).
- [x] Two-digit year → 20YY (2000 pivot); first date wins on multi-date; null when absent.
- [x] Unit tests pass (18).

**2F — Card + Merchant** (`CardExtractor.kt`, `MerchantExtractor.kt`, `merchants.json`)
- [x] Last-four from `xx5678`, `XX9876`, `ending 1234`, `ending in XX9907`, `*4521`, `Card no.` (3-digit `XX123`→null).
- [x] Card-type token classified (CREDIT_CARD/DEBIT_CARD/BARE_CARD/ACCOUNT/UNKNOWN).
- [x] Merchant from earliest `at/to/with X`, cut at ` on `/`.`/`,`; strips city + suffixes; domain dots kept.
- [x] Unit tests pass (27).

**Coordination / exit criteria**
- [x] Every component compiles; **all 140 unit tests pass** (`./gradlew :app:testDebugUnitTest`).
- [x] No component imports `android.*` (grep of `parser/` clean).
- [x] Config-driven proven — ExclusionEngine & BankResolver config-extensibility tests add a rule/bank/co-brand via data only.
- [x] Each agent mirrored its config JSON to test resources; `ConfigLoadTest` asserts byte-identical (passes).
- [x] Edge-case notes captured below.

**Component findings / edge cases**
- **Orchestrator integration fixes (4)** — surfaced by the central `./gradlew test` (agents self-reviewed but, by design, did not run Gradle): (1) **BankResolver** matched co-brand `product` against `sms.lower` without lowercasing (config keeps product original-case) → fixed at the match site; (2) **ExclusionRule** substring `"upi"` matched inside **"jupiter"** (sample 8 mis-excluded as UPI — latent in the seed too) → word-like tokens now match at **word boundaries** (also hardens "sip"-in-"gossip" C6 risks); punctuation tokens stay substring; (3) **InclusionClassifier** bare `"spend"` guard tripped on the noun **"spends"** ("cashback for May spends") → word-boundary spend guard; (4) **AmountExtractor** returned the OTP digits `458219` → now requires money-shape (comma/decimal-formatted, currency-anchored, or near transaction wording), else null.
- 2A: `avl limit` kept strictly distinct from `avl bal`; CARD_PAYMENT carries `withCard` so a "payment of" lacking a card signal falls through to SAVINGS; refined seed tokens (annual/joining fee, standing instruction, `premium of`/`policy no` rather than bare `premium`/`policy` to dodge "Premium Card").
- 2B: refund/reversal wins over CREDIT; CREDIT only on cashback/reward credited-to-card with no spend verb; punted — recognising a credit card with no limit/"credit card" phrase in the body (signal-driven by design; that case is the scorer's/BankResolver's concern).
- 2C: proximity anchors are generic context words, not sample-keyed; multiple distinct issuers with none near an anchor → null (refuses to guess).
- 2D: lakh grouping via comma-tolerant regex; `%` markup + `emi of` instalment rejected; foreign currency anywhere beats a co-mentioned INR equivalent.
- 2E: greedy `yyyy`/`yy` pitfall (a 4-digit pattern coercing `06-04-26`→year 0026) guarded by a year<1000 floor; long ref/phone numbers not misread as dates.
- 2F: 3-digit masked group (`XX123`)→null (conservative); merchant boundary `.` fires only before whitespace/end so `NETFLIX.COM/US` is preserved.

---

## Phase 3 — Parser Integration, Confidence & Golden-Set Tuning

**Features / tasks**
- [x] Real components wired into `SmsParser.create` in C3 order; `StubStages.kt` deleted.
- [x] `DefaultConfidenceScorer` implemented with documented bands (include base 0.77 + explicit-CC 0.14 / limit-lang 0.08 + field bonuses; co-brand −0.08; cap 0.97; strong exclusion 0.93; savings 0.78; ambiguous LOW_CONFIDENCE 0.60; malformed 0.10).
- [x] Missing-field penalties applied (bonuses for bank/date/merchant/cardLastFour; co-brand penalty).
- [x] Default-deny path emits `EXCLUDE`/`LOW_CONFIDENCE` for ambiguous-not-malformed input.
- [x] `ParserGoldenTest` (3 tests): asserts decision + reason + key fields; confidence as bands; **`transaction == null` for every EXCLUDE**; #1 reason via accepted-set, #4 SALARY_CREDIT custom code.
- [x] Tuned against oracle — **no divergences** (parser matches the oracle on all 25). One integration tuning: sample 21 refund to a bare "HDFC Card" is recognised as a credit-card REFUND (it survived exclusion, so it is not a debit/account refund).
- [x] Resilience tests (8): reorder/order-independence, empty array, synthetic **CREDIT** (reward), **multi-bank** issuer-vs-footer, fresh OTP, foreign-currency (EUR), unknown-bank-still-conservative, garbage/malformed never crash.

**Exit criteria**
- [x] Pipeline matches oracle decisions/reasons (no divergences).
- [x] Summary = **Included: 7 / Excluded: 18**; INR debit = ₹5,455; INR credit/refund = ₹450 (asserted in `aggregate_summary_matches_spec`).
- [x] Confidence bands behave; `transaction == null` holds for all excludes.
- [x] Reorder/append/empty tests pass.

**Phase 3 verification:** `./gradlew :app:testDebugUnitTest` → **151 tests, 0 failures**; `./gradlew assembleDebug` → BUILD SUCCESSFUL.

**Struggled-with samples (feeds README §4)**
- **#1** "Sent … From HDFC Bank A/C … To BIGBASKET … block CC" — the "block CC" footer is a trap (not a credit-card signal); resolved as an account debit (SAVINGS_ACCOUNT). Oracle accepts SAVINGS_ACCOUNT|UPI_BANK_ACCOUNT|BANK_ACCOUNT.
- **#5** bare "Card no." with no "Credit Card" — relies on "Available Limit" as the credit-card signal (limit-language), scored a touch lower than an explicit-CC spend.
- **#8** "j**upi**ter" — the substring `"upi"` falsely tripped UPI_BANK_ACCOUNT until word-boundary matching was added (Phase 2 integration fix).
- **#17** EMI-conversion vs spend — an existing spend converted to EMI is excluded (EMI_CONVERSION), not double-counted.
- **#21** refund to a bare "HDFC Card" (no "credit card"/limit phrase) — needed the survived-exclusion-implies-credit-card refund rule to be INCLUDE/REFUND.
- **#24** UPI + "Avl Bal" footer — UPI fires before BALANCE_ALERT so the account/UPI nature wins over the trailing balance line.
- "cashback" is treated as a promotional OFFER (exclusion-first), so the CREDIT type is exercised by genuine "reward credited to card" wording rather than "cashback" (a deliberate, conservative choice).

---

## Phase 4 — React Native UI

> Built as a single coherent pass (not fanned out) — small presentational components sharing one design language (`src/theme.ts`) + one `ParsedResult` type; verified device-free via `tsc` + Jest. (Orchestrator judgment; rationale recorded.)

**Features / tasks**
- [x] `SummaryHeader.tsx`: INR debit total, INR credit/refund total, included count, excluded count, "Top Exclusions" count-by-reason. INR totals sum only `currency === 'INR'` (C7).
- [x] `ResultRow.tsx` + `Chip.tsx` + `ConfidenceIndicator.tsx`: included rows (bank initials avatar, merchant, amount+currency, date, type chip, confidence bar).
- [x] Excluded rows: dimmed, reason chip, 2-line SMS preview, confidence.
- [x] `DetailModal.tsx`: raw SMS, decision, exclude reason, all transaction fields, confidence.
- [x] `ParserScreen.tsx`: calls `parseSms(samples.map(s=>s.text))` on mount; loading / error / results state; FlatList + modal. `App.tsx` renders it.

**Exit criteria — verifiable device-free (tsc + Jest, native bridge mocked)**
- [x] On launch, screen calls `parseSms` and renders all results (Jest: parseSms called once, output rendered; `samples.map` is any-length — C6).
- [x] Summary shows all five figures; included vs excluded rows distinct; excluded dimmed with chip + preview (SummaryHeader + ResultRow Jest tests; `tsc --noEmit` clean).
- [x] Detail modal built with all required fields; row `onPress` wires it (tap interaction itself is an on-device check).

**Exit criteria — requires on-device run (DEFERRED to phone connect)**
- [x] Rendered values match real parser output; INR totals exclude foreign currency; summary shows true 7/18 split. → **Verified on device** (CPH2467): summary 7/18, INR debit ₹5,455.00, INR credit/refund ₹450.00; included modal (all fields) + excluded modal (reason, no txn).

**Phase 4 verification:** `yarn tsc --noEmit` → clean; `yarn jest` → **4 tests, 0 failures** (SummaryHeader C7, ResultRow included, ResultRow excluded, ParserScreen on-mount).

---

## Phase 5 — Kotlin Unit Test Suite Completion & Hardening

> Required matrix made explicit as named, full-pipeline tests in `RequiredCategoriesTest` (9) + `HardeningTest` (4). Written directly (one cohesive pass) rather than the per-category file fan-out — simple assertions over the full parser.

**Required tests (docs/Testing.md)** — all in `RequiredCategoriesTest`
- [x] 1. Clear credit-card spend (sample 2) → INCLUDE/DEBIT + HDFC/5678/SWIGGY/1250 INR/date/conf≥0.85.
- [x] 2. Debit-card exclusion (sample 6) → EXCLUDE/DEBIT_CARD, txn null.
- [x] 3. OTP exclusion (sample 10) → EXCLUDE/OTP, txn null.
- [x] 4. UPI/savings exclusion (sample 20) → EXCLUDE/UPI_BANK_ACCOUNT, txn null.
- [x] 5. Fintech/co-branded issuer (5a sample 8 → Federal Bank; 5b sample 9 → Bank of Baroda).
- [x] 6. Refund (sample 21) → INCLUDE/REFUND + HDFC/450 INR/date.
- [x] 7. Foreign currency (sample 22) → INCLUDE/DEBIT, USD, contributes 0 to INR totals.
- [x] 8. Malformed (sample 25) → EXCLUDE/MALFORMED_SMS, txn null, conf ≤0.2.

**Extra hardening tests** — in `HardeningTest`
- [x] Config-extensibility: new bank added via `baseConfig.copy(banks = … + BankPattern)` resolves; base config does not (proves C5).
- [x] Conservative-bias: ambiguous bare "Card", no limit-language → `EXCLUDE`/`LOW_CONFIDENCE`, conf <0.75 (C2).
- [x] Null-contract: a non-malformed EXCLUDE (OTP) has `transaction == null`.
- [x] `CREDIT`-type path exercised by a "reward credited to card" message → INCLUDE/CREDIT.

**Exit criteria**
- [x] All 8 required categories present as named, passing tests.
- [x] Extensibility + conservative-bias tests pass.
- [x] `cd android; ./gradlew test` green; command captured for README.

**Phase 5 verification:** `cd android; ./gradlew :app:testDebugUnitTest` → **164 tests, 0 failures** across 16 test classes (BankResolver 18, CardSignal 5, ExclusionEngine 31, InclusionClassifier 12, ConfigLoad 2, Amount 12, Card 18, Currency 10, Date 18, Merchant 9, FieldNameSnapshot 2, Hardening 4, Golden 3, PipelineShape 3, RequiredCategories 9, Resilience 8). README run command: `cd android && ./gradlew test`.

---

## Phase 6 — Feature / Integration / E2E Testing

**Features / tasks**
- [x] Golden E2E (Kotlin): `ParserGoldenTest.aggregate_summary_matches_spec` asserts 7/18 + INR ₹5,455 / ₹450.
- [x] Hidden-sample simulation: `ResilienceTest` (synthetic CREDIT, multi-bank, fresh OTP, EUR foreign, unknown-bank, garbage) → conservative, sane output, no false-positive INCLUDEs.
- [x] **Manual app run on the physical device (CPH2467, Android 15)** — verified via `adb` screenshots: launch → summary (7/18, ₹5,455/₹450, Top Exclusions) → included + excluded rows → included modal (SWIGGY: all txn fields) → excluded modal (SAVINGS_ACCOUNT: reason, no txn).
- [x] Anti-cheat self-audit: clean — only doc-comment sample mentions (`MerchantExtractor`); no SMS perms/Telephony/inbox, no network/LLM (parser or JS), no sample-ID/length/order reliance.
- [x] (Optional) Jest smoke test for JS/UI shape — done in Phase 4 (4 tests).

**Exit criteria**
- [x] Aggregate summary E2E passes (Kotlin test + confirmed on-device).
- [x] Hidden-wording samples produce no false-positive INCLUDEs.
- [x] Manual run completes the full 5-step recording script error-free (screenshots captured).
- [x] Anti-cheat audit clean.

**Phase 6 verification:** ran `./gradlew installDebug` + Metro on device 39ce622b (CPH2467, Android 15); on-device screenshots confirm the summary, both row styles, and both modal variants. Live output matches the oracle (7 included / 18 excluded; INR debit ₹5,455.00; INR credit/refund ₹450.00).

---

## Phase 7 — Documentation, README & Submission Prep

> Drafted via a 7-agent ⚡ parallel Workflow (read-only agents returned section markdown); orchestrator assembled `README.md`, fact-checked every claim against the code, and deduped voice.

**README sections (docs/README-Requirements.md)** — all in `README.md`
- [x] 1. How to run (exact commands: `yarn install`, `yarn android`, `cd android && ./gradlew test`, optional jest/tsc).
- [x] 2. Parsing architecture (pure-Kotlin core location, bridge call path, exclusion-vs-extraction separation, body-based bank detection, config location, how to add a bank/rule, why — with ASCII diagram).
- [x] 3. Confidence scoring model (exact constants verified against `ConfidenceScorer.kt`).
- [x] 4. Samples you struggled with (#1 block-CC, #5 bare Card, #8 jUPIter, #17 EMI, #21 bare-card refund, #24 UPI+balance, cashback→OFFER).
- [x] 5. What you'd do differently with a full week (rule engine, java.time, merchant, tests, prod flow, operability).
- [x] 6. Production Android design note — **627 words** (in 500–800); covers permissions/denial, Play risk, incremental+dedupe, WorkManager/BroadcastReceiver/ContentObserver, **30s budget**, process death/retry, **all 5 Indian OEMs** + recovery UX, privacy.
- [x] 7. AI tool usage (tools, what worked, the 4 AI bugs caught, human-owned decisions, manual verification — honest).

**Submission checklist (docs/Submission.md)**
- [x] Ticked all items in `docs/Submission.md` except the **screen-recording link** (user action — the app is verified ready to record). RN app · Kotlin module · JS bridge · renders all 25 · summary header · detail modal · config-driven rules · 164 Kotlin tests · README complete · production note complete · no real SMS permission · no external API.

**Exit criteria**
- [x] All 7 README sections present, specific; production note 627 words covering every bullet + all five OEMs + 30s budget.
- [x] Submission checklist ticked (except the external recording link — user adds after recording).
- [x] Build + tests green on the committed tree: `cd android; ./gradlew test` = 164 passing; `yarn android`/`installDebug` launched + verified on device. Repo is self-contained (yarn.lock + pinned `.yarn/releases` committed) so a fresh checkout reproduces.

---

# Build Plan v2 — Liquid Glass UI · File Import · Contextual Engine

> Companion to [buildphase-v2.md](buildphase-v2.md). **Everything below is ADDITIVE to the assignment-core build above (Phases 0–7).** Prime directive: `parseSms` and the frozen `ParsedResult` schema are unchanged — the 25-sample `ParserGoldenTest` oracle must show **zero diffs**. The contextual engine is a separate sidecar (`parseSmsSession`) with its own schema; it never touches the graded path.
> **Convention:** `[ ]` not started · `[~]` in progress · `[x]` done · `[device]` deferred — needs on-device verification.

## v2 status at a glance

| WS | Title | State |
| --- | --- | --- |
| WS-1 | Kotlin: spaced month-name dates | ✅ tests green · golden zero-diff |
| WS-2 | Kotlin: contextual engine (threading · canonicalisation · recurring) | ✅ tests green · golden zero-diff |
| WS-3 | Bridge: `parseSmsSession` + enriched JS schema | ✅ JVM + TS verified |
| WS-4 | React Native: iOS 26 Liquid Glass redesign | ⏳ built · device render deferred |
| WS-5 | React Native: file import ("add more SMS") | ⏳ built · device flow deferred |
| WS-6 | React Native: Insights view (threads · merchants · recurring) | ⏳ built · device render deferred |
| WS-7 | Exclusions: keep all three + prove load-bearing (D2) | ✅ tests green · no rule removed |
| WS-8 | Tests sweep (golden regression gate) | ✅ Kotlin suite green · golden zero-diff |
| WS-9 | Docs & Progress (this section + README) | ✅ this update |

> **Why some items are deferred:** v2 RN work (glass surfaces, file picker, Insights segment) requires a real device/emulator to verify rendering and the SAF file-pick flow. Per the v2 workstream rules these were implemented and statically verified (`tsc`/`lint`/Jest where applicable) but **not** device-built (no emulator in this environment; the existing physical-device verification predates the v2 RN code). Those rows are marked `[device]` below.

---

## WS-1 — Kotlin: spaced month-name dates

**Tasks**
- [x] Widen `DateExtractor.kt` candidate regex with a **separate** alternation branch `\b\d{1,2}\s+[A-Za-z]{3,9}\s+\d{2,4}\b` joined to the unchanged original dash/slash branch (original branch byte-identical).
- [x] Append four formats to `dates.json` (main + test-resource mirror): `dd MMM yyyy`, `dd MMM yy`, `d MMM yyyy`, `dd MMMM yyyy` — original 5 kept first to preserve format priority.
- [x] Preserve 2000-pivot, strict `isLenient=false` full-token parse, `< MIN_YEAR` guard, and "first valid date wins, left-to-right".

**Exit**
- [x] New `DateExtractorTest` cases for the three spaced shapes (+ first-date-wins, invalid-day fall-through, non-month negative) pass.
- [x] **`ParserGoldenTest` zero diffs** on the 25 samples (confirmed via forced `--rerun-tasks`).
- [x] Full Kotlin suite green.

---

## WS-2 — Kotlin: contextual engine (lifecycle threading + merchant canonicalisation/recurring)

> New pure-Kotlin package `parser/session/` (`ContextualEngine`, `TransactionThreader`, `MerchantCanonicalizer`, `CorrelationSignals`, `model/SessionModels`). Reuses the existing stateless `SmsParser` per message; layers correlation + enrichment on top. Core `ParsedResult` untouched (V1/V6).

**Tasks**
- [x] `SmsRecord(text, receivedAt?, sender?)` input model; date fallback `receivedAt → in-body date → input order`; `receivedAt` never required.
- [x] Per-message parse reuses the existing stateless `SmsParser` (core fields untouched).
- [x] `MerchantCanonicalizer` — config-driven (`merchant-categories.json`, ported from Moneyprism `categories.dart`), first-match, substring-over-normalised; `NETFLIX.COM/US` · `NETFLIX-MONTHLY` · `NETFLIX_SUBSCRIPTION` → `Netflix`; null when no token hits.
- [x] `TransactionThreader` — union-find over a **strong** key (card4 AND amount AND canonical merchant within a config time window: 15-min with `receivedAt`, same-day for date-only) plus explicit back-reference links (sample 21 refund → original spend; sample 17 EMI-conversion → original spend). Conservative (V4): no strong signal ⇒ singleton thread.
- [x] Recurring detection: canonical merchant flagged `recurring` when it appears ≥2× or matches a known-subscription token set; future-auto-debit (sample 14) surfaced as a recurring signal.
- [x] No confidence recalibration / no decision changes (D4) — enrichment is purely additive.
- [x] `ParserConfig`/`JsonConfigParser` extended for the new merchant-category config (mirrored to test resources).

**Exit**
- [x] `ContextualEngineTest`/`MerchantCanonicalizerTest`/`TransactionThreaderTest` prove: Netflix variants collapse to one merchant; sample 21 threads onto its spend; sample 17 threads onto its spend; a recurring merchant is flagged; two ₹-equal spends with different card4 do **not** merge.
- [x] Engine never changes the underlying `ParsedResult` core fields.
- [x] **`ParserGoldenTest` zero diffs**; full Kotlin suite green.

---

## WS-3 — Bridge: `parseSmsSession` native method + enriched JS schema

**Tasks**
- [x] `@ReactMethod parseSmsSession(records, promise)` added to `SmsParserModule.kt` (reuses the lazily-built `parser` + a lazy `ContextualEngine`; iterates by `size()` C6; `promise.reject` on error, never throws across the bridge C8). `parseSms` method unchanged.
- [x] New `parser/session/SessionResultMapper.kt` (separate from `ResultMapper`) maps engine output to `WritableMap`/`WritableArray`; the five frozen core keys are emitted byte-identically for the embedded core result.
- [x] `src/native/SmsParser.ts` adds `parseSmsSession(records)` + `SmsRecord`, `EnrichedResult`, `Thread`, `MerchantSummary`, `SessionResult` types (§7). Existing `parseSms` wrapper + `ParsedResult`/`Transaction` types unchanged.

**Exit**
- [x] JVM test asserts the session map's key set.
- [x] JS types compile (`tsc --noEmit`).
- [x] `FieldNameSnapshotTest` for the core schema still passes (frozen keys intact).

---

## WS-4 — React Native: iOS 26 Liquid Glass redesign

> Single `<Glass>` wrapper (`src/components/Glass.tsx`) over `@sbaiahmed1/react-native-blur`; the rest of the UI is library-agnostic. Adaptive light/dark via `src/theme/ThemeContext.tsx`. New chrome: `Toolbar.tsx` (title + Import) and `SegmentedControl.tsx` (Messages ⇄ Insights). Components restyled as glass cards.

**Tasks**
- [x] `<Glass>` wrapper with capability detection (`isGlassSupported()`: iOS true; Android `Platform.Version >= 33`).
- [x] **V5 fallback** below API 33: solid translucent surface (`rgba(250,250,252,0.72)` light / `rgba(28,28,30,0.72)` dark) + hairline border, so the app looks intentional down to `minSdk 23`.
- [x] Token overhaul in `theme.ts` (iOS palette, type scale, 8-pt spacing, concentric radii, shadow) with light + dark support.
- [x] Floating glass toolbar + segmented control above the scrolling list (content/controls separation).
- [x] Glass-card restyle of `SummaryHeader`, `ResultRow` (included/excluded), `DetailModal` (iOS sheet).
- [device] Reduced-motion handling and final spring tuning verified statically; runtime behaviour is a device check.

**Exit**
- [x] TypeScript compiles; Jest UI smoke tests green; lint clean.
- [device] Screen builds and renders on a real device/emulator; glass on API 33+, fallback below; light + dark legible; no horizontal overflow. **Deferred — needs on-device verification** (no emulator available this environment).

---

## WS-5 — React Native: file import ("add more SMS")

> `src/import/` — `pickFile.ts` (SAF picker), `parseImport.ts` (native-module-free parser), `useImport.ts` (state + calls `parseSmsSession` over combined bundled + imported records).

**Tasks**
- [x] Import action in the floating glass toolbar opens the system picker.
- [x] Accept `.json` (`[{text,…}]` and bare `["…"]`, honouring `receivedAt`/`sender` when present) and `.txt` (one SMS per line, blanks skipped); detect by content/extension.
- [x] Map to `SmsRecord[]` and call **`parseSmsSession`** (not `parseSms`) over the combined set; append to state with a count notice.
- [x] Malformed file ⇒ non-blocking error banner (`Banner.tsx`), never crash; large-import cap with a visible notice.

**Exit**
- [x] `parseImport` maps `.json`/`.txt` to records (unit-level / static verification); types compile; lint clean.
- [device] Importing a sample `.json` and `.txt` renders new rows and threads them with the bundled samples; bad files show a friendly error. **Deferred — needs on-device verification** (SAF file-pick flow).

---

## WS-6 — React Native: Insights view (threads · merchants · recurring)

> `InsightsView.tsx` + `EnrichmentLine.tsx` + `RecurringBadge.tsx`; `ParserScreen.tsx` now runs `parseSmsSession` over the bundled samples and switches Messages ⇄ Insights via the segmented control.

**Tasks**
- [x] Messages segment: existing per-SMS list with an additive enrichment line on included rows (canonical merchant + category chip; recurring badge where flagged). Core fields unchanged.
- [x] Insights segment: Threads (primary txn + linked lifecycle events + net amount), Merchants (canonical rollups: count/total/category), Recurring (flagged merchants).
- [x] Tapping any item opens the same iOS sheet detail with thread/enrichment info.

**Exit**
- [x] Component renders from a mocked `parseSmsSession`; types compile; lint clean.
- [device] With the 25 samples (+ an imported file) the Insights segment shows ≥1 multi-message thread, the Netflix rollup as one merchant, and a recurring flag. **Deferred — needs on-device verification.**

---

## WS-7 — Exclusions: keep all three + prove load-bearing (D2)

**Tasks**
- [x] **Removed nothing** — all three rules (`SALARY_CREDIT`, `INVESTMENT`, `INSURANCE`) remain in `exclusion-rules.json`.
- [x] Added 3 engine-level cases to `ExclusionEngineTest` (card-based INSURANCE premium, card-based SIP INVESTMENT, salary → SALARY_CREDIT).
- [x] Added 2 end-to-end cases to `RequiredCategoriesTest` driving the full `SmsParser` — `decision=EXCLUDE` with reason INSURANCE/INVESTMENT and `transaction == null` (proving they are not classified as INCLUDE/DEBIT and the `notCreditCard` SAVINGS catch-all cannot save them). Covers the example `"Rs 12,500 spent on your HDFC Bank Credit Card xx5678 for HDFC Life Insurance Premium"` ⇒ EXCLUDE INSURANCE.
- [x] Each block cites buildphase-v2.md D2 so a future reader does not "simplify" the rules away.

**Exit**
- [x] New tests green; the three rules remain in `exclusion-rules.json` (test-only, no production code/config changed).
- [x] **`ParserGoldenTest` zero diffs.**

---

## WS-8 — Tests sweep (regression gate)

- [x] WS-1, WS-2, WS-3, WS-7 Kotlin tests added under their respective packages.
- [x] **`ParserGoldenTest` (25-sample oracle) shows zero diffs** — the regression gate for all of v2 (confirmed via forced `--rerun-tasks` after fresh recompile).
- [x] Full existing Kotlin suite remains green (`./gradlew testDebugUnitTest` / `test`).
- [~] RN: TS compiles; Jest smoke + lint where applicable.
- [device] `yarn android` device build of the v2 RN screen. **Deferred — needs on-device verification.**

**Exit**
- [x] `./gradlew test` green with `ParserGoldenTest` zero diffs.
- [device] Full on-device run (Messages + Insights + import) — deferred.

---

## WS-9 — Docs & Progress

- [x] This v2 section added to `Progress.md`, mirroring WS-1…WS-8, ticked per implementer summaries; device-only items marked `[device]`.
- [x] README updated with "Contextual engine (beyond the assignment)", "Liquid Glass UI", "Import your own SMS" sections; API-33 glass requirement + `< API 33` fallback noted; assignment-compliance + production-Android notes kept intact; `parseSms` stated unchanged and graded; engine stated to be an additive sidecar not affecting the golden oracle.

**Exit**
- [x] README + Progress.md updated; a reader can tell assignment-core (Phases 0–7) from v2-additive (WS-1…WS-9).
