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

/* ------------------------------------------------------------------ *
 * Session API (buildphase-v2.md §7) — ADDITIVE, separate from parseSms.
 * The contextual engine (native Kotlin) threads related messages and
 * canonicalises/flags recurring merchants. parseSms is unchanged and
 * remains the graded entry point.
 * ------------------------------------------------------------------ */

/** One input record for the session API. receivedAt is epoch ms; null/absent when unknown. */
export interface SmsRecord {
  text: string;
  receivedAt?: number;
  sender?: string;
}

/**
 * A ParsedResult (the 5 core keys byte-identical to parseSms) plus additive
 * enrichment. The core fields are spread at the top level so this genuinely
 * extends ParsedResult.
 */
export interface EnrichedResult extends ParsedResult {
  /** Stable id within the session (input index as a string). */
  id: string;
  receivedAt: number | null;
  threadId: string | null;
  /** Canonical merchant, e.g. "Netflix"; null when no token hits. */
  merchantCanonical: string | null;
  /** Category, e.g. "entertainment"; null when unknown. */
  category: string | null;
  recurring: boolean;
  /** ids of corroborating messages in the thread; null when standalone. */
  linkedTo: string[] | null;
}

/** A lifecycle thread: a primary transaction and its linked events. */
export interface Thread {
  threadId: string;
  card4: string | null;
  merchantCanonical: string | null;
  /** spend minus refunds within the thread. */
  netAmount: number;
  /** ordered: auth -> spend -> refund/EMI/bill. */
  events: EnrichedResult[];
}

/** Cross-message rollup for one canonical merchant. */
export interface MerchantSummary {
  canonical: string;
  category: string | null;
  count: number;
  totalSpend: number;
  recurring: boolean;
}

/** The full session output (§7). results stays 1:1 and in input order. */
export interface SessionResult {
  results: EnrichedResult[];
  threads: Thread[];
  merchants: MerchantSummary[];
}

/**
 * Narrow a ParsedResult to an EnrichedResult. True for results that came from
 * `parseSmsSession` (they carry the additive enrichment keys); false for plain
 * `parseSms` output. Lets a shared component (e.g. ResultRow) render the
 * enrichment line only when the data is present, without a separate prop.
 */
export function isEnriched(
  result: ParsedResult | EnrichedResult,
): result is EnrichedResult {
  return (
    typeof (result as EnrichedResult).id === 'string' &&
    'merchantCanonical' in result &&
    'recurring' in result
  );
}

interface SmsParserNativeModule {
  parseSms(samples: string[]): Promise<ParsedResult[]>;
  parseSmsSession(records: SmsRecord[]): Promise<SessionResult>;
}

const {SmsParser} = NativeModules as {SmsParser: SmsParserNativeModule};

/**
 * Parse a batch of raw SMS strings in native Kotlin.
 * Accepts any-length array (C6 — never assumes 25 samples).
 */
export function parseSms(samples: string[]): Promise<ParsedResult[]> {
  if (!SmsParser) {
    return Promise.reject(
      new Error(
        'Native module "SmsParser" is not linked. Did the app rebuild?',
      ),
    );
  }
  return SmsParser.parseSms(samples);
}

/**
 * Run an ordered batch of SMS records through the native contextual engine,
 * returning enriched results + cross-message rollups (§7). ADDITIVE — parseSms
 * is unchanged. Accepts any-length array (C6).
 */
export function parseSmsSession(records: SmsRecord[]): Promise<SessionResult> {
  if (!SmsParser) {
    return Promise.reject(
      new Error(
        'Native module "SmsParser" is not linked. Did the app rebuild?',
      ),
    );
  }
  return SmsParser.parseSmsSession(records);
}
