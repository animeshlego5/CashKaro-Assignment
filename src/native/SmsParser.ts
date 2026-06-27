/**
 * Typed JS wrapper around the native Kotlin SMS parser.
 *
 * All parsing/classification/scoring happens in Kotlin (see
 * android/app/src/main/java/com/cashkaro/smsparser). JS only calls this bridge
 * and renders the results. The types below mirror the schema in
 * docs/Functions.md exactly — keep them in lock-step with the Kotlin models.
 */
import {NativeModules} from 'react-native';

/** decision field — INCLUDE only for relevant completed credit-card transactions. */
export type Decision = 'INCLUDE' | 'EXCLUDE';

/** transaction.type — DEBIT (spend), CREDIT (relevant CC credit), REFUND (reversal). */
export type TxnType = 'DEBIT' | 'CREDIT' | 'REFUND';

/** Present only when decision === 'INCLUDE'. */
export interface Transaction {
  amount: number;
  currency: string;
  bank: string | null;
  cardLastFour: string | null;
  merchant: string | null;
  type: TxnType;
  date: string | null;
}

export interface ParsedResult {
  rawSms: string;
  decision: Decision;
  /** Required when decision === 'EXCLUDE'; null for INCLUDE. */
  excludeReason: string | null;
  /** Null for every EXCLUDE result. */
  transaction: Transaction | null;
  /** 0.0–1.0 — parser's confidence in the decision + extracted fields. */
  confidence: number;
}

interface SmsParserNativeModule {
  parseSms(samples: string[]): Promise<ParsedResult[]>;
}

const {SmsParser} = NativeModules as {SmsParser: SmsParserNativeModule};

/**
 * Parse a batch of raw SMS strings in native Kotlin.
 * Accepts any-length array (C6 — never assumes 25 samples).
 */
export function parseSms(samples: string[]): Promise<ParsedResult[]> {
  if (!SmsParser) {
    return Promise.reject(
      new Error('Native module "SmsParser" is not linked. Did the app rebuild?'),
    );
  }
  return SmsParser.parseSms(samples);
}
