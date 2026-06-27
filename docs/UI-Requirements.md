# UI Requirements — React Native Screen

> The React Native screen specification. See [Assignment-Index.md](Assignment-Index.md) for all documents.

Build one simple screen that calls `parseSms(samples)` on mount and renders the parsed output.

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
