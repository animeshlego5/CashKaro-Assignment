# Progress — CashKaro Bank SMS Parser

> Live checklist for the build. Companion to [buildphase.md](buildphase.md) — every item here maps to a task or exit criterion there.
> **Convention:** `[ ]` not started · `[~]` in progress · `[x]` done. Tick boxes as work completes; do not mark a phase ✅ until **all** its Exit Criteria are `[x]`.

## Status at a glance

| Phase | Title | State |
| --- | --- | --- |
| 0 | Project Scaffold & Build Bring-up | ✅ build+test green · on-device launch deferred |
| 1 | Architecture Contracts, Config Schema & Sample Oracle | ✅ contracts frozen · 12 tests green |
| 2 | ⚡ Parallel Component Build | ☐ |
| 3 | Parser Integration, Confidence & Golden-Set Tuning | ☐ |
| 4 | React Native UI | ☐ |
| 5 | Kotlin Unit Test Suite Completion & Hardening | ☐ |
| 6 | Feature / Integration / E2E Testing | ☐ |
| 7 | Documentation, README & Submission Prep | ☐ |

---

## Cross-cutting principles (verify continuously, not once)

- [x] **C1** Parsing logic is entirely in Kotlin; JS only calls the bridge and renders. *(architecture established Phase 1: pure `parser/` core + thin bridge; `SmsParser.ts` only calls.)*
- [~] **C2** Conservative under uncertainty — EXCLUDE with specific reason + low confidence when unsure. *(orchestrator default-denies + downgrades amount-less includes; full verification in Phase 2/3.)*
- [~] **C3** Exclusion rules run before INCLUDE is declared. *(pipeline order enforced in `SmsParser`; real engine in Phase 2.)*
- [ ] **C4** Bank attribution reads the SMS body; co-brands resolve to issuer (Jupiter/Edge→Federal, BOBCARD→BoB).
- [ ] **C5** Config-driven — adding a bank/rule is a JSON edit, not a code change.
- [ ] **C6** Hidden-sample resilient — no hard-coded strings/IDs, no array length/order reliance, no whole-body matching.
- [ ] **C7** Currency detected, never assumed (INR, Rs, USD, EUR, AED).
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

### ⛔ Deferred verification — requires the user's physical Android phone (connect later)

The user has a physical Android phone but will connect it for a later testing phase. Everything that needs a **running device** is deferred until then; everything JVM-side proceeds now.

- **Phase 0 exit:** `yarn android` *launch* + "25 results received" on screen → **deferred**. Substitute now: verify the app **compiles** via `./gradlew assembleDebug` (no device needed). `./gradlew test` wiring is verifiable now.
- **Phase 4 (UI):** "requires Phase 3 green / real device" exit criteria → **deferred** to device connect; build UI against stub then real parser output now.
- **Phase 6:** manual app run / 5-step recording script → **deferred** to device connect.
- **When the phone is connected:** enable USB debugging, `adb devices` should list it, then run `yarn android` and complete the deferred checks above before ticking those phases truly done.

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
- [~] `yarn android` builds and launches. → **Build verified** via `./gradlew assembleDebug` = BUILD SUCCESSFUL (5m1s on JDK 21), `app-debug.apk` produced (123 MB). **Launch deferred** to phone connect (see Environment block).
- [~] Screen proves the bridge returns one result per input. → **Deferred** to phone connect; `App.tsx` renders "N results received" — verified on device later.
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
- [ ] Ordered rule engine → reason; first-match-wins.
- [ ] Covers: OTP, DEBIT_CARD, SAVINGS_ACCOUNT, UPI_BANK_ACCOUNT, BALANCE_ALERT, BILL_DUE, OFFER, FUTURE_AUTO_DEBIT, DECLINED, EMI_CONVERSION, FEE_OR_CHARGE, CARD_PAYMENT, INVESTMENT, INSURANCE, SALARY_CREDIT.
- [ ] Ordering verified (future auto-debit on a credit card ⇒ FUTURE_AUTO_DEBIT, not spend).
- [ ] Unit tests pass.

**2B — InclusionClassifier** (`InclusionClassifier.kt`, **owns `products.json`** via `CardSignal`)
- [ ] Credit-card spend detection via signal tokens (Credit Card / limit-language) vs non-card (Debit Card / A/C / UPI).
- [ ] Assigns `TxnType`: DEBIT (spend) vs REFUND (reversal) vs **CREDIT** (relevant non-refund credit-card credit, distinct from excluded `CARD_PAYMENT`).
- [ ] Conservative on bare "Card" without limit-language.
- [ ] **Default-deny:** ambiguous-but-not-malformed, no credit-card signal → `EXCLUDE`/`LOW_CONFIDENCE`.
- [ ] Unit tests pass.

