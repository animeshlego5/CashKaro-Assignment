# PRD — Bank SMS Parser

> Product Requirements for the take-home assignment. See [Assignment-Index.md](Assignment-Index.md) for all documents.

## Assignment metadata

| Field | Detail |
| --- | --- |
| Expected effort | 6-9 focused hours. If you cross 10 hours, stop and submit with a note in the README about what is incomplete and why. Time honesty is respected. |
| Deadline | 7 calendar days from receiving this assignment |
| Submission | GitHub repo link + screen recording link |
| Follow-up | 30-minute walkthrough call after review |

## What you're building

Build a React Native Android app with a native Kotlin module that parses real-world Indian bank SMS messages into structured credit-card transaction data.

The hard part is not writing a regex. The hard part is deciding what to exclude, handling ambiguity conservatively, and correctly attributing the issuer bank when fintech/co-branded cards are involved.

## Product context

We are building an Indian credit-card optimisation app. One of the core jobs is to understand how users are spending on their credit cards by analysing bank SMS messages.

In real usage, a large percentage of bank-looking SMS messages should not be counted as credit-card spends. Examples include:

- OTPs
- debit-card transactions
- savings-account debits
- UPI transactions
- bill due alerts
- credit-card bill payments
- offer/promotional messages
- finance charges/fees
- future auto-debit alerts
- declined transactions
- malformed or incomplete messages

The parser should be conservative under uncertainty.

When in doubt, exclude the SMS with a clear reason and low confidence. A parser that confidently outputs wrong data is worse than one that admits it is unsure.

## What is provided

You will receive:

- Starter doc with scaffolded React Native + Kotlin module: assignment-starter
- samples.json with 25 SMS samples (see [SMS-Examples.md](SMS-Examples.md))
- Working build setup

The app should build and launch with:

```
yarn install
yarn android
```

## Scope clarification

Use only the provided SMS samples.

- Do not request real SMS permissions from the device.
- Do not read the user's actual SMS inbox.
- Do not call any external API or server for parsing.
- Do not use an LLM at runtime.

We are evaluating parsing architecture, native Android/Kotlin capability, and reasoning under ambiguity - not production SMS permission integration.

## What we are testing

### 1. Filter recall
Did you correctly exclude messages that are not credit-card spends?
False positives are worse than false negatives in this assignment.

### 2. Bank attribution accuracy
Can you correctly identify issuer banks, especially for fintech/co-branded cards?

### 3. Kotlin/native Android ability
Can you build native Android logic and expose it cleanly to React Native?

### 4. Architecture extensibility
Can a new bank or rule be added without rewriting the parser?

### 5. Conservative reasoning
Does your parser behave safely when it is unsure?

### 6. Testing discipline
Are the core parser behaviours covered by unit tests?

### 7. Communication and ownership
Can you clearly explain your trade-offs, gaps, and design decisions?

## What we are not testing

- React Native UI polish
- iOS
- production persistence
- backend/network integration
- performance at large scale
- real SMS inbox access
- real Android permission implementation
- perfect merchant extraction

A simple, clean UI is enough.
