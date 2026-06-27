# Build Phase Plan — CashKaro Bank SMS Parser

> **Audience:** an orchestrator agent that will execute this plan **one phase at a time**.
> **Source of truth for requirements:** the files in [docs/](docs/). This document tells you *how* to build; the docs tell you *what* is required. When they disagree, the docs win — update this file and flag it.
>
> **Companion file:** [Progress.md](Progress.md) holds a checklist mirroring every task below. After completing each task, tick its box there. Never mark a phase done until its **Exit Criteria** are all met.

---

## 0. How the orchestrator should use this document

1. **Execute phases strictly in order (0 → 7).** Each phase lists `Prerequisites` (must be green before starting) and `Exit Criteria` (must be green before advancing). Do not skip ahead.
2. **Read before building.** Each phase names the one or two `docs/` files to (re)read. Do not load all docs every time.
3. **Fan out where marked.** Steps tagged **⚡ PARALLEL** are designed for concurrent sub-agents (a `Workflow` `parallel()`/`pipeline()` fan-out, or several `Agent` calls in one message). Each parallel agent owns a **disjoint set of files** so there are no write conflicts. Steps **not** tagged parallel are sequential — especially anything that edits shared build files (`package.json`, `build.gradle`, `settings.gradle`, `ParserConfig.kt`, the pipeline orchestrator). **Never run two agents that edit the same file concurrently.**
4. **Test as you build.** Every Kotlin component ships with its own unit tests in the same phase it is written (Phase 2). Phases 5–6 then *complete and harden* the suite — they are not where testing begins.
5. **Golden oracle.** [Appendix B](#appendix-b--sample-oracle-expected-outputs) is the expected-output table for the 25 samples. Use it as a **test oracle only** — drive tests and tuning from it. **Never hard-code it into the parser** (that is an explicit anti-cheat failure; see [docs/Submission.md](docs/Submission.md) and [docs/Testing.md](docs/Testing.md)).
6. **Update Progress.md** at the end of every task and phase.

---

## 0a. Orchestration protocol — running multiple agents

You (the orchestrator) hold the goal and the context; sub-agents do scoped work and report back to you. You never lose the thread — you dispatch, verify, integrate, and tick boxes.

### Loop (per phase)
1. **Load context once:** read this file's phase section + the `docs/` file it names + the relevant part of [Progress.md](Progress.md). Sub-agents get only what they need (see dispatch contract).
2. **Check the gate:** confirm the phase's `Prerequisites` are green. If not, finish the earlier phase first.
3. **Decide the shape:**
   - **Sequential step** (anything that edits shared build/contract files — `package.json`, `*.gradle`, `settings.gradle`, `MainApplication`, `SmsParser.kt`, `ParserConfig.kt`, the frozen interfaces) → do it yourself or via **one** sub-agent. Never fan these out.
   - **⚡ PARALLEL step** (marked in the phase) → fan out, one sub-agent per row of the ownership table, **each on its disjoint file set**.
4. **Dispatch** (see contract below). Prefer the **`Workflow` tool** with a `parallel()` fan-out and a `schema` so each agent returns a structured report; or, for a small fan-out, several **`Agent`** calls **in a single message** so they run concurrently.
5. **Collect reports**, then **integrate yourself**: wire components into the orchestrator, resolve any cross-file touch, and record each agent's edge-cases/deviations into the Progress.md notes blocks.
6. **Verify the gate:** run the phase's checks (`cd android; ./gradlew test`, and `yarn android` for UI phases). Reconcile failures. Only when every `Exit Criteria` box is objectively green do you tick the phase and advance.

### Dispatch contract — what every sub-agent prompt MUST contain
- **Scope:** the one component/task it owns and the **exact files it may create or edit** (from the phase's ownership table). "Do not touch any other file."
- **Contracts:** the frozen Phase-1 interface/signature it must implement against, and the `ConfigSource`/`ParserConfig` shape. It reads these; it does not change them.
- **Principles:** the cross-cutting rules that apply (C1–C9 — at minimum C2 conservative, C5 config-driven, C6 hidden-sample-resilient, and "no `android.*` in the parser core").
- **Tests:** it must write its own JUnit tests using `TestConfigSource` and mirror only its own config JSON into `src/test/resources/`.
- **Report-back format (require this verbatim so reports are comparable):**
  1. `files_changed` — list,
  2. `summary` — what it implemented,
  3. `tests` — names added + `./gradlew test` pass/fail for its slice,
  4. `edge_cases` — tricky inputs handled or punted (feeds README §4),
  5. `deviations_or_blockers` — anything it could not do to contract, or a gap it found in the frozen interface.

### Handling reports & conflicts
- **Blocker / contract gap:** if any agent reports it needs a change to a frozen interface or another agent's file, **pause the fan-out**, resolve the contract centrally (edit the Phase-1 file yourself), then re-dispatch the affected agents. Do not let agents negotiate across each other.
- **Merge:** because file sets are disjoint, there should be no overlapping edits. If two reports claim the same file, that's a planning error — stop and reassign ownership before integrating.
- **Isolation:** plain parallel sub-agents are fine given disjoint files. Use `worktree` isolation only if you cannot guarantee disjointness for a given fan-out.
- **Green-before-advance:** never tick an Exit Criterion from an agent's say-so alone — re-run the build/tests yourself after integration.

### Recommended tool
For the structured fan-outs (Phase 2 components, Phase 5 tests, Phase 7 README sections) use the **`Workflow`** tool: `parallel()` the per-owner tasks, give each a `schema` matching the report-back format above, then integrate the returned objects. This keeps reports uniform and lets you resume if a phase is interrupted.

---

## 1. Tech stack (locked — do not substitute without recording the reason in the README)

| Layer | Choice | Notes |
| --- | --- | --- |
| App framework | **React Native (Android only)**, pinned **0.74.x** | iOS is explicitly out of scope ([docs/PRD.md](docs/PRD.md)). Use the community CLI TypeScript template. |
| JS language | **TypeScript** | Types mirror the `ParsedResult` schema in [docs/Functions.md](docs/Functions.md). |
| Native module | **Kotlin**, classic bridge (`ReactContextBaseJavaModule` + `ReactPackage`) | Classic bridge works with or without the New Architecture and is the most explainable. Keep `newArchEnabled=false` to minimise setup friction; record this choice in the README. |
| Parser core | **Pure Kotlin, Android-agnostic** (no `android.*` imports) | This is the key testability decision — see [Architecture](#3-architecture-overview). |
| Config | **Bundled JSON** in `android/app/src/main/assets/parser-config/` | Loaded through a `ConfigSource` interface so tests can inject config without an Android `Context`. |
| Kotlin tests | **JUnit4** (`testImplementation junit:junit:4.13.2`), run on the JVM via Gradle | No Robolectric / no instrumented tests needed because the parser core is pure Kotlin. |
| JS tests | **Jest** (ships with the RN template) — *optional*, one smoke test only | The assignment requires **Kotlin** parser tests; JS tests are a nicety, not a requirement. |
| Toolchain | Node 18+, JDK 17, Android SDK + one emulator/device, Yarn | Build/launch must work via `yarn install` && `yarn android` ([docs/PRD.md](docs/PRD.md)). |

**Environment note (Windows / PowerShell):** Gradle is invoked as `cd android; ./gradlew test` (or `.\gradlew.bat test`). Confirm JDK 17 is the active `JAVA_HOME` and an emulator is reachable (`adb devices`) before Phase 0 exit.

---

## 2. Cross-cutting principles — honour these in every phase

These come straight from [docs/Functions.md](docs/Functions.md) and [docs/PRD.md](docs/PRD.md). Treat them as acceptance gates on *all* code.

- **C1 — Parsing lives in Kotlin.** Classification, bank detection, amount/field extraction, and confidence scoring are all Kotlin. JS only calls the bridge and renders. Doing parser logic in JS is an explicit penalty.
- **C2 — Conservative under uncertainty.** When unsure, `EXCLUDE` with a *specific* reason and *low* confidence. **False positives are worse than false negatives.** A confidently-wrong INCLUDE is the worst outcome.
- **C3 — Exclusion-first.** Most bank-looking SMS are *not* credit-card spends. Run exclusion rules before declaring an INCLUDE.
- **C4 — Bank attribution reads the SMS body**, never just the sender ID. Co-branded/fintech cards resolve to the **issuer**: *Jupiter / Edge → Federal Bank*, *BOBCARD One → Bank of Baroda / BOBCARD*.
- **C5 — Config-driven.** Banks, products, exclusion rules, merchant rules, currency patterns, and date formats live in config. **Adding a new bank or rule must not require editing parser logic.** No single mega-regex tuned to the 25 samples.
- **C6 — Hidden-sample resilience.** ~10 hidden samples (≈15% of the grade) will be appended to `samples.json`. **No** hard-coded sample IDs/strings, **no** reliance on array length/order, **no** matching whole SMS bodies. Generalise patterns.
- **C7 — Currency is detected, never assumed.** Support at least INR, Rs, USD, EUR, AED.
- **C8 — Malformed → fail safe.** `EXCLUDE / MALFORMED_SMS / transaction=null / confidence≈0.1`.
- **C9 — No runtime network, no LLM, no real SMS permissions, no reading the device inbox.** Everything is local and offline.

---

## 3. Architecture overview

**Design intent:** a thin Android bridge wrapping a **pure-Kotlin, dependency-injected parser core**. The core has zero `android.*` imports, so the entire parser is unit-testable on the JVM with no emulator and no `Context`.

```
JS (TypeScript)
  src/native/SmsParser.ts ──calls──▶  NativeModules.SmsParser.parseSms(string[])
                                              │
ANDROID BRIDGE (android.* allowed)            ▼
  SmsParserPackage.kt  ─registers─▶  SmsParserModule.kt  (ReactContextBaseJavaModule)
        • reads config from assets via AssetConfigSource
        • calls the pure-Kotlin SmsParser
        • converts List<ParsedResult> ──▶ WritableArray for JS
                                              │
PURE KOTLIN CORE (no android.* imports)       ▼
  SmsParser (orchestrator)
     1. Normalizer            → clean/standardise text
     2. MalformedGate         → C8 fast-exit for truncated/empty input
     3. ExclusionEngine       → ordered, config-driven rules → (reason | none)   ← C3
     4. InclusionClassifier   → credit-card spend? type = DEBIT/CREDIT/REFUND
     5. Extractors (run only if INCLUDE candidate):
          AmountExtractor · CurrencyExtractor · DateExtractor
          CardExtractor (last-four + card-type) · MerchantExtractor · BankResolver  ← C4
     6. ConfidenceScorer      → 0.0–1.0 from decision certainty + field completeness
  ParserConfig  ← loaded once from ConfigSource (AssetConfigSource | TestConfigSource)
```

**Why this shape (for the README later):** separating *exclusion* from *extraction* keeps conservative filtering (the thing being graded hardest) independent and ordered; injecting config via `ConfigSource` makes the core JVM-testable and satisfies C5; resolving the bank in a dedicated `BankResolver` that reads the body satisfies C4 and isolates the trickiest logic.

### Proposed directory layout (the orchestrator creates this)

```
/ (repo root)
  package.json  app.json  index.js  App.tsx  tsconfig.json  babel.config.js
  src/
    data/samples.json                 # 25 samples as [{id, text}], JS-loadable
    native/SmsParser.ts               # typed wrapper + ParsedResult TS types
    screens/ParserScreen.tsx          # the single screen; calls parseSms on mount
    components/SummaryHeader.tsx  ResultRow.tsx  DetailModal.tsx  Chip.tsx  ConfidenceIndicator.tsx
  android/app/src/main/java/com/cashkaro/smsparser/
    SmsParserPackage.kt   SmsParserModule.kt           # bridge (android.* allowed)
    parser/                                            # PURE KOTLIN
      SmsParser.kt
      model/        ParsedResult.kt  Transaction.kt  Decision.kt  TxnType.kt  ExcludeReason.kt
      pipeline/     Normalizer.kt  MalformedGate.kt
      classify/     ExclusionEngine.kt  ExclusionRule.kt  InclusionClassifier.kt  CardSignal.kt
      extract/      AmountExtractor.kt  CurrencyExtractor.kt  DateExtractor.kt  CardExtractor.kt  MerchantExtractor.kt
      bank/         BankResolver.kt
      confidence/   ConfidenceScorer.kt
      config/       ParserConfig.kt  ConfigSource.kt  AssetConfigSource.kt  JsonConfigParser.kt
  android/app/src/main/assets/parser-config/
      banks.json  card-products.json  products.json  exclusion-rules.json  currencies.json  merchants.json  dates.json
  android/app/src/test/java/com/cashkaro/smsparser/parser/   # JUnit4 tests
  android/app/src/test/resources/parser-config/              # TestConfigSource reads these (mirror of assets)
  docs/   CLAUDE.md   buildphase.md   Progress.md   README.md
```

---

## Phase 0 — Project Scaffold & Build Bring-up

**Goal:** a blank RN Android app that builds, launches on an emulator, and exposes a *stubbed* native `parseSms` returning the correct schema shape.

**Read:** [docs/PRD.md](docs/PRD.md) (scope, build commands), [docs/Functions.md](docs/Functions.md) (schema & `parseSms` signature).

**Prerequisites:** Node 18+, JDK 17, Android SDK + emulator/device verified (`adb devices`).

**Tasks (sequential — these touch shared build files):**
1. Scaffold RN 0.74.x TypeScript app at the repo root (community CLI). Keep `docs/`, `CLAUDE.md`, `buildphase.md`, `Progress.md` intact.
2. Set `newArchEnabled=false` in `android/gradle.properties`; confirm app id `com.cashkaro.smsparser` (or chosen id, recorded consistently).
3. Create `src/data/samples.json` as `[{ "id": 1, "text": "..." }, …]` for all 25 samples (verbatim from [docs/SMS-Examples.md](docs/SMS-Examples.md)). This stands in for the provided `samples.json`. **The JS must accept any-length array (C6).**
4. Implement the **stub** bridge: `SmsParserModule.parseSms(samples: ReadableArray, promise: Promise)` returning, for each input, a hard-coded-shape result (`rawSms` echoed, `decision="EXCLUDE"`, `excludeReason="LOW_CONFIDENCE"`, `transaction=null`, `confidence=0.0`). Register via `SmsParserPackage` in `MainApplication`.
5. Implement `src/native/SmsParser.ts` typed wrapper + `ParsedResult` TS types matching the schema exactly.
6. Minimal `App.tsx` that calls `parseSms` on mount and renders the raw count so the bridge round-trip is visibly working.
7. Add `.gitignore` entries for RN/Android build artefacts if not already covered.

**⚡ PARALLEL:** none — scaffolding mutates shared config/build files; keep it serial.

**Deliverables:** building app; working JS↔Kotlin round-trip with stub data; `samples.json`.

**Exit Criteria:**
- [ ] `yarn install` succeeds.
- [ ] `yarn android` builds and launches on the emulator.
- [ ] The screen shows "25 results received" (or similar) proving the stub bridge returns one result per input.
- [ ] `cd android; ./gradlew test` runs (zero tests is fine) — Gradle test wiring confirmed.

---

## Phase 1 — Architecture Contracts, Config Schema & Sample Oracle

**Goal:** freeze the data models, the `ConfigSource`/`ParserConfig` contracts, the JSON config schema, and the pipeline orchestrator **with stubbed components**. Author the sample oracle that drives all later testing. Nothing here should require rework in Phase 2 — these are the *contracts* the parallel agents build against.

**Read:** [docs/Functions.md](docs/Functions.md) (schema, field rules, reason codes), [docs/SMS-Examples.md](docs/SMS-Examples.md) (the 25 samples).

**Prerequisites:** Phase 0 green.

**Tasks:**
1. **Models** (`parser/model/`): `Decision` (INCLUDE/EXCLUDE), `TxnType` (DEBIT/CREDIT/REFUND — note all three must exist; `CREDIT` is for relevant non-refund credit-card credits and is exercised in Phase 2B/3 even though no *visible* sample is a CREDIT), `ExcludeReason` (enum/sealed of **all** reasons in [docs/Functions.md](docs/Functions.md) **plus the custom codes the oracle uses: `SAVINGS_ACCOUNT`, `UPI_BANK_ACCOUNT`, `SALARY_CREDIT`** — so the enum is complete at freeze time; `LOW_CONFIDENCE` is reserved as the ambiguous default-deny fallback, see Phase 2B), `Transaction` (amount: Double, currency: String, bank: String?, cardLastFour: String?, merchant: String?, type: TxnType, date: String?), `ParsedResult` (rawSms, decision, excludeReason, transaction?, confidence: Double).
2. **Config contracts** (`parser/config/`): `ParserConfig` data class aggregating `banks`, `cardProducts`, `products`, `exclusionRules`, `currencies`, `merchantRules`, `dateFormats`. `ConfigSource` interface (`fun load(): ParserConfig`). `JsonConfigParser` (string → `ParserConfig`). Define the **JSON schema** for each config file (see [Appendix A](#appendix-a--config-schema-sketch)) and commit *seed* JSON (can start minimal; Phase 2 agents extend their slices).
   - **Freeze a shared `CardSignal` helper** (`parser/classify/CardSignal.kt` or `util/`) that detects a credit-card signal from `products.json` and is consulted by **both** the ExclusionEngine (to evaluate the `withCard` / `notCreditCard` rule qualifiers) and the InclusionClassifier — this breaks the otherwise-hidden 2A→2B dependency. **Seed `products.json` with the core credit-card signals in this phase** so the helper is functional before Phase 2; agent 2B only *refines* it later. The helper must disambiguate `avl limit` / `available limit` (credit-card signal) from `avl bal` / `available balance` (balance alert) — never collapse the `avl ` prefix.
   - **Define and freeze the exclusion-rule qualifier semantics** exactly as documented in [Appendix A](#appendix-a--config-schema-sketch): a rule matches when any `any` token is present, no `unless` token is present, and all flags (`withCard`/`notCreditCard`, via `CardSignal`) hold.
3. **Android config source** (`AssetConfigSource` in the bridge layer) reads `assets/parser-config/*.json`; **test config source** (`TestConfigSource`) reads `src/test/resources/parser-config/*.json`. Both produce identical `ParserConfig`.
4. **Pipeline orchestrator** (`SmsParser.kt`): wire the 6 stages as injected interfaces (`Normalizer`, `MalformedGate`, `ExclusionEngine`, `InclusionClassifier`, extractor set, `ConfidenceScorer`), each with a no-op/stub implementation that compiles and returns schema-valid output. **Freeze these interface signatures.**
5. **Bridge integration:** `SmsParserModule` constructs `SmsParser` with `AssetConfigSource` and real stages (still stubs), converts results to `WritableArray`. End-to-end still returns schema-valid stub data — but now through the real pipeline shape.
6. **Sample oracle:** transcribe [Appendix B](#appendix-b--sample-oracle-expected-outputs) into a test resource (e.g. `src/test/resources/oracle.json`) used by the golden-set test in Phase 3/6. Mark clearly that this is a *test oracle*, not parser data.

**⚡ PARALLEL (2 agents, disjoint files):**
- *Agent 1* — models + config contracts + orchestrator skeleton (`parser/model/`, `parser/config/`, `SmsParser.kt`).
- *Agent 2* — author/validate the sample oracle (`oracle.json`) and the per-sample reasoning notes, re-deriving expected outputs from [docs/Functions.md](docs/Functions.md) rules rather than copying blindly. Reconcile with [Appendix B](#appendix-b--sample-oracle-expected-outputs); flag any disagreement.

**Deliverables:** frozen contracts; compiling stubbed pipeline; seed config JSON; oracle resource.

**Exit Criteria:**
- [ ] All model/config/interface types compile; bridge returns schema-valid output through the real pipeline shape.
- [ ] `ConfigSource` swappable (Asset vs Test) verified by a trivial load test.
- [ ] **Verbatim field-name check:** a shape/snapshot assertion confirms the bridge result's JSON keys equal the [docs/Functions.md](docs/Functions.md) schema exactly — top level `rawSms, decision, excludeReason, transaction, confidence` and `transaction.{amount, currency, bank, cardLastFour, merchant, type, date}` — guarding against silent drift like `cardLast4`/`txnType`.
- [ ] `CardSignal` helper + qualifier semantics frozen; `products.json` seeded with core credit-card signals.
- [ ] Oracle resource present and internally consistent with [docs/Functions.md](docs/Functions.md) field rules.
- [ ] Interface signatures for all 6 stages documented in code comments and **declared frozen**.

---

## Phase 2 — ⚡ Parallel Component Build (the core logic)

**Goal:** implement every pipeline component against the frozen Phase-1 contracts, each **with its own JUnit tests and its own config slice**. This is the main fan-out and the bulk of the work.

**Read:** [docs/Functions.md](docs/Functions.md) (field rules, exclusion list, bank examples, currency, malformed), [docs/Testing.md](docs/Testing.md) (which behaviours must be covered).

**Prerequisites:** Phase 1 contracts frozen.

**⚡ PARALLEL — 6 agents, each owning a disjoint file set (code + config + tests). Recommended as a `Workflow` `parallel()` fan-out (or 6 `Agent` calls in one message).** Each agent must: implement to the frozen interface, write unit tests using `TestConfigSource`, generalise patterns for hidden samples (C6), and stay config-driven (C5). No agent edits another's files or the shared `ParserConfig`/orchestrator.

| Agent | Component | Owns (code) | Owns (config) | Must handle |
| --- | --- | --- | --- | --- |
| **2A** | **ExclusionEngine + rules** | `classify/ExclusionEngine.kt`, `classify/ExclusionRule.kt` | `exclusion-rules.json` | Ordered rules → reason. Cover **all** C2/C4 exclusions: OTP, DEBIT_CARD, SAVINGS_ACCOUNT, UPI_BANK_ACCOUNT, BALANCE_ALERT, BILL_DUE, OFFER, FUTURE_AUTO_DEBIT, DECLINED, EMI_CONVERSION, FEE_OR_CHARGE, CARD_PAYMENT, INVESTMENT, INSURANCE, SALARY_CREDIT. Order matters (e.g. a *future* auto-debit on a credit card is FUTURE_AUTO_DEBIT, not a spend). |
| **2B** | **InclusionClassifier** | `classify/InclusionClassifier.kt` | **owns `products.json`** (via the Phase-1 `CardSignal` helper) | Decide credit-card spend vs not; assign `TxnType`. "Credit Card" / "Avl Limit/Available Limit" ⇒ credit-card signal; "Debit Card" / "A/C"/"Acct"/"UPI" ⇒ not. Distinguish **DEBIT** (spend) vs **REFUND** (reversal) vs **CREDIT** (relevant non-refund credit-card credit, kept distinct from `CARD_PAYMENT` which is excluded). Conservative when only ambiguous "Card" appears (lean on limit-language). **Default-deny:** ambiguous-but-not-malformed, no credit-card signal ⇒ `EXCLUDE`/`LOW_CONFIDENCE` (never a confident INCLUDE). |
| **2C** | **BankResolver** | `bank/BankResolver.kt` | `banks.json`, `card-products.json` (read-only on `products.json`) | **C4**: resolve issuer from body. Co-branded map (`card-products.json`): Jupiter/Edge→Federal Bank; BOBCARD One→Bank of Baroda/BOBCARD. Product/app branding must **not** override issuer. Direct bank names from `banks.json` (HDFC, ICICI, Axis, Yes Bank…). **Multi-bank precedence:** when more than one bank token appears (issuer + a "powered by"/helpline footer), prefer the token adjacent to card/limit/spend language and ignore footer/helpline contexts. Return null when unknown (lowers confidence, never guesses). |
| **2D** | **Amount + Currency extractors** | `extract/AmountExtractor.kt`, `extract/CurrencyExtractor.kt` | `currencies.json` | Parse Indian-format numbers (`1,45,300.00`, `Rs.450.00`, `Rs 50000`). Detect currency symbol/code (INR, Rs→INR, USD, EUR, AED) — **C7**, never assume INR. Pick the *transaction* amount, not balance/limit/markup amounts. |
| **2E** | **Date extractor** | `extract/DateExtractor.kt` | `dates.json` | Many formats → ISO `YYYY-MM-DD`: `02/04/26`, `03-04-2026`, `04-Apr-26`, `06-APR-26`. Two-digit year → 20xx. Return null when absent (not a guess). |
| **2F** | **Card extractor + Merchant extractor** | `extract/CardExtractor.kt`, `extract/MerchantExtractor.kt` | `merchants.json` | Card: last-four from `xx5678`, `XX9876`, `ending 1234`, `ending in XX9907`; also classify card-type token (Credit Card / Debit Card / bare Card / A/C) to feed the classifier. Merchant: extract from `at X`, `to X`, `at X, City` — clean but "perfect merchant extraction" is explicitly *not* tested, so favour safe over clever. |

**Coordination rules for the orchestrator:**
- Each agent adds **only its own** config JSON file(s); the shared `ParserConfig` aggregation was frozen in Phase 1, so agents read it, never edit it.
- If two components genuinely need the same helper (e.g. number normalisation), put it in a Phase-1-frozen `util/` file or have the *owning* agent expose it — do not duplicate-edit.
- Each agent mirrors **only its own** `assets/parser-config/*.json` file(s) into `src/test/resources/parser-config/` (mirroring stays inside each agent's disjoint set — no shared owner, no gap) so JVM tests load config without a `Context`.
- Use `worktree` isolation only if the orchestrator cannot guarantee disjoint files; with the table above, files are disjoint, so plain parallel agents are fine.

**Deliverables:** all 6 components implemented + unit-tested in isolation; config files populated.

**Exit Criteria:**
- [ ] Every component compiles and its own unit tests pass (`./gradlew test`).
- [ ] No component imports `android.*`.
- [ ] Each component is driven by config (adding a bank/rule/currency = JSON edit, demonstrated in a test).
- [ ] Each agent's findings/edge-cases captured in Progress.md notes.

---

## Phase 3 — Parser Integration, Confidence Scoring & Golden-Set Tuning

**Goal:** replace the stub stages with the real components, implement the confidence model, run the full pipeline over all 25 samples, and tune against the oracle until the parser matches it (or you deliberately, with a recorded reason, diverge).

**Read:** [docs/Functions.md](docs/Functions.md) (confidence examples, malformed), [docs/PRD.md](docs/PRD.md) ("what we are testing").

**Prerequisites:** Phase 2 green.

**Tasks (sequential — this is the integration point):**
1. Wire real components into `SmsParser` in the C3 order: Normalize → MalformedGate → ExclusionEngine → (if not excluded) InclusionClassifier → extractors → ConfidenceScorer.
2. **ConfidenceScorer** model (document in README later):
   - Clear credit-card spend (explicit "Credit Card" + amount + merchant + date): **0.9–0.97**.
   - Credit-card signal via limit-language but bare "Card": **0.8–0.88**.
   - Co-branded resolved via body (Jupiter/BOBCARD): **0.78–0.88**.
   - Clear exclusion (OTP/debit/UPI/keyword-strong): **0.9–0.97**.
   - Ambiguous exclusion: **0.6–0.78**.
   - Malformed: **≈0.1** (C8).
   - Apply small penalties for missing extractable fields (no date, no merchant, unresolved bank).
3. **Golden-set test** (`ParserGoldenTest`): run all oracle inputs through `SmsParser` (built with `TestConfigSource`) and assert decision + excludeReason + key transaction fields per [Appendix B](#appendix-b--sample-oracle-expected-outputs). Treat confidence as a **band** assertion (e.g. ≥0.8 for clear includes, ≤0.2 for malformed), not exact equality. **Also assert `transaction == null` for *every* EXCLUDE result** (not just MALFORMED_SMS). To avoid brittleness on the two custom-code cells, assert sample #1's reason against an **accepted set** (`SAVINGS_ACCOUNT` | `UPI_BANK_ACCOUNT` | `BANK_ACCOUNT`) and document `SALARY_CREDIT` (#4) as an intentional custom code.
4. **Tune** the config/components on mismatches. Every deliberate divergence from the oracle is recorded as a note (feeds README §4 "samples you struggled with").
5. **Hidden-sample resilience pass:** add tests that (a) reorder the samples, (b) append novel-wording variants — including a **non-refund credit-card CREDIT** (e.g. "Rs X credited to your … Credit Card as cashback/reversal") to exercise the `CREDIT` type, a **multi-bank** message (issuer + helpline-footer bank) to exercise 2C precedence, and a fresh OTP/foreign-currency phrasing — and (c) feed an empty array — asserting no crash, order-independence, and conservative behaviour (C6, C8).

**⚡ PARALLEL:** the confidence model (task 2) and the resilience tests (task 5) can be drafted by a second agent while the primary agent does integration + golden tuning — but **integration itself is single-owner** (one agent edits `SmsParser.kt`).

**Deliverables:** fully integrated parser; confidence model; passing golden-set test; resilience tests; a list of struggled-with samples.

**Exit Criteria:**
- [ ] Full pipeline returns the oracle's decisions/reasons for the 25 samples (or documented, justified divergences only).
- [ ] Summary derived from output matches the spec hint **Included: 7 / Excluded: 18** ([docs/UI-Requirements.md](docs/UI-Requirements.md)).
- [ ] Confidence bands behave (high for clear, ~0.1 for malformed).
- [ ] Reorder/append/empty tests pass.

---

## Phase 4 — React Native UI (may overlap Phase 3)

**Goal:** the single screen from [docs/UI-Requirements.md](docs/UI-Requirements.md): summary header, result list (included + excluded rows), tap-to-open detail modal. Clean and readable — **polish is explicitly not graded** ([docs/PRD.md](docs/PRD.md)).

**Read:** [docs/UI-Requirements.md](docs/UI-Requirements.md).

**Prerequisites:** the bridge **schema** is frozen (end of Phase 1). UI can be built against stub data and switched to real output once Phase 3 lands — so this phase **may run in parallel with Phase 3**.

**⚡ PARALLEL (UI components are independent files — 3–4 agents):**
- *Agent 4A* — `SummaryHeader.tsx`: total INR debit, total INR credit/refund, included count, excluded count, count-by-exclude-reason ("Top Exclusions"). INR totals must **exclude non-INR** transactions (C7) — sum only `currency === "INR"`.
- *Agent 4B* — `ResultRow.tsx` + `Chip.tsx` + `ConfidenceIndicator.tsx`: included rows (bank initials/logo, merchant, amount+currency, date, type, confidence); excluded rows (dimmed, reason chip/badge, short SMS preview, confidence).
- *Agent 4C* — `DetailModal.tsx`: raw SMS, decision, exclude reason (if any), parsed transaction fields (if any), confidence.
- *Agent 4D* — `ParserScreen.tsx`: calls `parseSms(samples.map(s => s.text))` on mount, holds state, composes the above, handles loading/error.

**Deliverables:** working screen rendering all results from the real parser.

**Exit Criteria** — split because the UI may be *built* against stub data before Phase 3 lands, but cannot be *verified complete* until the real parser is wired:

*Verifiable any time (against stub data):*
- [ ] On launch the screen calls `parseSms` and renders all (25 + any appended) results.
- [ ] Summary header renders all five required figures; included vs excluded rows visually distinct; excluded rows dimmed with reason chip + preview.
- [ ] Tapping any row opens the detail modal with all required fields.

*Requires Phase 3 green (verified against real parser output):*
- [ ] Rendered values match real parser output; INR totals exclude foreign-currency rows; summary reflects the true 7/18 split. **Do not tick Phase 4 complete until Phase 3 is done**, even if the UI build overlapped it.

---

## Phase 5 — Kotlin Unit Test Suite Completion & Hardening

**Goal:** guarantee the **8 required test categories** from [docs/Testing.md](docs/Testing.md) are explicitly covered as named tests, plus config-extensibility and conservative-behaviour tests. (Components already have tests from Phase 2; this phase ensures the *required* matrix is complete and named.)

**Read:** [docs/Testing.md](docs/Testing.md).

**Prerequisites:** Phase 3 green.

**⚡ PARALLEL (one agent per required test category — disjoint test files):** each writes a focused, named JUnit test using `TestConfigSource`:
1. Clear credit-card spend (e.g. sample 2) → INCLUDE/DEBIT, correct fields.
2. Debit-card exclusion (sample 6) → EXCLUDE/DEBIT_CARD.
3. OTP exclusion (sample 10) → EXCLUDE/OTP.
4. UPI/savings-account exclusion (sample 20 or 3) → EXCLUDE/UPI_BANK_ACCOUNT.
5. Fintech/co-branded issuer attribution (sample 8 → Federal Bank; and sample 9 → BOBCARD/Bank of Baroda) → bank resolved from body.
6. Refund (sample 21) → INCLUDE/REFUND.
7. Foreign-currency transaction (sample 22) → INCLUDE/DEBIT, currency USD, excluded from INR totals.
8. Malformed SMS (sample 25) → EXCLUDE/MALFORMED_SMS, transaction null, confidence ≈0.1.

**Plus (can be same fan-out):**
- Config-extensibility test: add a brand-new bank **purely via JSON** and assert it resolves — proves C5.
- Conservative-bias test: an ambiguous bare-"Card" message with no limit-language → `EXCLUDE`/`LOW_CONFIDENCE`, never a confident INCLUDE (C2).
- Null-contract test: assert `transaction == null` for a representative EXCLUDE that is **not** malformed (the null contract must hold for all excludes, not only MALFORMED_SMS).
- (Covered in Phase 3 resilience, cross-referenced here) `CREDIT`-type path exercised by a synthetic non-refund credit-card credit.

**Deliverables:** complete, named, passing unit-test suite; documented run command.

**Exit Criteria:**
- [ ] All 8 required categories present as named, passing tests.
- [ ] Config-extensibility and conservative-bias tests pass.
- [ ] `cd android; ./gradlew test` is green and the command is captured for the README.

---

## Phase 6 — Feature / Integration / E2E Testing

**Goal:** prove the whole app behaves correctly end-to-end, is hidden-sample resilient, and is ready to record.

**Read:** [docs/Testing.md](docs/Testing.md) (hidden-sample rules), [docs/Submission.md](docs/Submission.md) (recording steps & checklist), [docs/UI-Requirements.md](docs/UI-Requirements.md).

**Prerequisites:** Phases 3, 4, 5 green.

**Tasks:**
1. **Golden E2E (Kotlin):** the full `samples.json` → parser → assert the aggregate summary (7 included / 18 excluded, INR debit total, INR credit/refund total) matches [Appendix B](#appendix-b--sample-oracle-expected-outputs).
2. **Hidden-sample simulation:** append 3–5 *new-wording* synthetic samples (new bank, new co-brand, a new OTP phrasing, a foreign currency, a truncated string) and confirm sensible, conservative results — manually inspected, **not** asserted against fixed strings (C6).
3. **Manual app run** on the emulator (use the `run`/`verify` skills): launch → summary correct → scroll included + excluded → open one included modal → open one excluded modal. This is exactly the [docs/Submission.md](docs/Submission.md) recording script.
4. **Anti-cheat self-audit:** grep the codebase for hard-coded sample strings, sample-ID switches, array-length/order assumptions, and any network/LLM/SMS-permission calls. Must find none (C1, C6, C9).
5. **(Optional) Jest smoke test** for the JS wrapper shape.

**⚡ PARALLEL:** tasks 1, 2, and 4 (golden E2E, hidden-sim, anti-cheat audit) are independent and can fan out; task 3 (manual run) is interactive and single-owner.

**Deliverables:** passing E2E; documented hidden-sim observations; clean anti-cheat audit; a verified recording walkthrough.

**Exit Criteria:**
- [ ] Aggregate summary E2E passes.
- [ ] Hidden-wording samples produce conservative, sane output (no false-positive INCLUDEs).
- [ ] Manual run completes the full 5-step recording script without errors.
- [ ] Anti-cheat audit clean.

---

## Phase 7 — Documentation, README & Submission Prep

**Goal:** the README (graded heavily) with every required section, plus the submission checklist satisfied.

**Read:** [docs/README-Requirements.md](docs/README-Requirements.md), [docs/Submission.md](docs/Submission.md).

**Prerequisites:** Phases 0–6 green.

**⚡ PARALLEL (README sections are independent — fan out, one agent per section, then a single agent stitches + dedupes voice):**
1. **How to run** — exact commands: `yarn install`, `yarn android`, and the test command (`cd android; ./gradlew test`).
2. **Parsing architecture** — where the Kotlin parser lives, how the bridge calls it, exclusion-vs-extraction separation, bank detection, where config lives, how to add a bank/rule, and *why* structured this way.
3. **Confidence scoring** — the model from Phase 3 (high vs low, low-confidence exclusion, malformed handling).
4. **Samples you struggled with** — from the Phase 3 divergence notes (be specific & honest; e.g. sample 1 "block CC" footer trap, sample 5 bare "Card", sample 17 EMI-conversion-vs-spend).
5. **What you'd do differently with a full week** — rule engine, merchant extraction, date parsing, coverage, real SMS flow, telemetry.
6. **Production Android design note (500–800 words)** — the big one. Cover: runtime SMS permission flow; denial handling; Google Play SMS-policy risk; incremental/new-only parsing; duplicate prevention; background execution (WorkManager vs BroadcastReceiver vs ContentObserver trade-offs); the **30-second** notification latency budget (which paths can/can't meet it and where the parser must run); process-death/retry; **Indian OEM** background-kill behaviour for **Xiaomi/MIUI/HyperOS, Realme, OPPO/ColorOS, Vivo/FuntouchOS, OnePlus/OxygenOS** (battery optimisation, autostart, app-standby buckets, Doze, OEM restrictions) **and** the UX to detect/explain/recover; privacy/local-processing. *This is a design note only — do not implement.*
7. **AI tool usage** — honest account: tools used, prompts that worked/failed, what AI wrote vs what was changed, what was verified manually.

**Then (single-owner):** fill the [docs/Submission.md](docs/Submission.md) checklist, add the screen-recording link placeholder, final commit.

**Deliverables:** complete README; satisfied submission checklist.

**Exit Criteria:**
- [ ] All 7 README sections present and specific; production note is 500–800 words and covers every required bullet incl. all five OEMs and the 30s budget.
- [ ] Submission checklist in [docs/Submission.md](docs/Submission.md) fully ticked (except the external recording link, which is manual).
- [ ] Final `yarn android` + `./gradlew test` both green on a clean checkout.

---

## Appendix A — Config schema sketch

Seed shapes for `assets/parser-config/` (and mirrored test resources). Exact fields are the orchestrator's to refine in Phase 1 — keep them generic enough for hidden samples (C6).

```jsonc
// banks.json (owned by 2C) — DIRECT issuer bank names, matched against the SMS BODY (C4).
{
  "banks": [
    { "canonical": "HDFC Bank",      "patterns": ["hdfc"] },
    { "canonical": "ICICI Bank",     "patterns": ["icici"] },
    { "canonical": "Axis Bank",      "patterns": ["axis"] },
    { "canonical": "Yes Bank",       "patterns": ["yes bank"] },
    { "canonical": "Federal Bank",   "patterns": ["federal bank"] },
    { "canonical": "Bank of Baroda", "patterns": ["bank of baroda"] }
  ]
}
```
```jsonc
// card-products.json (owned by 2C) — CO-BRAND / fintech product name → issuer (C4).
// Sender/app branding must NOT override the issuer; this map encodes the resolution.
{
  "cardProducts": [
    { "product": "Edge",        "issuer": "Federal Bank" },     // Jupiter/Edge card
    { "product": "Jupiter",     "issuer": "Federal Bank" },
    { "product": "BOBCARD One", "issuer": "Bank of Baroda" },   // model may report "BOBCARD"
    { "product": "BOBCARD",     "issuer": "Bank of Baroda" }
  ]
}
```
```jsonc
// exclusion-rules.json — ORDERED; first match wins (C3). Keyword/regex driven, generalised.
//
// QUALIFIER SEMANTICS (frozen in Phase 1): a rule matches when ANY token in "any" is present
//   AND no token in "unless" is present AND every flag holds. Flags are evaluated via the
//   shared CardSignal helper (Phase 1): "withCard" ⇒ a credit-card signal is present;
//   "notCreditCard" ⇒ no credit-card signal present.
//
// ORDERING PRINCIPLE: specific intent BEFORE generic account catch-alls. Action-based
//   exclusions (UPI / salary / investment / insurance / debit-card) and the specific
//   BALANCE_ALERT precede the generic SAVINGS_ACCOUNT fallback, so a transaction is never
//   masked by a footer. TWO FOOTER TRAPS to respect: sample 1 "block CC" is NOT a credit-card
//   signal, and sample 24 "Avl Bal" footer must NOT mask the UPI debit (UPI fires first).
//   Keep "avl bal"/"available balance" (BALANCE_ALERT) distinct from "avl limit"/"available
//   limit" (a CREDIT-CARD signal) — never collapse the "avl " prefix.
{
  "rules": [
    { "reason": "OTP",              "any": ["otp", "one time password", "do not share"] },
    { "reason": "DECLINED",         "any": ["declined", "was declined"] },
    { "reason": "OFFER",            "any": ["% off", "cashback", "offer", "t&c apply"] },
    { "reason": "FUTURE_AUTO_DEBIT","any": ["will be auto debited", "e-mandate", "auto-debit", "auto debit"] },
    { "reason": "EMI_CONVERSION",   "any": ["converted to emi", "emi of"] },
    { "reason": "FEE_OR_CHARGE",    "any": ["finance charge", "late payment fee", "gst"] },
    { "reason": "CARD_PAYMENT",     "any": ["payment of", "received towards"], "withCard": true },
    { "reason": "BILL_DUE",         "any": ["bill of", "is due", "minimum amount due"] },
    { "reason": "INSURANCE",        "any": ["insurance", "premium", "policy"] },
    { "reason": "INVESTMENT",       "any": ["sip", "mutual fund", "folio", "large cap"] },
    { "reason": "DEBIT_CARD",       "any": ["debit card"] },
    { "reason": "UPI_BANK_ACCOUNT", "any": ["upi", "@ok", "/p2a/", "vpa"] },         // before BALANCE_ALERT (sample 24)
    { "reason": "SALARY_CREDIT",    "any": ["salary"] },
    { "reason": "BALANCE_ALERT",    "any": ["avl bal", "available balance"],         // before SAVINGS_ACCOUNT (sample 11)
                                    "unless": ["spent", "debited", "credited", "auto debited", "payment of"] },
    { "reason": "SAVINGS_ACCOUNT",  "any": ["a/c", "acct", "account"], "notCreditCard": true }  // generic fallback — LAST
  ]
}
```
*This order was validated against all 25 samples: e.g. #24 (UPI + "Avl Bal") → `UPI_BANK_ACCOUNT`, #11 (pure balance) → `BALANCE_ALERT`, #1 ("block CC" footer, account send) → `SAVINGS_ACCOUNT`, while #2/#5 (credit-card "avl limit") are never caught by `BALANCE_ALERT`. Phase 3 must re-confirm against the oracle.*
```jsonc
// currencies.json — C7
{ "currencies": [
    { "code": "INR", "tokens": ["inr", "rs", "rs.", "₹"] },
    { "code": "USD", "tokens": ["usd", "$"] },
    { "code": "EUR", "tokens": ["eur", "€"] },
    { "code": "AED", "tokens": ["aed"] } ] }
```
```jsonc
// dates.json — input format → ISO. Two-digit years → 20YY.
{ "formats": ["dd/MM/yy", "dd-MM-yyyy", "dd-MM-yy", "dd-MMM-yy", "dd-MMM-yyyy"] }
```
```jsonc
// products.json (owned by 2B; SEEDED in Phase 1) — credit-card signal tokens vs non-card tokens.
// Consumed by the shared CardSignal helper, which feeds BOTH the ExclusionEngine qualifiers
// (withCard/notCreditCard) AND the InclusionClassifier. NOTE the deliberate split below:
// "avl limit"/"available limit" are CARD signals; "avl bal"/"available balance" are NOT here —
// they belong to BALANCE_ALERT in exclusion-rules.json.
{ "creditCardSignals": ["credit card", "avl limit", "available limit", "avl lmt", "credit limit", "foreign currency markup"],
  "nonCardSignals":   ["debit card", "a/c", "acct", "savings", "upi"] }
```
```jsonc
// merchants.json — extraction hints + cleanup (merchant perfection is NOT graded).
{ "atPrepositions": ["at", "to", "with"], "stripSuffixes": ["pvt", "ltd", "in"], "stripCity": true }
```

---

## Appendix B — Sample oracle (expected outputs)

**Test oracle only. Drive golden/E2E tests and tuning from this. NEVER embed it in the parser (anti-cheat).** Confidence column is a *target band*, not an exact value. Aggregate: **7 INCLUDE, 18 EXCLUDE; INR debit ≈ ₹5,455 (samples 2+5+7+8+9); INR credit/refund ≈ ₹450 (sample 21); sample 22 is USD and excluded from INR totals.**

| # | Decision | Reason / Type | Bank (issuer) | Amount | Cur | Card | Merchant | Date | Conf | Why |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | EXCLUDE | SAVINGS_ACCOUNT | — | — | — | — | — | — | ~0.8 | "Sent … From … A/C … To BIGBASKET"; account debit. "block CC" footer is a trap, not a card spend. |
| 2 | INCLUDE | DEBIT | HDFC Bank | 1250.0 | INR | 5678 | SWIGGY | 2026-04-03 | ~0.95 | Explicit credit-card spend. |
| 3 | EXCLUDE | UPI_BANK_ACCOUNT | — | — | — | — | — | — | ~0.92 | Account debit credited to UPI VPA. |
| 4 | EXCLUDE | SALARY_CREDIT | — | — | — | — | — | — | ~0.9 | Salary credited to A/c. |
| 5 | INCLUDE | DEBIT | Axis Bank | 320.0 | INR | 9876 | AMAZON | 2026-04-06 | ~0.85 | "Card" + "Available Limit" ⇒ credit card. |
| 6 | EXCLUDE | DEBIT_CARD | — | — | — | — | — | — | ~0.95 | Explicit Debit Card. |
| 7 | INCLUDE | DEBIT | Yes Bank | 1200.0 | INR | 8888 | AMAZON | 2026-04-07 | ~0.93 | Explicit credit-card spend. |
| 8 | INCLUDE | DEBIT | **Federal Bank** | 1836.0 | INR | 4422 | HOSPITALITY PVT DELHI (messy) | 2026-04-07 | ~0.82 | Co-brand: Edge/Jupiter → **Federal Bank** issuer. |
| 9 | INCLUDE | DEBIT | **Bank of Baroda / BOBCARD** | 849.0 | INR | 9907 | Blackwater Coffee | 2026-04-08 | ~0.82 | Co-brand: BOBCARD One → BoB/BOBCARD. |
| 10 | EXCLUDE | OTP | — | — | — | — | — | — | ~0.97 | OTP. |
| 11 | EXCLUDE | BALANCE_ALERT | — | — | — | — | — | — | ~0.95 | Available balance. |
| 12 | EXCLUDE | BILL_DUE | — | — | — | — | — | — | ~0.95 | Bill due alert. |
| 13 | EXCLUDE | OFFER | — | — | — | — | — | — | ~0.95 | Promotional. |
| 14 | EXCLUDE | FUTURE_AUTO_DEBIT | — | — | — | — | — | — | ~0.9 | "will be auto debited" — future, even though credit card. |
| 15 | EXCLUDE | DECLINED | — | — | — | — | — | — | ~0.95 | Declined transaction. |
| 16 | EXCLUDE | INVESTMENT | — | — | — | — | — | — | ~0.92 | SIP / mutual fund from A/c. |
| 17 | EXCLUDE | EMI_CONVERSION | — | — | — | — | — | — | ~0.85 | Existing spend converted to EMI — counting again double-counts. |
| 18 | EXCLUDE | FEE_OR_CHARGE | — | — | — | — | — | — | ~0.92 | Finance charge + GST. |
| 19 | EXCLUDE | CARD_PAYMENT | — | — | — | — | — | — | ~0.93 | Card bill payment received (not a spend). |
| 20 | EXCLUDE | UPI_BANK_ACCOUNT | — | — | — | — | — | — | ~0.95 | UPI from A/c. |
| 21 | INCLUDE | **REFUND** | HDFC Bank | 450.0 | INR | 5678 | BIGBASKET | 2026-04-12 | ~0.9 | Refund credited to credit card. |
| 22 | INCLUDE | DEBIT | Axis Bank | 49.99 | **USD** | 9876 | NETFLIX.COM/US | 2026-04-13 | ~0.85 | Foreign-currency card spend; excluded from INR totals. |
| 23 | EXCLUDE | INSURANCE | — | — | — | — | — | — | ~0.9 | Insurance premium from A/c. |
| 24 | EXCLUDE | UPI_BANK_ACCOUNT | — | — | — | — | — | — | ~0.95 | UPI from A/c (to NETFLIX-MONTHLY, but still account/UPI). |
| 25 | EXCLUDE | MALFORMED_SMS | — | — | — | — | — | — | ~0.1 | Truncated "Spent Rs. 2,4". |

---

## Appendix C — Required unit tests → sample mapping

From [docs/Testing.md](docs/Testing.md); implemented in Phase 5 (some already covered in Phase 2).

| # | Required category | Drive with sample | Assert |
| --- | --- | --- | --- |
| 1 | Clear credit-card spend | 2 | INCLUDE/DEBIT, HDFC, 5678, SWIGGY, 1250 INR, date, conf ≥0.9 |
| 2 | Debit-card exclusion | 6 | EXCLUDE/DEBIT_CARD |
| 3 | OTP exclusion | 10 | EXCLUDE/OTP |
| 4 | UPI/savings exclusion | 20 (or 3) | EXCLUDE/UPI_BANK_ACCOUNT |
| 5 | Fintech/co-branded issuer | 8 (& 9) | bank = Federal Bank (& BOBCARD/BoB), resolved from body |
| 6 | Refund | 21 | INCLUDE/REFUND |
| 7 | Foreign currency | 22 | INCLUDE, currency USD, excluded from INR totals |
| 8 | Malformed | 25 | EXCLUDE/MALFORMED_SMS, transaction null, conf ≈0.1 |
| + | Config extensibility | synthetic | new bank added via JSON only → resolves |
| + | Conservative bias | synthetic | ambiguous bare "Card", no limit-language → not a confident INCLUDE |
