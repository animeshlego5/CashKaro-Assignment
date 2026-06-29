import React, {useEffect, useRef} from 'react';
import {
  Animated,
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  useWindowDimensions,
  View,
} from 'react-native';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import {
  EnrichedResult,
  isEnriched,
  ParsedResult,
  Thread,
} from '../native/SmsParser';
import {
  categoryLabel,
  elevation,
  formatAmount,
  radius,
  space,
  type,
} from '../theme';
import {useTheme} from '../theme/ThemeContext';
import Glass from './Glass';

/**
 * Detail modal (docs/UI-Requirements.md §3): raw SMS, decision, exclude reason
 * (if any), parsed transaction fields (if any) and confidence.
 *
 * WS-4: presented as an iOS sheet — a glass surface with a grabber handle that
 * springs up on present and slides down on dismiss. The spring is toned down to
 * a plain timing when Reduce Motion is enabled (AccessibilityInfo).
 *
 * WS-6: the same sheet opens for any item (a Messages row, a thread event, a
 * merchant rollup). When the result carries contextual-engine enrichment it
 * additionally shows an Enrichment section (canonical merchant, category,
 * recurring, linked messages) and, when the item belongs to a multi-message
 * thread, a Thread section (net amount + the ordered lifecycle stages). These
 * are purely additive; the five core fields above them are byte-identical to
 * parseSms (V1/V6).
 */
export default function DetailModal({
  result,
  thread,
  visible,
  onClose,
}: {
  result: ParsedResult | EnrichedResult | null;
  /** The thread this result belongs to, when it is part of a multi-event one. */
  thread?: Thread | null;
  visible: boolean;
  onClose: () => void;
}): React.JSX.Element {
  const {palette, reduceMotion} = useTheme();
  const insets = useSafeAreaInsets();
  // Bound the sheet to a concrete fraction of the screen (a '%' maxHeight does
  // not resolve against the absolutely-positioned, height-less sheet wrapper) so
  // the fixed header (grabber + Done) is always on-screen and the body scrolls.
  const {height: windowHeight} = useWindowDimensions();
  const maxSheet = Math.round(windowHeight * 0.88);
  // 1 = presented, 0 = hidden. Drives a translateY + backdrop fade. Uses RN's
  // built-in Animated (no native C++ build) so the glass UI compiles without the
  // reanimated NDK toolchain; native-driven so it stays off the JS thread.
  const progress = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    if (visible) {
      const present = reduceMotion
        ? Animated.timing(progress, {
            toValue: 1,
            duration: 120,
            useNativeDriver: true,
          })
        : Animated.spring(progress, {
            toValue: 1,
            damping: 18,
            stiffness: 220,
            mass: 0.9,
            useNativeDriver: true,
          });
      present.start();
    } else {
      Animated.timing(progress, {
        toValue: 0,
        duration: reduceMotion ? 80 : 180,
        useNativeDriver: true,
      }).start();
    }
  }, [visible, reduceMotion, progress]);

  const sheetStyle = {
    transform: [
      {
        translateY: progress.interpolate({
          inputRange: [0, 1],
          outputRange: [600, 0],
        }),
      },
    ],
  };
  const backdropStyle = {opacity: progress};

  return (
    <Modal
      visible={visible}
      animationType="none"
      transparent
      onRequestClose={onClose}>
      <View style={styles.fill}>
        <Animated.View style={[styles.backdrop, backdropStyle]}>
          <Pressable
            style={styles.fill}
            onPress={onClose}
            accessibilityLabel="Dismiss"
          />
        </Animated.View>

        <Animated.View style={[styles.sheetWrap, sheetStyle]}>
          <Glass radius={radius.outer} elevation={elevation.sheet}>
            <View
              style={[
                styles.inner,
                {maxHeight: maxSheet, paddingBottom: space.lg + insets.bottom},
              ]}>
              <View
                style={[styles.grabber, {backgroundColor: palette.grabber}]}
              />

              <View style={styles.header}>
                <Text style={[styles.title, {color: palette.label}]}>
                  Parsed Result
                </Text>
                <TouchableOpacity
                  onPress={onClose}
                  hitSlop={{top: 8, bottom: 8, left: 8, right: 8}}>
                  <Text style={[styles.close, {color: palette.accent}]}>
                    Done
                  </Text>
                </TouchableOpacity>
              </View>

              {result ? (
                <ScrollView style={styles.scroll}>
                  <Field label="Decision" value={result.decision} />
                  {result.excludeReason ? (
                    <Field
                      label="Exclude reason"
                      value={result.excludeReason}
                    />
                  ) : null}
                  <Field
                    label="Confidence"
                    value={`${Math.round(result.confidence * 100)}%`}
                  />

                  {result.transaction ? (
                    <>
                      <Text
                        style={[
                          styles.section,
                          {color: palette.tertiaryLabel},
                        ]}>
                        Transaction
                      </Text>
                      <Field
                        label="Amount"
                        value={formatAmount(
                          result.transaction.amount,
                          result.transaction.currency,
                        )}
                      />
                      <Field
                        label="Currency"
                        value={result.transaction.currency}
                      />
                      <Field label="Type" value={result.transaction.type} />
                      <Field
                        label="Bank (issuer)"
                        value={result.transaction.bank ?? '—'}
                      />
                      <Field
                        label="Card last 4"
                        value={result.transaction.cardLastFour ?? '—'}
                      />
                      <Field
                        label="Merchant"
                        value={result.transaction.merchant ?? '—'}
                      />
                      <Field
                        label="Date"
                        value={result.transaction.date ?? '—'}
                      />
                    </>
                  ) : null}

                  {isEnriched(result) ? (
                    <EnrichmentSection result={result} />
                  ) : null}

                  {thread && thread.events.length > 1 ? (
                    <ThreadSection thread={thread} />
                  ) : null}

                  <Text
                    style={[styles.section, {color: palette.tertiaryLabel}]}>
                    Raw SMS
                  </Text>
                  <Text style={[styles.raw, {color: palette.label}]}>
                    {result.rawSms}
                  </Text>
                </ScrollView>
              ) : null}
            </View>
          </Glass>
        </Animated.View>
      </View>
    </Modal>
  );
}