**2C — BankResolver** (`BankResolver.kt`, owns `banks.json` + `card-products.json`; read-only on `products.json`)
- [ ] Resolves issuer from body (C4).
- [ ] Co-brand map (`card-products.json`): Jupiter/Edge→Federal Bank; BOBCARD One→Bank of Baroda/BOBCARD.
- [ ] App/product branding does not override issuer.
- [ ] **Multi-bank precedence:** issuer token adjacent to card/limit/spend wins over footer/helpline bank tokens.
- [ ] Returns null when unknown (no guessing).
- [ ] Unit tests pass.

**2D — Amount + Currency** (`AmountExtractor.kt`, `CurrencyExtractor.kt`, `currencies.json`)
- [ ] Indian number formats parsed (`1,45,300.00`, `Rs.450.00`, `Rs 50000`).
- [ ] Currency detected: INR, Rs→INR, USD, EUR, AED (C7).
- [ ] Picks transaction amount, not balance/limit/markup.
- [ ] Unit tests pass.

**2E — Date extractor** (`DateExtractor.kt`, `dates.json`)
- [ ] Formats → ISO `YYYY-MM-DD` (`02/04/26`, `03-04-2026`, `04-Apr-26`, `06-APR-26`).
- [ ] Two-digit year → 20YY; null when absent.
- [ ] Unit tests pass.

**2F — Card + Merchant** (`CardExtractor.kt`, `MerchantExtractor.kt`, `merchants.json`)
- [ ] Last-four from `xx5678`, `XX9876`, `ending 1234`, `ending in XX9907`.
- [ ] Card-type token classified (Credit/Debit/bare Card/A/C) for the classifier.
- [ ] Merchant extracted from `at/to/with X`, cleaned reasonably.
- [ ] Unit tests pass.

**Coordination / exit criteria**
- [ ] Every component compiles; its own tests pass (`./gradlew test`).
- [ ] No component imports `android.*`.
- [ ] Config-driven proven (add bank/rule/currency via JSON in a test).
- [ ] Each agent mirrored **only its own** `assets/parser-config/*.json` file(s) into `src/test/resources/parser-config/` (no shared owner, no gap).
- [ ] Edge-case notes captured below.

**Component findings / edge cases**
- (record per-component notes here as agents report back)

---

## Phase 3 — Parser Integration, Confidence & Golden-Set Tuning

**Features / tasks**
- [ ] Real components wired into `SmsParser` in C3 order.
- [ ] `ConfidenceScorer` implemented with documented bands (clear spend 0.9–0.97, bare-card 0.8–0.88, co-brand 0.78–0.88, clear exclusion 0.9–0.97, ambiguous exclusion 0.6–0.78, malformed ≈0.1).
- [ ] Missing-field penalties applied.
- [ ] Default-deny path emits `EXCLUDE`/`LOW_CONFIDENCE` for ambiguous-not-malformed input.
- [ ] `ParserGoldenTest` runs all oracle inputs; asserts decision + reason + key fields; confidence as bands; **`transaction == null` for every EXCLUDE**; #1 reason asserted as accepted-set, #4 documented custom code.
- [ ] Tuned against oracle; deliberate divergences recorded.
- [ ] Resilience tests: reorder, append-novel-wording (incl. **CREDIT** case + **multi-bank** case + fresh OTP/foreign-currency), empty array.

**Exit criteria**
- [ ] Pipeline matches oracle decisions/reasons (or documented justified divergences).
- [ ] Summary = **Included: 7 / Excluded: 18**; INR debit ≈ ₹5,455; INR credit/refund ≈ ₹450.
- [ ] Confidence bands behave; `transaction == null` holds for all excludes.
- [ ] Reorder/append/empty tests pass.

