# Build Plan v2 — Liquid Glass UI · File Import · Contextual Engine

> **This document is a self-contained prompt for an agent orchestrator.** It assumes
> no prior conversation. Read §0–§5 fully before writing any code, then execute the
> workstreams in §6 honouring the parallelization plan in §8. Tick `Progress.md` as
> you go. The original v1 build plan is [buildphase.md](buildphase.md); this plan is
> additive to it and must not regress it.

---

## 0. How to use this document

You are extending a **completed, passing** React Native (Android) + native-Kotlin app
that parses Indian bank SMS into credit-card transactions. v1 is done and green; do
**not** rebuild it. You are adding four things on top:

1. An **iOS 26 "Liquid Glass"** visual redesign of the React Native screen.
2. A **file import** flow so the user can load more SMS at runtime.
3. **Spaced month-name date** parsing in the Kotlin extractor.
4. A **contextual engine** (stateful, additive) that threads related messages and
   canonicalises merchants / flags recurring ones — exposed through a **new** native
   method, leaving the graded `parseSms` path untouched.

Work phase-by-phase. After each workstream, run its exit checks (§6) and update
[Progress.md](Progress.md). Never mark a workstream done until every exit criterion is met.

---

## 1. Mission & prime directive

**Prime directive — do not regress the assignment.** The app is an assignment graded on a
fixed contract:

- `parseSms(samples: Array<String>): Array<ParsedResult>` is a **pure, stateless, 1:1,
  order-independent** map. It must keep returning byte-identical output for the 25 provided
  samples (the golden oracle in `ParserGoldenTest`). Every change below is **additive**.
- All parsing/classification/scoring stays in **Kotlin** (C1). React Native only calls the
  bridge and renders.
- The result schema in [docs/Functions.md](docs/Functions.md) is frozen and snapshot-tested
  (`FieldNameSnapshotTest`, `ResultMapper`). Do **not** add, rename, or reorder its keys. The
  contextual engine uses a **separate** schema (§7).

If any task here would change `parseSms` output for the 25 samples, you have misread the
plan — stop and re-read §3.

---

## 2. Read-first (load this context before coding)

| Read | Why |
| --- | --- |
| [CLAUDE.md](CLAUDE.md) | Project rules; Kotlin-does-parsing; config-driven; conservative ethos. |
| [buildphase.md](buildphase.md) | v1 plan + the authoritative **cross-cutting principles C1–C9** and the sample oracle. Honour C1–C9 verbatim. |
| [docs/Functions.md](docs/Functions.md) | Parser API, frozen result schema, field rules, hard requirements. |
| [docs/UI-Requirements.md](docs/UI-Requirements.md) | What the screen must show (summary header, result rows, detail modal). The redesign must still satisfy all of this. |
| [docs/SMS-Examples.md](docs/SMS-Examples.md) | The 25 sample inputs. Use these + the new card-based cases in §6 WS-7 as your test corpus. |
| [docs/Testing.md](docs/Testing.md) | Required unit tests + hidden-sample rules. |
| [Progress.md](Progress.md) | Live checklist — extend it with a v2 section and tick as you go. |

**Mine for reuse (do not copy wholesale — curate):**
[Moneyprism-main/lib/core/parser/india/constants/categories.dart](Moneyprism-main/lib/core/parser/india/constants/categories.dart)
is a ready-made keyword→category seed (19 categories: food, groceries, fuel, travel, shopping,
entertainment, utilities, recharge, insurance, healthcare, education, investment, transfer…).
Port it into a JSON config (§6 WS-2), keeping our conservative posture — drop ultra-short
ambiguous tokens.

---

## 3. Decisions already made (do NOT re-litigate)

These were decided with the product owner. Implement them as stated.