/**
 * Additive enrichment details from the contextual engine (WS-6): canonical
 * merchant, category, recurring flag, and the ids of corroborating messages.
 * Only shown when at least one is present (conservative — the engine emits null
 * when no token hits).
 */
function EnrichmentSection({
  result,
}: {
  result: EnrichedResult;
}): React.JSX.Element | null {
  const {palette} = useTheme();
  const category = categoryLabel(result.category);
  const linked = result.linkedTo ?? [];
  const hasAny =
    !!result.merchantCanonical ||
    !!category ||
    result.recurring ||
    linked.length > 0;
  if (!hasAny) {
    return null;
  }
  return (
    <>
      <Text style={[styles.section, {color: palette.tertiaryLabel}]}>
        Enrichment
      </Text>
      {result.merchantCanonical ? (
        <Field label="Canonical merchant" value={result.merchantCanonical} />
      ) : null}
      {category ? <Field label="Category" value={category} /> : null}
      <Field label="Recurring" value={result.recurring ? 'Yes' : 'No'} />
      {linked.length > 0 ? (
        <Field label="Linked messages" value={linked.join(', ')} />
      ) : null}
    </>
  );
}

/**
 * The lifecycle thread this result belongs to (WS-6): the net amount plus the
 * ordered stages (auth -> spend -> refund/EMI/bill). Only rendered for
 * multi-event threads so a standalone transaction stays uncluttered.
 */
function ThreadSection({thread}: {thread: Thread}): React.JSX.Element {
  const {palette} = useTheme();
  return (
    <>
      <Text style={[styles.section, {color: palette.tertiaryLabel}]}>
        Thread
      </Text>
      <Field
        label="Net amount"
        value={formatAmount(thread.netAmount, threadCurrency(thread))}
      />
      <Field label="Events" value={String(thread.events.length)} />
      {thread.events.map((ev, i) => (
        <View
          key={ev.id}
          style={[styles.threadRow, {borderBottomColor: palette.separator}]}>
          <Text style={[styles.threadStage, {color: palette.secondaryLabel}]}>
            {i + 1}. {stageLabel(ev)}
          </Text>
          <Text
            style={[styles.threadAmount, {color: palette.label}]}
            numberOfLines={1}>
            {ev.transaction
              ? formatAmount(ev.transaction.amount, ev.transaction.currency)
              : ev.excludeReason ?? 'EXCLUDED'}
          </Text>
        </View>
      ))}
    </>
  );
}

/** Currency to format a thread's net amount in (first event with one, else INR). */
function threadCurrency(thread: Thread): string {
  for (const ev of thread.events) {
    if (ev.transaction) {
      return ev.transaction.currency;
    }
  }
  return 'INR';
}

/** A short human label for a thread event's lifecycle stage. */
function stageLabel(ev: EnrichedResult): string {
  if (ev.transaction) {
    return ev.transaction.type; // DEBIT / CREDIT / REFUND
  }
  return ev.excludeReason ?? 'EVENT';
}

function Field({
  label,
  value,
}: {
  label: string;
  value: string;
}): React.JSX.Element {
  const {palette} = useTheme();
  return (
    <View style={[styles.field, {borderBottomColor: palette.separator}]}>
      <Text style={[styles.fieldLabel, {color: palette.secondaryLabel}]}>
        {label}
      </Text>
      <Text style={[styles.fieldValue, {color: palette.label}]}>{value}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  fill: {flex: 1},
  backdrop: {...StyleSheet.absoluteFillObject, backgroundColor: '#00000066'},
  sheetWrap: {position: 'absolute', left: 0, right: 0, bottom: 0},
  inner: {padding: space.lg},
  // Body scrolls within the bounded sheet so the fixed header (Done) stays put.
  // RN's default flexShrink is 0, so this is required for the ScrollView to
  // shrink to the available space instead of overflowing the sheet.
  scroll: {flexShrink: 1},
  grabber: {
    width: 36,
    height: 5,
    borderRadius: 3,
    alignSelf: 'center',
    marginBottom: space.md,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: space.md,
  },
  title: {...type.title3, fontWeight: '700'},
  close: {...type.headline},
  section: {
    ...type.caption,
    fontWeight: '700',
    textTransform: 'uppercase',
    letterSpacing: 0.6,
    marginTop: space.md,
    marginBottom: space.xs,
  },
  field: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingVertical: 8,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  fieldLabel: {...type.subhead},
  fieldValue: {
    ...type.subhead,
    fontWeight: '600',
    flexShrink: 1,
    textAlign: 'right',
    marginLeft: space.md,
  },
  raw: {...type.footnote, lineHeight: 19, marginTop: space.xs},
  threadRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 6,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  threadStage: {...type.footnote, fontWeight: '600', flexShrink: 1},
  threadAmount: {...type.footnote, marginLeft: space.md},
});
