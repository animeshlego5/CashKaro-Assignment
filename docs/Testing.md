# Testing

> Testing practices and requirements. See [Assignment-Index.md](Assignment-Index.md) for all documents.

## Kotlin unit tests

Add Kotlin unit tests for the parser.
You do not need heavy UI tests, but parser tests are required.

At minimum, include tests for:

1. a clear credit-card spend
2. a debit-card exclusion
3. an OTP exclusion
4. a UPI/savings-account exclusion
5. a fintech/co-branded issuer attribution case
6. a refund
7. a foreign-currency transaction
8. a malformed SMS

Document how to run the tests in the README.

## Hidden test samples

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