| # | Decision | Rationale |
| --- | --- | --- |
| D1 | **Contextual engine is an additive sidecar.** Keep `parseSms` stateless & spec-compliant. Build the engine behind a **new** native method (`parseSmsSession`) fed by the file-import flow. | Stateful enrichment would break the graded 1:1 contract and change OTP=EXCLUDE behaviour. |
| D2 | **Keep all three exclusions** — `SALARY_CREDIT`, `INVESTMENT`, `INSURANCE`. Do **not** remove them. Additionally **add tests** proving they are load-bearing for *card-based* insurance/investment (see WS-7). | They guard hidden card-based cases ("premium spent on your Credit Card") and yield the spec-preferred specific reason codes over a generic `SAVINGS_ACCOUNT`/`LOW_CONFIDENCE`. |
| D3 | **Full iOS 26 Liquid Glass fidelity**, realised with real native blur. Single-screen layout (no full tab-bar app) + a floating glass toolbar and an iOS segmented control to switch **Messages ⇄ Insights**. | Product owner chose maximum fidelity; chose against a multi-tab restructure. |
| D4 | **Contextual features in scope:** (a) **lifecycle threading**, (b) **merchant canonicalisation + recurring detection**. Out of scope this build: dedup/refund-netting, LLM fallback, confidence recalibration, per-card ledger, anomaly/velocity. | Scope control. The engine must **not** alter the core 5 result fields — it only **adds** enrichment fields. |

---

## 4. Cross-cutting principles for v2 (in addition to C1–C9 from buildphase.md)

- **V1 — Additive only.** No change may alter `parseSms` output for the 25 samples. The
  golden oracle test is the gate.
- **V2 — Kotlin owns the logic (extends C1).** Threading, canonicalisation, and recurring
  detection are **Kotlin**. RN renders; it does not correlate or classify.
- **V3 — Config-driven (extends C5).** New knowledge (merchant→canonical/category map, extra
  date formats, threading windows) lives in JSON under `assets/parser-config/`, loaded via
  `JsonConfigParser`. Adding a merchant alias is a JSON edit, not a code change.
- **V4 — Conservative correlation (extends C2).** Thread two messages only on a **strong**
  key match (card-last-four **and** amount **and** a tight time/merchant compatibility). When
  unsure, leave them unlinked. A wrong link is worse than no link.
- **V5 — Graceful platform fallback.** Real glass requires API 33+; below that, fall back to
  solid translucent surfaces. The app must look correct and run on `minSdk 23`.
- **V6 — Separate schema for enrichment.** The session API returns its own shape (§7); never
  bend the frozen `ParsedResult` schema to fit it.

---

## 5. Current architecture (the files you'll touch)

**Native Kotlin parser** (`android/app/src/main/java/com/cashkaro/smsparser/`):
- `parser/SmsParser.kt` — pure pipeline orchestrator: `normalize → malformed gate → exclusion
  → inclusion → extract → score`. Built via `SmsParser.create(config)`. **Leave behaviour
  unchanged.**
- `parser/extract/DateExtractor.kt` — **WS-1 touches this** (candidate regex at the
  `candidate` field; formats injected from config).
- `parser/classify/ExclusionEngine.kt` + `assets/parser-config/exclusion-rules.json` — ordered,
  config-driven, first-match-wins. **WS-7 adds tests only; no rule removal.**
- `parser/config/ParserConfig.kt`, `parser/config/JsonConfigParser.kt`,
  `AssetConfigSource.kt` — config model + loader (reads `assets/parser-config/*.json`).
  **WS-2 extends these** for the new merchant-category config.
- `parser/model/ParsedResult.kt`, `parser/model/Transaction.kt`, `parser/ResultMapper.kt` —
  frozen schema + JS map. **Do not modify.** Add a *new* mapper for enriched results.
- `SmsParserModule.kt` — the React bridge (`@ReactMethod parseSms`). **WS-3 adds a second
  `@ReactMethod parseSmsSession` here** (same module; reuse the lazily-built `parser`).
- `SmsParserPackage.kt` — module registration (no change unless you add a new module).

**Config** (`android/app/src/main/assets/parser-config/`): `banks.json`, `card-products.json`,
`products.json`, `currencies.json`, `merchants.json`, `dates.json`, `exclusion-rules.json`.
**WS-1 edits `dates.json`; WS-2 adds `merchant-categories.json`.**

