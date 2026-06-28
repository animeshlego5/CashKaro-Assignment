/**
 * Pure file-import mapping (WS-5, buildphase-v2.md §6).
 *
 * Turns the raw text of a user-picked file into `SmsRecord[]` for the contextual
 * engine (`parseSmsSession`). This module is intentionally FREE of any native
 * dependency (no document picker, no react-native-fs, no react-native at all) so
 * WS-8 can unit-test the `.json` / `.txt` -> records mapping without the bridge.
 *
 * Conservative parsing (V4 / task 4):
 *   - A malformed file never throws to the caller — it returns `{error}` so the
 *     UI can show a non-blocking banner instead of crashing.
 *   - Very large imports are capped (MAX_RECORDS) with a VISIBLE `notice`; we
 *     never silently truncate.
 *
 * Accepted formats (detected by content, with the filename as a hint):
 *   - `.json` — the samples.json shape `[{ "text": "..." }, …]`. Also accepts a
 *     bare string array `["...", …]`. `receivedAt` (epoch ms) and `sender` keys
 *     are honoured when present for richer threading.
 *   - `.txt`  — one SMS per line; blank lines are skipped.
 */
import type {SmsRecord} from '../native/SmsParser';

/** Hard cap on imported messages — protects the engine + UI from a huge file. */
export const MAX_RECORDS = 5000;

/** Outcome of parsing a picked file. Exactly one of records / error is meaningful. */
export interface ImportOutcome {
  /** The mapped records (possibly empty). Always present (never undefined). */
  records: SmsRecord[];
  /**
   * A non-fatal, user-visible message (e.g. the truncation cap was hit, or a
   * file had no usable messages). Null when there is nothing to surface.
   */
  notice: string | null;
  /**
   * A fatal-for-this-file message (malformed JSON, unsupported content). When
   * set, `records` is empty and the UI shows a non-blocking error banner.
   */
  error: string | null;
}

/** Coerce an unknown value to a trimmed non-empty string, else null. */
function asText(value: unknown): string | null {
  if (typeof value !== 'string') {
    return null;
  }
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}

/** Coerce an unknown value to a finite epoch-ms number, else undefined. */
function asReceivedAt(value: unknown): number | undefined {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value;
  }
  // Tolerate stringified numbers (some exporters quote everything).
  if (typeof value === 'string' && value.trim() !== '') {
    const n = Number(value);
    if (Number.isFinite(n)) {
      return n;
    }
  }
  return undefined;
}

/** Coerce an unknown value to a non-empty sender string, else undefined. */
function asSender(value: unknown): string | undefined {
  const s = asText(value);
  return s ?? undefined;
}

/**
 * Map one JSON array element to a record. Accepts a bare string, or an object
 * with a `text` field (+ optional receivedAt / sender). Returns null when there
 * is no usable text — the caller skips it conservatively.
 */
function recordFromJsonItem(item: unknown): SmsRecord | null {
  // Bare string array: ["...", "..."]
  const bare = asText(item);
  if (bare !== null) {
    return {text: bare};
  }
  // Object form: { text, receivedAt?, sender? }
  if (item !== null && typeof item === 'object') {
    const obj = item as Record<string, unknown>;
    const text = asText(obj.text);
    if (text === null) {
      return null;
    }
    const record: SmsRecord = {text};
    const receivedAt = asReceivedAt(obj.receivedAt);
    if (receivedAt !== undefined) {
      record.receivedAt = receivedAt;
    }
    const sender = asSender(obj.sender);
    if (sender !== undefined) {
      record.sender = sender;
    }
    return record;
  }
  return null;
}

/** Apply the MAX_RECORDS cap, attaching a visible notice when it bites. */
function applyCap(records: SmsRecord[]): ImportOutcome {
  if (records.length > MAX_RECORDS) {
    return {
      records: records.slice(0, MAX_RECORDS),
      notice: `Large file: imported the first ${MAX_RECORDS.toLocaleString()} of ${records.length.toLocaleString()} messages.`,
      error: null,
    };
  }
  if (records.length === 0) {
    return {
      records: [],
      notice: 'No messages found in the file.',
      error: null,
    };
  }
  return {records, notice: null, error: null};
}

/** Parse `.json` content into records. Malformed JSON -> error (never throws). */
function parseJson(content: string): ImportOutcome {
  let data: unknown;
  try {
    data = JSON.parse(content);
  } catch {
    return {
      records: [],
      notice: null,
      error: "Couldn't read the file: it isn't valid JSON.",
    };
  }
  if (!Array.isArray(data)) {
    return {
      records: [],
      notice: null,
      error:
        'Unsupported JSON: expected an array of messages (e.g. [{ "text": "..." }, …]).',
    };
  }
  const records: SmsRecord[] = [];
  for (const item of data) {
    const rec = recordFromJsonItem(item);
    if (rec !== null) {
      records.push(rec);
    }
  }
  return applyCap(records);
}

/** Parse `.txt` content into records — one SMS per line, blanks skipped. */
function parseTxt(content: string): ImportOutcome {
  const records: SmsRecord[] = [];
  // Split on any newline style; skip blank/whitespace-only lines.
  for (const line of content.split(/\r\n|\r|\n/)) {
    const text = line.trim();
    if (text.length > 0) {
      records.push({text});
    }
  }
  return applyCap(records);
}

/** True when the (lower-cased) name ends with the given extension. */
function hasExt(fileName: string | null | undefined, ext: string): boolean {
  return !!fileName && fileName.toLowerCase().endsWith(ext);
}

/** Heuristic: does the trimmed content look like a JSON array? */
function looksLikeJson(content: string): boolean {
  return content.trimStart().startsWith('[');
}

/**
 * Parse a picked file's raw content into records.
 *
 * Detection order: trust the extension first (.json / .txt); when the name is
 * missing or ambiguous, fall back to content sniffing (a leading `[` => JSON,
 * otherwise treat as line-delimited text). An empty file yields a friendly
 * notice, never an error.
 */
export function parseImport(
  content: string,
  fileName?: string | null,
): ImportOutcome {
  if (content.trim().length === 0) {
    return {records: [], notice: 'The file is empty.', error: null};
  }

  if (hasExt(fileName, '.json')) {
    return parseJson(content);
  }
  if (hasExt(fileName, '.txt')) {
    return parseTxt(content);
  }

  // Unknown / missing extension: sniff the content. JSON arrays are
  // unambiguous; everything else is treated as one-SMS-per-line text.
  return looksLikeJson(content) ? parseJson(content) : parseTxt(content);
}
