# Take-Home Assignment: Bank SMS Parser

| Expected effort | 6-9 focused hours. If you cross 10 hours, stop and submit with a note in the README about what is incomplete and why. Time honesty is respected. |
| --- | --- |
| Deadline | 7 calendar days from receiving this assignment |
| Submission | GitHub repo link + screen recording link |
| Follow-up | 30-minute walkthrough call after review |

# What you're building
Build a React Native Android app with a native Kotlin module that parses real-world Indian bank SMS messages into structured credit-card transaction data.
The hard part is not writing a regex. The hard part is deciding what to exclude, handling ambiguity conservatively, and correctly attributing the issuer bank when fintech/co-branded cards are involved.
# Product context
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
# What is provided
You will receive:
- Starter doc  with scaffolded React Native + Kotlin module: assignment-starter
- samples.json with 25 SMS samples
- Working build setup

The app should build and launch with:

```
yarn install
yarn android
```

# Scope clarification
Use only the provided SMS samples.
- Do not request real SMS permissions from the device.
- Do not read the user's actual SMS inbox.
- Do not call any external API or server for parsing.
- Do not use an LLM at runtime.

We are evaluating parsing architecture, native Android/Kotlin capability, and reasoning under ambiguity - not production SMS permission integration.

# What you need to build
## 1. Kotlin native parser module
Expose one native method to JavaScript:

```
parseSms(samples: Array<String>): Array<ParsedResult>
```

The parsing must happen in Kotlin, not JavaScript.
Your React Native code may load samples.json, extract the text field from each sample, and pass those SMS strings to the Kotlin parser.
## 2. Parsed result schema
Each SMS should return a result in this shape:

```
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

### Field rules
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

# Hard requirements
## 1. Parsing must happen in Kotlin
The core parser, classification logic, bank detection, amount extraction, and confidence scoring should live in Kotlin.
React Native should only call the native module and render results.
## 2. Bank detection must read the SMS body
Do not rely only on sender ID.
We will specifically evaluate this using fintech/co-branded card examples.
For example:
- Jupiter/Edge Federal Bank card should resolve to Federal Bank.
- BOBCARD One should resolve to Bank of Baroda / BOBCARD, depending on your model.
- Sender/app branding should not override the issuer bank mentioned in the SMS body.

## 3. Config-driven design is required
The list of bank patterns, product patterns, exclusion rules, merchant extraction rules, and currency patterns should be configurable.
Adding a new bank should not require rewriting parser logic.
Acceptable approaches include:
- Kotlin data classes containing config
- JSON config bundled with the app
- rule objects loaded into the parser

Avoid one giant hardcoded regex block that only works for the 25 provided samples.
## 4. Exclusions matter as much as inclusions
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

## 5. Currency must be detected
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
## 6. Malformed SMS handling
Some samples are intentionally incomplete or malformed.
Handle them conservatively.
A malformed message should usually be:

```
{
  "decision": "EXCLUDE",
  "excludeReason": "MALFORMED_SMS",
  "transaction": null,
  "confidence": 0.1
}
```

Exact confidence is up to you, but the decision should fail safely.
## 7. Hidden test samples
We will run your parser against ~10 additional hidden SMS samples that follow similar patterns to the provided 25, but with different wording, banks, and edge cases.
How we will run them: we will append them to samples.json and re-run your app. Make sure your parser accepts any list of strings, not just the provided IDs.
Weighting: performance on the hidden set carries roughly 15% of total evaluation weight - comparable to your performance on the provided 25.
Avoid:
- hardcoding sample IDs or specific strings from the provided samples
- regex patterns that only match the exact wording used in the visible 25
- relying on array length or order
- returning fixed expected outputs
- parsing only by matching entire SMS bodies

A parser that scores 25/25 visible + 0/10 hidden is worse than one that scores 22/25 visible + 8/10 hidden.

# React Native screen
Build one simple screen that calls parseSms(samples) on mount and renders the parsed output.
## 1. Summary header
Show:
- total INR debit amount
- total INR credit/refund amount
- number of included transactions
- number of excluded messages
- count by exclude reason

Example:

```
Included: 7
Excluded: 18
INR Debit: ₹xx,xxx
INR Credit/Refund: ₹x,xxx
Top Exclusions: UPI_BANK_ACCOUNT: 3, OTP: 1, BILL_DUE: 1
```

## 2. Result list
For each SMS, show a row.
### Included transaction rows
Show:
- bank logo or initials
- merchant
- amount
- currency
- date
- transaction type
- confidence indicator

Bank logo is optional. Initials are fine.
### Excluded SMS rows
Show:
- dimmed row style
- exclude reason as a chip/badge
- short SMS preview
- confidence indicator

## 3. Detail modal
On tapping a row, show a modal with:
- raw SMS
- decision
- exclude reason, if any
- parsed transaction fields, if any
- confidence

UI polish is not the main evaluation area. Clean, readable, and functional is enough.
# Kotlin unit tests
Add Kotlin unit tests for the parser.
You do not need heavy UI tests, but parser tests are required.
At minimum, include tests for:
1.	a clear credit-card spend
2.	a debit-card exclusion
3.	an OTP exclusion
4.	a UPI/savings-account exclusion
5.	a fintech/co-branded issuer attribution case
6.	a refund
7.	a foreign-currency transaction
8.	a malformed SMS
Document how to run the tests in the README.

# README requirements
The README will be evaluated heavily.
Include the following sections:
## 1. How to run
Include exact commands.
Example:

```
yarn install
yarn android
```

Also include how to run tests.
## 2. Parsing architecture
Explain:
- where the Kotlin parser lives
- how the React Native bridge calls it
- how exclusion logic is separated from extraction logic
- how bank detection works
- where config lives
- how a new bank or exclusion rule would be added
- why you structured it this way

## 3. Confidence scoring
Explain your confidence model.
For example:
- what makes a parse high confidence
- what lowers confidence
- what causes low-confidence exclusion
- how malformed SMS messages are handled

## 4. Samples you struggled with
Be specific.
Mention which samples were ambiguous or hard and why.
Honesty is rewarded here. We do not expect a perfect parser.
## 5. What you would do differently with a full week
Share your improvement plan.
Examples:
- better rule engine
- more robust merchant extraction
- stronger date parsing
- improved test coverage
- real SMS permission flow
- background parsing design
- telemetry for parser failures
- better config management

## 6. Production Android design note
In 500-800 words, explain how you would extend this prototype to parse real SMS from a user's device in production.
Cover:
- runtime SMS permission flow
- what happens if permission is denied
- Google Play policy risk around SMS permissions
- how you would parse only new/incremental SMS
- duplicate prevention
- background execution approach
- WorkManager / BroadcastReceiver / ContentObserver trade-offs
- latency budget for real-time user notifications
- app process death and retry behaviour
- OEM-specific background-kill behaviour on Indian Android devices
- privacy and local processing considerations

Assume the product may need to fire a real-time notification within 30 seconds of an SMS arriving.
Example:

```
"You used HDFC for grocery. ICICI would have earned ₹50 more."
```

Explain:
- what execution path can realistically meet this 30-second budget
- what execution paths cannot reliably meet this budget
- where the parser has to run for this to be possible
- what should happen if the app is killed, restricted, or background execution is delayed

Also specifically address Indian OEM behaviour.
Cover what happens on devices from:
- Xiaomi / MIUI / HyperOS
- Realme
- OPPO / ColorOS
- Vivo / FuntouchOS
- OnePlus / OxygenOS

Mention both:
- the technical mechanism, such as battery optimisation, autostart permissions, app standby buckets, Doze mode, and OEM background restrictions
- the user experience you would build to detect, explain, and recover from these restrictions

Do not implement this in the assignment. Just explain the design.
## 7. AI tool usage
We assume you may use AI tools.
We do not penalise AI usage. We do penalise code you cannot explain.
In the README, mention:
- which AI tools you used
- what prompts worked well
- what prompts failed
- what code AI wrote
- what code you changed or rewrote yourself
- what you verified manually

Be honest. We use AI heavily ourselves and want to see how you orchestrate it.

# Screen recording
Submit a screen recording of 2 minutes or less.
Show:
1.	app launch
2.	parsed summary
3.	scrolling through included and excluded rows
4.	opening one included transaction modal
5.	opening one excluded SMS modal
No voiceover required, but it is fine if you prefer to explain briefly.
# What we are not testing
We are not testing:
- React Native UI polish
- iOS
- production persistence
- backend/network integration
- performance at large scale
- real SMS inbox access
- real Android permission implementation
- perfect merchant extraction

A simple, clean UI is enough.
# What we are testing
## 1. Filter recall
Did you correctly exclude messages that are not credit-card spends?
False positives are worse than false negatives in this assignment.
## 2. Bank attribution accuracy
Can you correctly identify issuer banks, especially for fintech/co-branded cards?
## 3. Kotlin/native Android ability
Can you build native Android logic and expose it cleanly to React Native?
## 4. Architecture extensibility
Can a new bank or rule be added without rewriting the parser?
## 5. Conservative reasoning
Does your parser behave safely when it is unsure?
## 6. Testing discipline
Are the core parser behaviours covered by unit tests?
## 7. Communication and ownership
Can you clearly explain your trade-offs, gaps, and design decisions?

# Anti-cheat note
We assume you may use AI tools.
That is allowed.
However, you must be able to explain every important part of your code during the walkthrough call.
We will penalise:
- code you cannot explain
- hardcoded outputs
- sample-specific hacks
- hidden runtime API calls
- skipping Kotlin parser logic and doing it in JavaScript
- vague README explanations
- pretending not to use AI if you obviously did

Be transparent. We care more about judgment and ownership than pretending to work without tools.
# Submission checklist
Before submitting, make sure your repo includes:
- ☐ React Native Android app
- ☐ Kotlin native parser module
- ☐ JS bridge call to parseSms
- ☐ screen rendering all 25 parsed results
- ☐ summary header
- ☐ detail modal
- ☐ config-driven bank/exclusion rules
- ☐ Kotlin parser unit tests
- ☐ README with all required sections
- ☐ Production Android design note covers OEMs, permissions, latency, duplicates, and incremental parsing
- ☐ screen recording link
- ☐ no real SMS permission request
- ☐ no external API dependency for parsing

# Appendix A: SMS Samples
Use these 25 SMS messages as your input set.

| ID | SMS text |
| --- | --- |
| 1 | Sent Rs.450.00 From HDFC Bank A/C *4521 To BIGBASKET on 02/04/26. Ref 405617287211. Not You? Call 18002586161/SMS BLOCK CC to 7308080808 to block CC. |
| 2 | INR 1,250.00 spent on HDFC Bank Credit Card xx5678 at SWIGGY on 03-04-2026. Avl Limit: INR 1,45,300.00. |
| 3 | ICICI Bank Acct XX123 debited Rs 2,500.00 on 04-Apr-26 & credited to UPI/swiggy@hdfc/Payment. UPI Ref:240412345678. Call 18002662 if not you. |
| 4 | Dear Customer, Rs 50000 credited to your A/c XX4521 on 05-04-2026 by SALARY-ACMECORP. Avl Bal: Rs 1,52,300.45. |
| 5 | INR 320.00 spent using Axis Bank Card no. XX9876 on 06-APR-26 at AMAZON. Available Limit: INR 87,500.00. |
| 6 | Transaction Alert: Rs. 500.00 debited from your HDFC Bank Debit Card ending 1234 at SWIGGY on 06-04-26. |
| 7 | Spent Rs. 1200.00 on YES BANK Credit Card XX8888 at AMAZON on 07-04-26. Avl Lmt: Rs 78,500. |
| 8 | Hey there, you've spent Rs 1836.00 to HOSPITALITY PVT DELHI IN on your Edge Federal Bank Credit Card ending 4422 on 07-04-2026. Tap to view your transactions in the Jupiter app. |
| 9 | You've spent Rs. 849.00 at Blackwater Coffee, Gurgaon with your BOBCARD One Credit Card ending in XX9907 on 08-04-2026. |
| 10 | Use 458219 as your OTP for HDFC Bank Net Banking login. Valid for 5 mins. Do NOT share with anyone. |
| 11 | Avl Bal in your A/C XX4521 as on 08-04-26 is INR 1,02,450.30. Call 18002586161 for details. |
| 12 | Your HDFC Bank Credit Card xx5678 bill of Rs 23,450.00 is due on 15-04-26. View your bill at hdfcbank.com/billview. |
| 13 | Get flat 50% off + extra 10% cashback on travel bookings with HDFC Credit Cards this weekend. T&C apply. Visit hdfcbank.com/offers. |
| 14 | Dear Customer, Rs 2,500 will be auto debited via E-Mandate from your HDFC Card XX5678 on 12-04-26 for NETFLIX_SUBSCRIPTION. Please maintain sufficient limit. |
| 15 | Transaction Declined: Attempt to spend Rs. 9,999 on your ICICI Credit Card XX1122 at FOREIGN MERCHANT was declined due to insufficient credit limit. |
| 16 | Your SIP of Rs 5,000 in Mirae Asset Large Cap Fund folio 12345678 has been debited from A/c XX4521 on 10-04-26. |
| 17 | Your Rs 75,000.00 spend on HDFC Card xx5678 at CROMA-ELECTRONICS has been converted to EMI of Rs 6,847/month for 12 months at 13% interest. |
| 18 | Finance charge of Rs 1,250.45 + GST Rs 225.08 has been debited from your HDFC Credit Card xx5678 for late payment on bill dated 31-03-2026. |
| 19 | Payment of Rs 23,450.00 received towards your HDFC Bank Credit Card xx5678 on 11-04-26. Thank you. |
| 20 | Rs 1,200 debited from A/c XX4521 via UPI on 11-04-26. UPI/P2A/MOHAN-SHARMA@OKAXIS/Personal. UPI Ref: 240411887211. |
| 21 | Refund of Rs 450.00 has been credited to your HDFC Card xx5678 from BIGBASKET on 12-04-26 against original txn dated 02-04-26. |
| 22 | USD 49.99 spent on your Axis Bank Card XX9876 at NETFLIX.COM/US on 13-APR-26. Foreign currency markup of 3.5% will be applied. INR equivalent will appear in statement. |
| 23 | Premium of Rs 12,500 debited from A/c XX4521 on 13-04-26 for HDFC Life Insurance Policy XYZ-2026. Renewal complete. |
| 24 | Rs.99 debited from A/c XX4521 via UPI on 14-04-26. UPI Ref: 240478234511 to NETFLIX-MONTHLY. Avl Bal: Rs 1,02,351.30. |
| 25 | Spent Rs. 2,4 |