**Tests** (`android/app/src/test/java/com/cashkaro/smsparser/`): JVM unit tests incl.
`ParserGoldenTest` (25-sample oracle — the regression gate), `DateExtractorTest`,
`ExclusionEngineTest`, `RequiredCategoriesTest`, `FieldNameSnapshotTest`. Mirror this layout
for new tests.

**React Native** (`src/`): bare RN **0.74.5**, React 18.2, TypeScript. No Expo, no navigation
lib, no blur/animation libs yet.
- `screens/ParserScreen.tsx` — the single screen; calls `parseSms` on mount, renders header +
  list + modal.
- `components/` — `SummaryHeader.tsx`, `ResultRow.tsx`, `DetailModal.tsx`, `Chip.tsx`,
  `ConfidenceIndicator.tsx`.
- `native/SmsParser.ts` — typed bridge wrapper + `ParsedResult`/`Transaction` types.
- `theme.ts` — design tokens + `formatAmount`/`bankInitials`/`confidenceColor` helpers.
- Samples: `src/data/samples.json` (shape: `[{ "text": "..." }, …]`).

---

## 6. Workstreams

### WS-1 — Kotlin: spaced month-name dates
**Goal:** parse `03 Apr 2026`, `3 Apr 26`, `03 April 2026` in addition to the current
dash/slash formats. **Must not change the 25-sample golden output** (they all use `-`/`/`).

**Files:** `parser/extract/DateExtractor.kt`, `assets/parser-config/dates.json`,
`test/.../extract/DateExtractorTest.kt`.

**Tasks:**
1. Widen the `candidate` regex so a space-separated date is also a candidate token. Current:
   `\b\d{1,2}[-/][A-Za-z0-9]{1,3}[-/]\d{2,4}\b`. Add an alternative for
   `\b\d{1,2}\s+[A-Za-z]{3,9}\s+\d{2,4}\b` (keep both; do not break the existing one).
2. Add formats to `dates.json`: `"dd MMM yyyy"`, `"dd MMM yy"`, `"d MMM yyyy"`, `"dd MMMM yyyy"`.
   (`SimpleDateFormat` `MMM`→"Apr", `MMMM`→"April". Keep the existing 2000-pivot + strict-parse
   behaviour for two-digit years.)
3. Preserve "first valid date wins, left-to-right" and the `< MIN_YEAR` guard.

**Exit:** new `DateExtractorTest` cases for the three spaced shapes pass; **`ParserGoldenTest`
still green** (zero diffs on the 25); full Kotlin suite green.

---

### WS-2 — Kotlin: contextual engine (lifecycle threading + merchant canonicalisation/recurring)
**Goal:** a pure-Kotlin, stateful engine that takes an ordered batch of SMS *records* and
returns enriched results + cross-message rollups. **Reuses** the existing stateless
`SmsParser` for the per-message parse; layers correlation + enrichment on top.

**New files (suggested):**
`parser/session/ContextualEngine.kt`, `parser/session/TransactionThreader.kt`,
`parser/session/MerchantCanonicalizer.kt`, `parser/session/model/*.kt`,
`assets/parser-config/merchant-categories.json`, plus `ParserConfig`/`JsonConfigParser`
extensions and tests under `test/.../session/`.

**Tasks:**
1. **Input record** (Kotlin side): `SmsRecord(text: String, receivedAt: Long?, sender: String?)`.
   `receivedAt` is epoch millis from the SMS inbox; **null when unknown** (e.g. imported files) —
   then fall back to in-body date, else input order. Never require it.
2. **Per-message parse:** run each record's `text` through the existing `SmsParser` to get the
   exact `ParsedResult` (core fields untouched, per V1/V6).
3. **Merchant canonicalisation (config-driven):** port the keyword map from Moneyprism's
   `categories.dart` into `merchant-categories.json` as
   `[{ "canonical": "Netflix", "category": "entertainment", "tokens": ["netflix"] }, …]`.
   `MerchantCanonicalizer` maps a raw merchant/SMS body → `{ canonical, category }` (first-match,
   conservative; null when no token hits). Examples that must collapse: `NETFLIX.COM/US`,
   `NETFLIX-MONTHLY`, `NETFLIX_SUBSCRIPTION` → `Netflix`.
