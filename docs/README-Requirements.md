# README Requirements

> What the submitted repo's README must contain. See [Assignment-Index.md](Assignment-Index.md) for all documents.

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
