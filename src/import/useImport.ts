/**
 * Import flow controller (WS-5, buildphase-v2.md §6; integrated by WS-6).
 *
 * Wires the floating toolbar's Import action end-to-end:
 *   pick a file (native) -> read it (native) -> map to records (pure helper) ->
 *   hand the imported `SmsRecord[]` back to the screen.
 *
 * WS-6 integration: the screen owns the full record set and re-runs the Kotlin
 * contextual engine (`parseSmsSession`) over the COMBINED bundled + imported
 * batch, so imported messages thread/roll-up alongside the bundled samples in
 * the Insights view (V2 — Kotlin owns all correlation; RN never correlates).
 * This hook therefore surfaces the parsed records, not a session result.
 *
 * Keeps all of this out of the screen component, and keeps the parse step in the
 * native-free `parseImport` helper so WS-8 can test the mapping in isolation.
 *
 * Conservative + non-blocking (V4 / task 4): a cancelled pick is a no-op; a
 * malformed file surfaces a friendly error banner; an oversized file is capped
 * with a visible notice. The flow never throws to the UI.
 */
import {useCallback, useState} from 'react';
import {Platform, ToastAndroid} from 'react-native';
import {SmsRecord} from '../native/SmsParser';
import {parseImport} from './parseImport';
import {pickSmsFile} from './pickFile';
import type {BannerVariant} from '../components/Banner';

export interface ImportBanner {
  message: string;
  variant: BannerVariant;
}

export interface UseImport {
  /** Open the picker and run the full import flow. Safe to call repeatedly. */
  importFile: () => void;
  /** True while a pick/parse/session round-trip is in flight. */
  importing: boolean;
  /** Current non-blocking banner (error or notice), or null. */
  banner: ImportBanner | null;
  /** Dismiss the banner. */
  dismissBanner: () => void;
}

/** Show a transient count toast (Android only; this app targets Android). */
function toast(message: string): void {
  if (Platform.OS === 'android') {
    ToastAndroid.show(message, ToastAndroid.SHORT);
  }
}

/**
 * @param onImported called with the parsed `SmsRecord[]` for the imported batch,
 *   in input order. The screen appends these to its record set and re-runs the
 *   contextual engine over the combined batch (so imports thread with bundled
 *   samples). It may return a Promise; this hook awaits it so any session error
 *   surfaces as a banner and `importing` stays true until the re-parse settles.
 */
export function useImport(
  onImported: (records: SmsRecord[]) => void | Promise<void>,
): UseImport {
  const [importing, setImporting] = useState(false);
  const [banner, setBanner] = useState<ImportBanner | null>(null);

  const dismissBanner = useCallback(() => setBanner(null), []);

  const importFile = useCallback(() => {
    if (importing) {
      return;
    }
    setImporting(true);
    setBanner(null);

    (async () => {
      const picked = await pickSmsFile();
      if (picked === null) {
        // User cancelled — a normal outcome, nothing to do.
        return;
      }

      const outcome = parseImport(picked.content, picked.fileName);
      if (outcome.error) {
        setBanner({message: outcome.error, variant: 'error'});
        return;
      }

      const records: SmsRecord[] = outcome.records;
      if (records.length === 0) {
        // No usable messages — surface the helper's notice, don't call native.
        setBanner({
          message: outcome.notice ?? 'No messages found in the file.',
          variant: 'notice',
        });
        return;
      }

      // Hand the parsed records to the screen, which re-runs the contextual
      // engine (parseSmsSession, NOT parseSms) over the combined bundled +
      // imported batch so imports are threaded/enriched together (V2).
      await onImported(records);

      const count = records.length;
      toast(`Imported ${count} ${count === 1 ? 'message' : 'messages'}`);
      // A cap/empty notice is non-fatal but worth showing alongside the toast.
      if (outcome.notice) {
        setBanner({message: outcome.notice, variant: 'notice'});
      }
    })()
      .catch((e: unknown) => {
        const detail =
          e instanceof Error ? e.message : typeof e === 'string' ? e : '';
        setBanner({
          message: detail
            ? `Couldn't import that file: ${detail}`
            : "Couldn't import that file.",
          variant: 'error',
        });
      })
      .finally(() => setImporting(false));
  }, [importing, onImported]);

  return {importFile, importing, banner, dismissBanner};
}