4. **Lifecycle threading:** `TransactionThreader` groups results into threads keyed on a
   **strong** signature — `cardLastFour + amount + canonicalMerchant` within a configurable time
   window (default e.g. 15 min when `receivedAt` present; same-day when only in-body dates exist).
   Link the lifecycle stages: `auth/OTP (if it carries amount+card) → spend confirmation →
   refund/reversal → EMI-conversion → bill`. Use the explicit back-references already in the
   corpus: sample 21 ("Refund … against original txn dated 02-04-26") links to sample 1/2;
   sample 17 ("Rs 75,000 spend … converted to EMI") links to the original spend. Conservative
   (V4): no strong key match ⇒ standalone thread of one.
5. **Recurring detection:** within a session, flag a canonical merchant as `recurring: true` when
   it appears ≥2× across threads, OR when it matches a known-subscription token set in config
   (Netflix, Prime, Spotify, etc.). Also surface a future-auto-debit (sample 14) as a recurring
   signal for its merchant.
6. **Do not** recalibrate confidence or change decisions (out of scope, D4). Enrichment fields
   are purely additive.

**Exit:** `ContextualEngineTest` proves (a) Netflix variants canonicalise to one merchant;
(b) sample 21 threads onto its original spend; (c) sample 17 threads onto its original spend;
(d) a recurring merchant is flagged; (e) two unrelated ₹-equal spends with different card4 do
**not** merge (V4). The engine never changes the underlying `ParsedResult` core fields.

---

### WS-3 — Bridge: `parseSmsSession` native method + enriched JS schema
**Goal:** expose the engine to React Native without touching the `parseSms` method or schema.

**Files:** `SmsParserModule.kt` (add method), a new `parser/session/SessionResultMapper.kt`
(separate from `ResultMapper`), `src/native/SmsParser.ts` (add typed wrapper + types).

**Tasks:**
1. Add `@ReactMethod fun parseSmsSession(records: ReadableArray, promise: Promise)` to
   `SmsParserModule`. Each element is a map `{ text, receivedAt?, sender? }`. Reuse the lazily
   built `parser`; construct the `ContextualEngine` once (lazy) too. Iterate by `size()` (C6).
   Never throw across the bridge (C8) — `promise.reject` on error.
2. `SessionResultMapper` converts the engine output (§7) to a `WritableMap`/`WritableArray`,
   reusing the existing generic `toWritableMap` transcription pattern. **Keep `ResultMapper`'s
   five frozen keys exactly as-is** for the embedded core result.
3. In `src/native/SmsParser.ts`: add `parseSmsSession(records)` + the `EnrichedResult`,
   `Thread`, `MerchantSummary`, `SessionResult` types (§7). Keep the existing `parseSms`
   wrapper + types unchanged.

**Exit:** a JVM test asserts the session map's key set; the JS types compile; the existing
`FieldNameSnapshotTest` for the core schema still passes.

---

### WS-4 — React Native: iOS 26 Liquid Glass redesign
**Goal:** restyle the screen to Apple's iOS 26 *Liquid Glass* language (translucent floating
control layer over a scrolling content layer; hierarchy / harmony / consistency), with **real
blur on Android** and a graceful fallback.

**Dependencies to add (Android-capable — verified):**
- **Blur/glass:** `@sbaiahmed1/react-native-blur` (cross-platform native blur incl. a
  `liquidGlass` mode) **or** `uginy/react-native-liquid-glass` (AGSL GPU shaders, real
  refraction, **Android API 33+**). Pick one; wrap it in a single `<Glass>` component so the
  rest of the UI is library-agnostic.
  ⚠️ **Do NOT use `@callstack/liquid-glass` or `expo-glass-effect` as the Android path** — they
  render real glass only on **iOS 26+** and fall back to a plain opaque `View` on Android, which
  is this app's only target.
- **Motion:** `react-native-reanimated` (v3) + `react-native-gesture-handler` for fluid spring
  transitions and the morphing toolbar.
