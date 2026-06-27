package com.cashkaro.smsparser.parser.model

/**
 * Transaction type for an INCLUDED credit-card transaction.
 *
 * - DEBIT  = card spend / purchase.
 * - REFUND = merchant refund / reversal credited back to a credit card.
 * - CREDIT = a relevant non-refund credit-card credit, kept DISTINCT from the
 *            excluded CARD_PAYMENT (bill-payment receipts), per docs/Functions.md.
 */
enum class TxnType { DEBIT, CREDIT, REFUND }
