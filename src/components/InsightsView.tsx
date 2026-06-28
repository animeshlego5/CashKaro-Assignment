import React, {useMemo} from 'react';
import {
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import {
  EnrichedResult,
  MerchantSummary,
  SessionResult,
  Thread,
} from '../native/SmsParser';
import {
  bankInitials,
  categoryLabel,
  elevation,
  formatAmount,
  radius,
  space,
  type,
} from '../theme';
import {useTheme} from '../theme/ThemeContext';
import Chip from './Chip';
import Glass from './Glass';
import RecurringBadge from './RecurringBadge';

/**
 * Insights segment (WS-6 / D3): surfaces what the Kotlin contextual engine adds
 * on top of the per-message parse —
 *   - Threads:   grouped lifecycle cards (auth -> spend -> refund/EMI/bill) with
 *                a net amount per thread.
 *   - Merchants: canonical-merchant rollups (count, total spend, category).
 *   - Recurring: the merchants flagged recurring/subscription.
 *
 * RN only renders these; all correlation/canonicalisation/recurring detection is
 * done in Kotlin (V2). Tapping any item opens the shared iOS detail sheet via
 * `onSelect`, passing the result and (for thread cards) its thread.
 */
export default function InsightsView({
  session,
  contentPadding,
  onSelect,
}: {
  session: SessionResult;
  /** Top inset so content starts below the floating glass control layer. */
  contentPadding: {top: number; bottom: number};
  onSelect: (result: EnrichedResult, thread: Thread | null) => void;
}): React.JSX.Element {
  const {palette} = useTheme();

  // Only multi-event threads are interesting as "lifecycle" cards; a standalone
  // single transaction is already shown in Messages.
  const lifecycleThreads = useMemo(
    () => session.threads.filter(t => t.events.length > 1),
    [session.threads],
  );
  // A representative result per canonical merchant, so tapping a rollup opens a
  // concrete detail sheet (the first included result that carries that merchant).
  const repByMerchant = useMemo(
    () => representativeByMerchant(session.results),
    [session.results],
  );
  const recurringMerchants = useMemo(
    () => session.merchants.filter(m => m.recurring),
    [session.merchants],
  );

  const openMerchant = (m: MerchantSummary): void => {
    const rep = repByMerchant.get(m.canonical);
    if (rep) {
      onSelect(rep.result, rep.thread);
    }
  };
  // Display currency for a merchant's rollup total. The engine's MerchantSummary
  // has no currency field (§7); we read it off the merchant's representative
  // result so a USD merchant (e.g. Netflix) isn't mislabelled as ₹. Defaults to
  // INR. This is display-only — RN does no classification (V2).
  const currencyForMerchant = (m: MerchantSummary): string =>
    repByMerchant.get(m.canonical)?.result.transaction?.currency ?? 'INR';

  return (
    <ScrollView
      contentContainerStyle={[
        styles.content,
        {paddingTop: contentPadding.top, paddingBottom: contentPadding.bottom},
      ]}
      showsVerticalScrollIndicator={false}>
      {/* THREADS ------------------------------------------------------------ */}
      <SectionTitle title="Threads" subtitle="Linked message lifecycles" />
      {lifecycleThreads.length === 0 ? (
        <EmptyHint text="No multi-message threads in this batch yet." />
      ) : (
        lifecycleThreads.map(t => (
          <ThreadCard
            key={t.threadId}
            thread={t}
            onPress={() => onSelect(primaryEvent(t), t)}
            onSelectEvent={ev => onSelect(ev, t)}
          />
        ))
      )}

      {/* MERCHANTS ---------------------------------------------------------- */}
      <SectionTitle title="Merchants" subtitle="Canonical-merchant rollups" />
      {session.merchants.length === 0 ? (
        <EmptyHint text="No merchants recognised yet." />
      ) : (
        <Glass
          radius={radius.card}
          elevation={elevation.card}
          style={styles.card}>
          <View style={styles.list}>
            {session.merchants.map((m, i) => (
              <MerchantRow
                key={m.canonical}
                merchant={m}
                currency={currencyForMerchant(m)}
                divider={i < session.merchants.length - 1}
                onPress={() => openMerchant(m)}
              />
            ))}
          </View>
        </Glass>
      )}

      {/* RECURRING ---------------------------------------------------------- */}
      <SectionTitle title="Recurring" subtitle="Subscriptions & repeat spend" />
      {recurringMerchants.length === 0 ? (
        <EmptyHint text="No recurring merchants detected." />
      ) : (
        <Glass
          radius={radius.card}
          elevation={elevation.card}
          style={styles.card}>
          <View style={styles.list}>
            {recurringMerchants.map((m, i) => (
              <MerchantRow
                key={m.canonical}
                merchant={m}
                currency={currencyForMerchant(m)}
                divider={i < recurringMerchants.length - 1}
                onPress={() => openMerchant(m)}
                showRecurring
              />
            ))}
          </View>
        </Glass>
      )}
      <Text style={[styles.footer, {color: palette.tertiaryLabel}]}>
        Threading, canonicalisation and recurring detection run in native Kotlin
        (the contextual engine). The graded parseSms output is unchanged.
      </Text>
    </ScrollView>
  );
}

/* -- Threads --------------------------------------------------------------- */

function ThreadCard({
  thread,
  onPress,
  onSelectEvent,
}: {
  thread: Thread;
  onPress: () => void;
  onSelectEvent: (ev: EnrichedResult) => void;
}): React.JSX.Element {
  const {palette} = useTheme();
  const title =
    thread.merchantCanonical ??
    primaryMerchant(thread) ??
    'Linked transactions';
  const netTone = thread.netAmount < 0 ? palette.credit : palette.debit;
  return (
    <Glass radius={radius.card} elevation={elevation.card} style={styles.card}>
      <TouchableOpacity activeOpacity={0.7} onPress={onPress}>
        <View style={styles.threadHead}>
          <View style={[styles.avatar, {backgroundColor: palette.avatarBg}]}>
            <Text style={[styles.avatarText, {color: palette.avatarText}]}>
              {bankInitials(threadBank(thread))}
            </Text>
          </View>
          <View style={styles.threadHeadBody}>
            <Text
              style={[styles.threadTitle, {color: palette.label}]}
              numberOfLines={1}>
              {title}
            </Text>
            <Text
              style={[styles.threadSub, {color: palette.secondaryLabel}]}
              numberOfLines={1}>
              {thread.card4 ? `•• ${thread.card4} · ` : ''}
              {thread.events.length} events
            </Text>
          </View>
          <View style={styles.netWrap}>
            <Text style={[styles.netLabel, {color: palette.tertiaryLabel}]}>
              NET
            </Text>
            <Text style={[styles.netAmount, {color: netTone}]}>
              {formatAmount(Math.abs(thread.netAmount), threadCurrency(thread))}
            </Text>
          </View>
        </View>
      </TouchableOpacity>

      <View style={[styles.timeline, {borderTopColor: palette.separator}]}>
        {thread.events.map((ev, i) => (
          <TouchableOpacity
            key={ev.id}
            activeOpacity={0.6}
            onPress={() => onSelectEvent(ev)}
            style={styles.eventRow}>
            <View style={styles.eventStage}>
              <View style={[styles.dot, {backgroundColor: palette.accent}]} />
              {i < thread.events.length - 1 ? (
                <View
                  style={[
                    styles.connector,
                    {backgroundColor: palette.separator},
                  ]}
                />
              ) : null}
            </View>
            <Text
              style={[styles.eventLabel, {color: palette.label}]}
              numberOfLines={1}>
              {stageLabel(ev)}
            </Text>
            <Text
              style={[styles.eventAmount, {color: palette.secondaryLabel}]}
              numberOfLines={1}>
              {ev.transaction
                ? formatAmount(ev.transaction.amount, ev.transaction.currency)
                : ev.excludeReason ?? '—'}
            </Text>
          </TouchableOpacity>
        ))}
      </View>
    </Glass>
  );
}

/* -- Merchants ------------------------------------------------------------- */

function MerchantRow({
  merchant,
  currency,
  divider,
  onPress,
  showRecurring = false,
}: {
  merchant: MerchantSummary;
  currency: string;
  divider: boolean;
  onPress: () => void;
  showRecurring?: boolean;
}): React.JSX.Element {
  const {palette} = useTheme();
  const category = categoryLabel(merchant.category);
  return (
    <TouchableOpacity activeOpacity={0.6} onPress={onPress}>
      <View
        style={[
          styles.merchantRow,
          divider && {
            borderBottomWidth: StyleSheet.hairlineWidth,
            borderBottomColor: palette.separator,
          },
        ]}>
        <View style={styles.merchantBody}>
          <View style={styles.merchantTitleLine}>
            <Text
              style={[styles.merchantName, {color: palette.label}]}
              numberOfLines={1}>
              {merchant.canonical}
            </Text>
            {category ? <Chip label={category} /> : null}
            {showRecurring || merchant.recurring ? <RecurringBadge /> : null}
          </View>
          <Text style={[styles.merchantMeta, {color: palette.secondaryLabel}]}>
            {merchant.count} {merchant.count === 1 ? 'txn' : 'txns'}
          </Text>
        </View>
        <Text style={[styles.merchantTotal, {color: palette.label}]}>
          {formatAmount(merchant.totalSpend, currency)}
        </Text>
      </View>
    </TouchableOpacity>
  );
}

/* -- Small shared bits ----------------------------------------------------- */

function SectionTitle({
  title,
  subtitle,
}: {
  title: string;
  subtitle: string;
}): React.JSX.Element {
  const {palette} = useTheme();
  return (
    <View style={styles.sectionTitle}>
      <Text style={[styles.sectionH, {color: palette.label}]}>{title}</Text>
      <Text style={[styles.sectionSub, {color: palette.secondaryLabel}]}>
        {subtitle}
      </Text>
    </View>
  );
}

function EmptyHint({text}: {text: string}): React.JSX.Element {
  const {palette} = useTheme();
  return (
    <Glass radius={radius.card} bordered style={styles.empty}>
      <Text style={[styles.emptyText, {color: palette.secondaryLabel}]}>
        {text}
      </Text>
    </Glass>
  );
}

/* -- Pure helpers ---------------------------------------------------------- */

/** The thread's primary (first) event — used as the sheet target for the card. */
function primaryEvent(thread: Thread): EnrichedResult {
  return thread.events[0];
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

/** Issuer bank for the thread avatar (first event that carries one). */
function threadBank(thread: Thread): string | null {
  for (const ev of thread.events) {
    if (ev.transaction?.bank) {
      return ev.transaction.bank;
    }
  }
  return null;
}

/** Raw merchant fallback when the thread has no canonical merchant. */
function primaryMerchant(thread: Thread): string | null {
  for (const ev of thread.events) {
    if (ev.transaction?.merchant) {
      return ev.transaction.merchant;
    }
  }
  return null;
}

/** A short human label for a thread event's lifecycle stage. */
function stageLabel(ev: EnrichedResult): string {
  if (ev.transaction) {
    return ev.transaction.type; // DEBIT / CREDIT / REFUND
  }
  return ev.excludeReason ?? 'EVENT';
}

/**
 * First included result (with its thread, if any) per canonical merchant, so a
 * merchant rollup can open a concrete detail sheet.
 */
function representativeByMerchant(
  results: EnrichedResult[],
): Map<string, {result: EnrichedResult; thread: Thread | null}> {
  const map = new Map<
    string,
    {result: EnrichedResult; thread: Thread | null}
  >();
  for (const r of results) {
    const key = r.merchantCanonical;
    if (key && !map.has(key)) {
      map.set(key, {result: r, thread: null});
    }
  }
  return map;
}

const styles = StyleSheet.create({
  content: {paddingHorizontal: space.md},
  card: {marginBottom: space.md},
  list: {paddingHorizontal: space.lg, paddingVertical: space.xs},
  sectionTitle: {
    marginTop: space.sm,
    marginBottom: space.sm,
    paddingHorizontal: space.xs,
  },
  sectionH: {...type.title3, fontWeight: '700'},
  sectionSub: {...type.footnote, marginTop: 2},
  empty: {padding: space.lg, marginBottom: space.md},
  emptyText: {...type.subhead, textAlign: 'center'},
  footer: {
    ...type.caption,
    lineHeight: 17,
    marginTop: space.xs,
    marginBottom: space.lg,
    paddingHorizontal: space.xs,
  },

  // Thread card
  threadHead: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: space.md,
  },
  avatar: {
    width: 40,
    height: 40,
    borderRadius: radius.control,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: space.md,
  },
  avatarText: {fontSize: 13, fontWeight: '800'},
  threadHeadBody: {flex: 1, marginRight: space.sm},
  threadTitle: {...type.headline},
  threadSub: {...type.footnote, marginTop: 2},
  netWrap: {alignItems: 'flex-end'},
  netLabel: {...type.caption, letterSpacing: 0.6, fontWeight: '700'},
  netAmount: {fontSize: 17, fontWeight: '700', marginTop: 1},
  timeline: {
    borderTopWidth: StyleSheet.hairlineWidth,
    paddingHorizontal: space.md,
    paddingVertical: space.sm,
  },
  eventRow: {flexDirection: 'row', alignItems: 'center', paddingVertical: 4},
  eventStage: {width: 16, alignItems: 'center'},
  dot: {width: 8, height: 8, borderRadius: 4},
  connector: {position: 'absolute', top: 12, width: 2, height: 18},
  eventLabel: {flex: 1, ...type.subhead, marginLeft: space.sm},
  eventAmount: {...type.footnote, marginLeft: space.sm},

  // Merchant row
  merchantRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: space.md,
  },
  merchantBody: {flex: 1, marginRight: space.md},
  merchantTitleLine: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.xs,
  },
  merchantName: {...type.headline, flexShrink: 1},
  merchantMeta: {...type.footnote, marginTop: 2},
  merchantTotal: {fontSize: 17, fontWeight: '700'},
});