- **Structure:** `react-native-safe-area-context` for notch/inset-correct floating layers.
- After install: `cd android && ./gradlew` autolinks; add reanimated's Babel plugin to
  `babel.config.js`; rebuild the dev client (`yarn android`). Document any `MainApplication`/
  Gradle edits.

**Tasks:**
1. **Fallback strategy (V5):** the `<Glass>` wrapper detects support (`Platform.Version >= 33`
   for AGSL, else the blur lib's own capability) and renders a **solid translucent** surface
   (e.g. `rgba(250,250,252,0.72)` light / `rgba(28,28,30,0.72)` dark + hairline border) below
   API 33 so the app still looks intentional on `minSdk 23`.
2. **Token overhaul** in `theme.ts`: iOS system palette (systemBackground, secondarySystem…,
   label/secondaryLabel, separators), iOS type scale (Large Title 34 / Title 28 / Body 17 /
   Footnote 13; system font — note SF Pro is not licensable for Android bundling, so use the
   platform default or bundle Inter as an SF-like stand-in), 8-pt spacing, **concentric** corner
   radii (outer ≈ 20–28, nested smaller), iOS shadow/elevation. Support **light & dark** (Liquid
   Glass is adaptive).
3. **Floating glass control layer (D3):** a top floating glass **toolbar** (title + Import
   button) and a glass **segmented control** ("Messages" / "Insights") that sits above the
   scrolling list. The list content scrolls and peeks through the glass (the defining iOS-26
   content/controls separation).
4. **Restyle components as glass cards:** `SummaryHeader` (still shows the
   [docs/UI-Requirements.md](docs/UI-Requirements.md) numbers — included count, excluded count,
   INR debit total, INR credit/refund total, top exclusions), `ResultRow` (included = bank-initial
   avatar + merchant + amount/currency + date + type + confidence; excluded = dimmed + reason chip
   + preview + confidence), `DetailModal` as an iOS sheet with a grabber + spring present/dismiss.
5. Respect `prefers-reduced-motion` / `AccessibilityInfo.isReduceMotionEnabled` — tone down
   springs when set.

**Exit:** screen builds and runs on a real device/emulator; summary + list + detail modal all
satisfy [docs/UI-Requirements.md](docs/UI-Requirements.md); glass renders on API 33+, falls back
cleanly below; light + dark both legible; no horizontal overflow.

---

### WS-5 — React Native: file import ("add more SMS")
**Goal:** let the user pick a file and append its SMS to the rendered set, feeding the contextual
engine.

**Dependency:** `react-native-document-picker` (or `@react-native-documents/picker`). Read the
file with `react-native-fs` (or the picker's built-in read). No new permissions needed for
user-initiated SAF picks on Android.

**Tasks:**
1. An **Import** action in the floating glass toolbar opens the system picker.
2. **Accept two formats** and detect by content/extension:
   - `.json` — the `samples.json` shape `[{ "text": "..." }, …]` (also accept a bare
     `["...", …]` array). Optionally honour `receivedAt`/`sender` keys if present → richer
     threading.
   - `.txt` — one SMS per line (blank lines skipped).
3. Map to `SmsRecord[]` and call **`parseSmsSession`** (not `parseSms`) so imported messages get
   threaded/enriched alongside the bundled samples. Append to state; show a count toast
   ("Imported 12 messages").
4. Conservative parsing of the file: malformed file ⇒ non-blocking error banner, never crash.
   Cap very large imports (e.g. 5 000 lines) with a visible notice (no silent truncation).

**Exit:** importing a sample `.json` and a sample `.txt` both render new rows; the engine threads
imported messages with the bundled ones; bad files show a friendly error.

---

### WS-6 — React Native: Insights view (threads · merchants · recurring)
**Goal:** surface what the contextual engine adds, behind the "Insights" segment (D3).

**Tasks:**
1. **Messages segment** = the existing per-SMS list, now with an enrichment line on included rows
   (canonical merchant + category chip; a "recurring" badge where flagged). Core fields stay as
   parsed.
2. **Insights segment:**
   - **Threads:** grouped cards — primary transaction + its linked lifecycle events (auth →
     spend → refund/EMI/bill), with the net amount per thread.
   - **Merchants:** canonical-merchant rollups (count, total spend, category).
   - **Recurring:** merchants flagged recurring/subscription.
3. Tapping any item opens the same iOS sheet detail (raw SMS, decision, reason, fields,
   confidence, + thread/enrichment info).

**Exit:** with the 25 samples (+ an imported file), the Insights segment shows ≥1 multi-message
thread (sample 21 onto its spend), the Netflix rollup as one merchant, and a recurring flag.

---

### WS-7 — Exclusions: keep all three + prove they're load-bearing (D2)
**Goal:** lock the decision with tests; **remove nothing**.

**Files:** `test/.../classify/ExclusionEngineTest.kt` and/or `RequiredCategoriesTest.kt`.

**Tasks:** add cases for **card-based** insurance/investment, e.g.
`"Rs 12,500 spent on your HDFC Bank Credit Card xx5678 for HDFC Life Insurance Premium"` ⇒
EXCLUDE `INSURANCE` (not INCLUDE/DEBIT), and a card-based SIP/investment ⇒ EXCLUDE `INVESTMENT`.
Add a comment citing D2 so a future reader doesn't "simplify" these away.

**Exit:** new tests green; the three rules remain in `exclusion-rules.json`.

---

### WS-8 — Tests
- Kotlin: WS-1, WS-2, WS-3, WS-7 tests above. **`ParserGoldenTest` (25-sample oracle) must show
  zero diffs** — this is the regression gate for the whole v2.
- Keep the full existing suite green (`./gradlew test` / `testDebugUnitTest`).
- RN: a smoke test that `ParserScreen` renders the header from a mocked `parseSms`, and that the
  import handler maps `.json`/`.txt` to records. Mock the native module.

**Exit:** `./gradlew test` green; `yarn test` green; `yarn lint` clean.

---

### WS-9 — Docs & Progress
- Add a **v2 section** to `Progress.md` mirroring WS-1…WS-8 and tick it.
- Update the README: new "Contextual engine (beyond the assignment)" + "Liquid Glass UI" +
  "Import your own SMS" sections; note the API-33 glass requirement + fallback; keep the existing
  assignment-compliance and production-Android notes intact. State clearly that `parseSms` is
  unchanged and remains the graded entry point.

**Exit:** README + Progress.md updated; a reader can tell what is assignment-core vs. v2-additive.

---

## 7. Session API schema (new — separate from the frozen `ParsedResult`)

**Input (JS → native):**
```ts
type SmsRecord = { text: string; receivedAt?: number /* epoch ms */; sender?: string };
parseSmsSession(records: SmsRecord[]): Promise<SessionResult>;
```

**Output (native → JS):**
```ts
interface EnrichedResult extends ParsedResult {   // the 5 core keys are byte-identical to parseSms
  receivedAt: number | null;
  threadId: string | null;
  merchantCanonical: string | null;               // e.g. "Netflix"
  category: string | null;                         // e.g. "entertainment"
  recurring: boolean;
  linkedTo: string[] | null;                       // ids of corroborating messages in the thread
}
interface Thread {
  threadId: string;
  card4: string | null;
  merchantCanonical: string | null;
  netAmount: number;                               // spend minus refunds within the thread
  events: EnrichedResult[];                         // ordered: auth → spend → refund/EMI/bill
}
interface MerchantSummary { canonical: string; category: string | null; count: number; totalSpend: number; recurring: boolean; }
interface SessionResult { results: EnrichedResult[]; threads: Thread[]; merchants: MerchantSummary[]; }
```
`results` stays **1:1 and in input order** (so the Messages segment is a drop-in for the current
list). `threads`/`merchants` are the cross-message rollups for the Insights segment.

---

## 8. Parallelization plan

```
Phase A (parallel):
  ├─ WS-1  spaced dates            (Kotlin, isolated)
  ├─ WS-7  exclusion proof tests   (Kotlin, isolated)
  └─ WS-4  Liquid Glass deps+theme (RN, isolated)         ← longest pole; start early

Phase B (after A):
  ├─ WS-2  contextual engine       (Kotlin; depends on stable parse from WS-1)
  └─ WS-4  glass components        (RN; finish restyle of existing components)

Phase C (after B):
  ├─ WS-3  bridge parseSmsSession  (depends on WS-2 engine + WS-2 schema)
  └─ WS-5  file import             (RN; depends on WS-3 method existing — can stub the type first)

Phase D (after C):
  └─ WS-6  Insights view           (RN; depends on WS-3 data + WS-4 glass + WS-5 import)

Phase E:
  └─ WS-8 tests sweep + WS-9 docs/Progress
```
WS-1, WS-7, and the WS-4 dependency/theme work have **no shared files** and can run as parallel
sub-agents. WS-2 → WS-3 → WS-5/WS-6 is the critical path.

---

## 9. References

**Assignment & repo:** `CLAUDE.md`, `buildphase.md` (C1–C9 + oracle), `docs/Functions.md`,
`docs/UI-Requirements.md`, `docs/Testing.md`, `docs/SMS-Examples.md`, `Progress.md`.

**Merchant/category seed:** `Moneyprism-main/lib/core/parser/india/constants/categories.dart`.

**iOS 26 Liquid Glass design:**
- Apple HIG — Materials: https://developer.apple.com/design/human-interface-guidelines/materials
- Apple Newsroom — the announcement: https://www.apple.com/newsroom/2025/06/apple-introduces-a-delightful-and-elegant-new-software-design/
- Hierarchy / Harmony / Consistency: https://www.createwithswift.com/liquid-glass-redefining-design-through-hierarchy-harmony-and-consistency/
- SwiftUI reference (concepts to mirror): https://github.com/conorluddy/LiquidGlassReference

**React Native realisation (mind the Android caveat):**
- Callstack guide — "How To Use Liquid Glass in React Native": https://www.callstack.com/blog/how-to-use-liquid-glass-in-react-native
- `@callstack/liquid-glass`: https://github.com/callstack/liquid-glass — ⚠️ real effect iOS-26-only; Android falls back to opaque View.
- `expo-glass-effect` (GlassView): https://docs.expo.dev/versions/latest/sdk/glass-effect/ — ⚠️ iOS-26-only.
- **`@sbaiahmed1/react-native-blur`** (cross-platform native blur + liquidGlass): https://github.com/sbaiahmed1/react-native-blur
- **`uginy/react-native-liquid-glass`** (AGSL GPU shaders on **Android API 33+**): https://github.com/uginy/react-native-liquid-glass

---

## 10. Definition of done / verification

1. `./gradlew test` (or `testDebugUnitTest`) — **green, with `ParserGoldenTest` showing zero
   diffs on the 25 samples.**
2. `yarn test` green; `yarn lint` clean; TypeScript compiles.
3. `yarn android` builds and runs on a device/emulator: Liquid Glass renders (API 33+) or falls
   back cleanly (API 23–32); Messages + Insights segments work; importing `.json` and `.txt`
   appends and threads.
4. Manual check vs [docs/UI-Requirements.md](docs/UI-Requirements.md): summary header numbers,
   included/excluded row styles, detail modal — all present.
5. `Progress.md` v2 section fully ticked; README updated.

## 11. Risks & gotchas

- **Golden regression (highest risk).** WS-1's regex must not capture anything new in the 25
  samples. Run `ParserGoldenTest` after every Kotlin change.
- **Android glass reality.** Real-time GPU blur is heavy and AGSL is API-33+. Don't stack many
  translucent layers (GPU stress); use the fallback below 33; test on a low-end device.
- **No SF Pro on Android.** Don't bundle SF Pro (licence). Use the platform default or Inter.
- **`receivedAt` is often absent** (imported files, assignment strings). Threading must degrade
  gracefully to in-body date / input order — never require a timestamp.
- **Keep the bridge non-throwing (C8)** and the core schema frozen (don't let `SessionResultMapper`
  leak new keys into the `parseSms` path).
- **Conservative threading (V4).** Prefer false-negatives (unlinked) over false-positives
  (wrongly merged) — same ethos as the parser itself.
```
