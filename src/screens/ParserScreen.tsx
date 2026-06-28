import React, {useCallback, useEffect, useMemo, useState} from 'react';
import {
  ActivityIndicator,
  FlatList,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import samples from '../data/samples.json';
import {
  EnrichedResult,
  parseSmsSession,
  SessionResult,
  SmsRecord,
  Thread,
} from '../native/SmsParser';
import DetailModal from '../components/DetailModal';
import ResultRow from '../components/ResultRow';
import SummaryHeader from '../components/SummaryHeader';
import SegmentedControl from '../components/SegmentedControl';
import InsightsView from '../components/InsightsView';
import Toolbar from '../components/Toolbar';
import Banner from '../components/Banner';
import {useImport} from '../import/useImport';
import {space, type} from '../theme';
import {useTheme} from '../theme/ThemeContext';

/**
 * The single screen (docs/UI-Requirements.md), restyled to iOS 26 Liquid Glass
 * (WS-4): a floating glass control layer (toolbar + segmented control) sits
 * above a scrolling content layer that peeks through the glass.
 *
 * WS-6: the screen now runs the native CONTEXTUAL ENGINE (`parseSmsSession`) over
 * the bundled samples on mount — so the Insights segment (threads, merchant
 * rollups, recurring) is populated on launch — and re-runs it over the COMBINED
 * bundled + imported batch on every import, so imports thread/roll-up alongside
 * the samples. `results` stays 1:1 and in input order, so the Messages segment is
 * a drop-in for the original per-SMS list. All parsing/correlation is in Kotlin
 * (C1/V2); RN only calls the bridge and renders. parseSms is unchanged and
 * remains the graded entry point.
 */
const SEGMENTS = ['Messages', 'Insights'] as const;
type Segment = (typeof SEGMENTS)[number];

/** A tapped item plus the thread it belongs to (null when standalone). */
interface Selection {
  result: EnrichedResult;
  thread: Thread | null;
}

export default function ParserScreen(): React.JSX.Element {
  const {palette} = useTheme();
  const insets = useSafeAreaInsets();
  const [session, setSession] = useState<SessionResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<Selection | null>(null);
  const [segment, setSegment] = useState<Segment>('Messages');

  // The full ordered record set fed to the engine: the bundled samples plus any
  // imported messages. Re-running the session over this combined batch keeps
  // threads/rollups consistent across bundled + imported (V2 — Kotlin owns it).
  const [records, setRecords] = useState<SmsRecord[]>(() =>
    samples.map(s => ({text: s.text})),
  );

  // Mount-only: run the engine over the bundled samples so Insights is populated
  // on launch. Imports re-parse explicitly in handleImported (so an import error
  // surfaces as a non-blocking banner, not a wiped screen) — hence this effect is
  // intentionally not keyed on `records`.
  useEffect(() => {
    let active = true;
    // C6: pass whatever the record set holds (any length) to the engine.
    parseSmsSession(records)
      .then(s => {
        if (active) {
          setSession(s);
        }
      })
      .catch(e => {
        if (active) {
          setError(String(e?.message ?? e));
        }
      });
    return () => {
      active = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // WS-5/WS-6: append imported messages to the record set and re-run the engine
  // over the COMBINED batch so imports thread/roll-up alongside the bundled
  // samples. Returning the promise lets the import hook keep its spinner up and
  // surface any session error as a banner (the screen is not wiped).
  const handleImported = useCallback(
    (imported: SmsRecord[]): Promise<void> => {
      const next = [...records, ...imported];
      return parseSmsSession(next).then(s => {
        setRecords(next);
        setSession(s);
      });
    },
    [records],
  );
  const {importFile, importing, banner, dismissBanner} =
    useImport(handleImported);

  const results = session?.results ?? null;

  // Map a result id -> the multi-event thread it belongs to, so tapping a
  // Messages row opens the detail sheet with its thread context.
  const threadByResultId = useMemo(() => {
    const map = new Map<string, Thread>();
    for (const t of session?.threads ?? []) {
      if (t.events.length > 1) {
        for (const ev of t.events) {
          map.set(ev.id, t);
        }
      }
    }
    return map;
  }, [session]);

  const selectResult = useCallback(
    (result: EnrichedResult): void => {
      setSelected({result, thread: threadByResultId.get(result.id) ?? null});
    },
    [threadByResultId],
  );
  const selectFromInsights = useCallback(
    (result: EnrichedResult, thread: Thread | null): void => {
      setSelected({result, thread});
    },
    [],
  );

  // Height reserved for the floating glass control layer so the content starts
  // below it (it still scrolls up under the glass).
  const controlsTop = insets.top + space.sm;
  const contentTop = controlsTop + CONTROLS_HEIGHT;
  const contentBottom = insets.bottom + space.xl;

  if (error) {
    return (
      <View
        style={[styles.center, {backgroundColor: palette.systemBackground}]}>
        <Text style={[styles.error, {color: palette.debit}]}>Bridge error</Text>
        <Text style={[styles.errorDetail, {color: palette.secondaryLabel}]}>
          {error}
        </Text>
      </View>
    );
  }

  if (results === null || session === null) {
    return (
      <View
        style={[styles.center, {backgroundColor: palette.systemBackground}]}>
        <ActivityIndicator size="large" color={palette.accent} />
        <Text style={[styles.loading, {color: palette.secondaryLabel}]}>
          Parsing {records.length} SMS…
        </Text>
      </View>
    );
  }

  return (
    <View
      style={[styles.container, {backgroundColor: palette.systemBackground}]}>
      {/* Scrolling content layer — peeks through the floating glass above it. */}
      {segment === 'Messages' ? (
        <FlatList
          data={results}
          keyExtractor={item => item.id}
          ListHeaderComponent={<SummaryHeader results={results} />}
          renderItem={({item}) => (
            <ResultRow result={item} onPress={() => selectResult(item)} />
          )}
          contentContainerStyle={[
            styles.list,
            {paddingTop: contentTop, paddingBottom: contentBottom},
          ]}
          showsVerticalScrollIndicator={false}
        />
      ) : (
        <InsightsView
          session={session}
          contentPadding={{top: contentTop, bottom: contentBottom}}
          onSelect={selectFromInsights}
        />
      )}

      {/* Floating glass control layer (toolbar + segmented control). */}
      <View
        style={[styles.controls, {top: controlsTop}]}
        pointerEvents="box-none">
        <Toolbar
          title="Transactions"
          onImport={importFile}
          importing={importing}
        />
        <View style={styles.segmentWrap}>
          <SegmentedControl
            segments={SEGMENTS}
            value={segment}
            onChange={setSegment}
          />
        </View>
        {banner ? (
          <View style={styles.bannerWrap}>
            <Banner
              message={banner.message}
              variant={banner.variant}
              onDismiss={dismissBanner}
            />
          </View>
        ) : null}
      </View>

      <DetailModal
        result={selected?.result ?? null}
        thread={selected?.thread ?? null}
        visible={selected !== null}
        onClose={() => setSelected(null)}
      />
    </View>
  );
}

// Approximate height of toolbar + segmented control + gap, used to inset the
// content so the first card starts below the floating glass (it still scrolls up
// under it).
const CONTROLS_HEIGHT = 124;

const styles = StyleSheet.create({
  container: {flex: 1},
  controls: {
    position: 'absolute',
    left: space.md,
    right: space.md,
  },
  segmentWrap: {marginTop: space.sm},
  bannerWrap: {marginTop: space.sm},
  list: {paddingHorizontal: space.md},
  center: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: space.xl,
  },
  loading: {marginTop: space.md, ...type.subhead},
  error: {...type.headline, fontWeight: '700'},
  errorDetail: {marginTop: space.sm, ...type.footnote, textAlign: 'center'},
});
