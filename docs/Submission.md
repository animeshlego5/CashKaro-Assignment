# Submission & Evaluation

> Submission logistics, anti-cheat rules, and the final checklist. See [Assignment-Index.md](Assignment-Index.md) for all documents.

## Screen recording

Submit a screen recording of 2 minutes or less.

Show:

1. app launch
2. parsed summary
3. scrolling through included and excluded rows
4. opening one included transaction modal
5. opening one excluded SMS modal

No voiceover required, but it is fine if you prefer to explain briefly.

## Anti-cheat note

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

## Submission checklist

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
