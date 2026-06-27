# Functions — Parser Spec & Functional Requirements

> The functional/technical specification of the parser. See [Assignment-Index.md](Assignment-Index.md) for all documents.

## Kotlin native parser module

Expose one native method to JavaScript:

```
parseSms(samples: Array<String>): Array<ParsedResult>
```

The parsing must happen in Kotlin, not JavaScript.

Your React Native code may load samples.json, extract the text field from each sample, and pass those SMS strings to the Kotlin parser.

## Parsed result schema

Each SMS should return a result in this shape:

```json
{
  "rawSms": "string",
  "decision": "INCLUDE | EXCLUDE",
  "excludeReason": "string | null",
  "transaction": {
    "amount": 1250.0,
    "currency": "INR",
    "bank": "HDFC Bank",
    "cardLastFour": "5678",
    "merchant": "SWIGGY",
    "type": "DEBIT | CREDIT | REFUND",
    "date": "2026-04-03"
  },
  "confidence": 0.92
}
```

## Field rules

### decision
Use INCLUDE only for completed credit-card transactions that are relevant to spend/transaction analysis.
Use EXCLUDE for anything that should not be counted.

### excludeReason
Required when decision = EXCLUDE.
Do not return a generic reason like COULD_NOT_PARSE unless the message is genuinely malformed or insufficient.

Prefer specific reasons such as:

```
OTP
DEBIT_CARD
SAVINGS_ACCOUNT
UPI_BANK_ACCOUNT
BALANCE_ALERT
BILL_DUE
OFFER
FUTURE_AUTO_DEBIT
DECLINED
EMI_CONVERSION
FEE_OR_CHARGE
CARD_PAYMENT
INVESTMENT
INSURANCE
MALFORMED_SMS
LOW_CONFIDENCE
```

You may add your own reason codes if needed, but keep them clear and consistent.

### transaction
Only present when decision = INCLUDE.

Rules:

- amount should be numeric.
- currency must be detected from the SMS. Do not assume INR.
- bank must be the resolved issuer bank, not the sender ID.
- cardLastFour should be extracted when available.
- merchant should be extracted as cleanly as possible.
- date should be ISO format, YYYY-MM-DD, when extractable. Otherwise return null.
- type should be one of: DEBIT, CREDIT, REFUND.

Use DEBIT for card spends/purchases.
Use REFUND for merchant refunds/reversals.
Use CREDIT only for relevant credit-card credits, if clearly identifiable.
Credit-card bill payments should generally be excluded as CARD_PAYMENT, not counted as spend.

### confidence
Required for every result.
Use a number between 0.0 and 1.0.
This should represent how sure your parser is about the decision and extracted fields.

Examples:

- Clear credit-card spend: high confidence
- Clear OTP: high confidence exclusion
- Clear debit-card transaction: high confidence exclusion
- Clear UPI/savings-account debit: high confidence exclusion
- Refund on a credit card: high confidence inclusion
- Malformed SMS: low confidence exclusion
- Ambiguous bank/product: lower confidence

## Hard requirements

### 1. Parsing must happen in Kotlin
The core parser, classification logic, bank detection, amount extraction, and confidence scoring should live in Kotlin.
React Native should only call the native module and render results.

### 2. Bank detection must read the SMS body
Do not rely only on sender ID.
We will specifically evaluate this using fintech/co-branded card examples.

For example:

- Jupiter/Edge Federal Bank card should resolve to Federal Bank.
- BOBCARD One should resolve to Bank of Baroda / BOBCARD, depending on your model.
- Sender/app branding should not override the issuer bank mentioned in the SMS body.

### 3. Config-driven design is required
The list of bank patterns, product patterns, exclusion rules, merchant extraction rules, and currency patterns should be configurable.
Adding a new bank should not require rewriting parser logic.

Acceptable approaches include:

- Kotlin data classes containing config
- JSON config bundled with the app
- rule objects loaded into the parser

Avoid one giant hardcoded regex block that only works for the 25 provided samples.

### 4. Exclusions matter as much as inclusions
This assignment is primarily testing whether you can avoid false positives.

A good parser should exclude:

- debit-card spends
- UPI spends from savings/current accounts
- OTPs
- account balance alerts
- salary credits
- bill due alerts
- card payment receipts
- offers
- declined transactions
- upcoming/future debits
- fees/finance charges
- insurance/investment debits
- malformed SMS

### 5. Currency must be detected
Do not assume every transaction is INR.

Some SMS messages may contain:

```
INR
Rs
USD
EUR
AED
```

For this assignment, support at least the currencies appearing in the provided samples.

### 6. Malformed SMS handling
Some samples are intentionally incomplete or malformed.
Handle them conservatively.

A malformed message should usually be:

```json
{
  "decision": "EXCLUDE",
  "excludeReason": "MALFORMED_SMS",
  "transaction": null,
  "confidence": 0.1
}
```

Exact confidence is up to you, but the decision should fail safely.