**Struggled-with samples (feeds README §4)**
- (record here: e.g. #1 "block CC" trap, #5 bare "Card", #17 EMI-vs-spend, …)

---

## Phase 4 — React Native UI

**Features / tasks**
- [ ] `SummaryHeader.tsx`: INR debit total, INR credit/refund total, included count, excluded count, count-by-reason ("Top Exclusions"). INR totals exclude non-INR rows.
- [ ] `ResultRow.tsx` + `Chip.tsx` + `ConfidenceIndicator.tsx`: included rows (bank initials, merchant, amount+currency, date, type, confidence).
- [ ] Excluded rows: dimmed, reason chip/badge, short SMS preview, confidence.
- [ ] `DetailModal.tsx`: raw SMS, decision, exclude reason, transaction fields, confidence.
- [ ] `ParserScreen.tsx`: calls `parseSms(samples.map(s=>s.text))` on mount; state; loading/error handling.

**Exit criteria — verifiable any time (stub data)**
- [ ] On launch, screen calls `parseSms` and renders all results (resilient to appended samples).
- [ ] Summary shows all five figures; included vs excluded rows visually distinct; excluded dimmed with chip + preview.
- [ ] Tapping a row opens detail modal with all required fields.

**Exit criteria — requires Phase 3 green (real parser output)**
- [ ] Rendered values match real parser output; INR totals exclude foreign currency; summary shows true 7/18 split. (Do not tick Phase 4 done until Phase 3 is done.)

---

## Phase 5 — Kotlin Unit Test Suite Completion & Hardening

**Required tests (docs/Testing.md)**
- [ ] 1. Clear credit-card spend (sample 2) → INCLUDE/DEBIT + fields.
- [ ] 2. Debit-card exclusion (sample 6) → EXCLUDE/DEBIT_CARD.
- [ ] 3. OTP exclusion (sample 10) → EXCLUDE/OTP.
- [ ] 4. UPI/savings exclusion (sample 20 or 3) → EXCLUDE/UPI_BANK_ACCOUNT.
- [ ] 5. Fintech/co-branded issuer (sample 8 → Federal Bank; sample 9 → BOBCARD/BoB).
- [ ] 6. Refund (sample 21) → INCLUDE/REFUND.
- [ ] 7. Foreign currency (sample 22) → INCLUDE/DEBIT, USD, excluded from INR totals.
- [ ] 8. Malformed (sample 25) → EXCLUDE/MALFORMED_SMS, transaction null, conf ≈0.1.

**Extra hardening tests**
- [ ] Config-extensibility: new bank added via JSON only → resolves (proves C5).
- [ ] Conservative-bias: ambiguous bare "Card", no limit-language → `EXCLUDE`/`LOW_CONFIDENCE`, not a confident INCLUDE (C2).
- [ ] Null-contract: a non-malformed EXCLUDE has `transaction == null`.
- [ ] `CREDIT`-type path exercised by a synthetic non-refund credit-card credit (cross-ref Phase 3 resilience).

**Exit criteria**
- [ ] All 8 required categories present as named, passing tests.
- [ ] Extensibility + conservative-bias tests pass.
- [ ] `cd android; ./gradlew test` green; command captured for README.

---

## Phase 6 — Feature / Integration / E2E Testing

**Features / tasks**
- [ ] Golden E2E (Kotlin): full samples → assert aggregate summary (7/18, INR totals).
- [ ] Hidden-sample simulation: 3–5 new-wording synthetic samples → conservative, sane output (manually inspected, not string-asserted).
- [ ] Manual app run (run/verify skill): launch → summary → scroll included+excluded → included modal → excluded modal.
- [ ] Anti-cheat self-audit: no hard-coded sample strings, no ID switches, no length/order reliance, no network/LLM/SMS-permission calls.
- [ ] (Optional) Jest smoke test for JS wrapper shape.

**Exit criteria**
- [ ] Aggregate summary E2E passes.
- [ ] Hidden-wording samples produce no false-positive INCLUDEs.
- [ ] Manual run completes the full 5-step recording script error-free.
- [ ] Anti-cheat audit clean.

---

## Phase 7 — Documentation, README & Submission Prep

**README sections (docs/README-Requirements.md)**
- [ ] 1. How to run (exact commands incl. tests).
- [ ] 2. Parsing architecture (parser location, bridge, exclusion-vs-extraction, bank detection, config location, how to add a bank/rule, why).
- [ ] 3. Confidence scoring model.
- [ ] 4. Samples you struggled with (specific & honest).
- [ ] 5. What you'd do differently with a full week.
- [ ] 6. Production Android design note (500–800 words; permission flow, denial, Play policy, incremental parse, dedupe, WorkManager/BroadcastReceiver/ContentObserver, 30s budget, process death/retry, all 5 Indian OEMs + recovery UX, privacy).
- [ ] 7. AI tool usage (honest: tools, prompts that worked/failed, AI-written vs changed, manually verified).

**Submission checklist (docs/Submission.md)**
- [ ] RN Android app · Kotlin native module · JS bridge call · screen renders all results · summary header · detail modal · config-driven rules · Kotlin parser unit tests · README complete · production note complete · screen-recording link · no real SMS permission · no external API.

**Exit criteria**
- [ ] All 7 README sections present, specific; production note within 500–800 words covering every bullet + all five OEMs + 30s budget.
- [ ] Submission checklist fully ticked (except external recording link).
- [ ] Clean-checkout `yarn android` + `./gradlew test` both green.
