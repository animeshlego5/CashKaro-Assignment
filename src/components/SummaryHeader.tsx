import React from 'react';
import {StyleSheet, Text, View} from 'react-native';
import {ParsedResult} from '../native/SmsParser';
import {elevation, formatAmount, radius, space, type} from '../theme';
import {useTheme} from '../theme/ThemeContext';
import Glass from './Glass';

/**
 * Summary header (docs/UI-Requirements.md §1): included/excluded counts, INR
 * debit + INR credit/refund totals, and the count-by-exclude-reason.
 *
 * WS-4: rendered as a Liquid Glass card. The numbers are unchanged.
 *
 * C7: INR totals sum ONLY currency === 'INR' rows — foreign-currency
 * transactions (e.g. the USD Netflix spend) are excluded from the INR figures.
 */
export default function SummaryHeader({
  results,
}: {
  results: ParsedResult[];
}): React.JSX.Element {
  const {palette} = useTheme();
  const included = results.filter(r => r.decision === 'INCLUDE');
  const excluded = results.filter(r => r.decision === 'EXCLUDE');

  const inrDebit = included
    .filter(
      r => r.transaction?.currency === 'INR' && r.transaction?.type === 'DEBIT',
    )
    .reduce((sum, r) => sum + (r.transaction?.amount ?? 0), 0);
  const inrCreditRefund = included
    .filter(
      r =>
        r.transaction?.currency === 'INR' &&
        (r.transaction?.type === 'REFUND' || r.transaction?.type === 'CREDIT'),
    )
    .reduce((sum, r) => sum + (r.transaction?.amount ?? 0), 0);

  const byReason: Record<string, number> = {};
  for (const r of excluded) {
    const key = r.excludeReason ?? 'UNKNOWN';
    byReason[key] = (byReason[key] ?? 0) + 1;
  }
  const topExclusions = Object.entries(byReason)
    .sort((a, b) => b[1] - a[1])
    .map(([reason, n]) => `${reason}: ${n}`)
    .join(', ');

  return (
    <Glass radius={radius.card} elevation={elevation.card} style={styles.card}>
      <View style={styles.inner}>
        <Text style={[styles.title, {color: palette.label}]}>
          Credit-Card Transactions
        </Text>

        <View style={styles.countsRow}>
          <Stat
            label="Included"
            value={included.length}
            color={palette.credit}
          />
          <Stat
            label="Excluded"
            value={excluded.length}
            color={palette.secondaryLabel}
          />
        </View>

        <View style={[styles.totals, {borderTopColor: palette.separator}]}>
          <Text style={[styles.totalLabel, {color: palette.secondaryLabel}]}>
            INR Debit{'  '}
            <Text style={[styles.amount, {color: palette.debit}]}>
              {formatAmount(inrDebit, 'INR')}
            </Text>
          </Text>
          <Text style={[styles.totalLabel, {color: palette.secondaryLabel}]}>
            INR Credit/Refund{'  '}
            <Text style={[styles.amount, {color: palette.credit}]}>
              {formatAmount(inrCreditRefund, 'INR')}
            </Text>
          </Text>
        </View>

        <Text style={[styles.exTitle, {color: palette.tertiaryLabel}]}>
          Top Exclusions
        </Text>
        <Text style={[styles.exList, {color: palette.label}]}>
          {topExclusions || '—'}
        </Text>
      </View>
    </Glass>
  );
}

function Stat({
  label,
  value,
  color,
}: {
  label: string;
  value: number;
  color: string;
}): React.JSX.Element {
  const {palette} = useTheme();
  return (
    <View style={styles.stat}>
      <Text style={[styles.statValue, {color}]}>{value}</Text>
      <Text style={[styles.statLabel, {color: palette.tertiaryLabel}]}>
        {label}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  card: {marginBottom: space.md},
  inner: {padding: space.lg},
  title: {...type.title3, marginBottom: space.md},
  countsRow: {flexDirection: 'row', marginBottom: space.md},
  stat: {marginRight: space.xl},
  statValue: {fontSize: 30, fontWeight: '700', letterSpacing: 0.4},
  statLabel: {
    ...type.caption,
    textTransform: 'uppercase',
    letterSpacing: 0.6,
    marginTop: 2,
  },
  totals: {
    marginBottom: space.md,
    paddingTop: space.md,
    borderTopWidth: StyleSheet.hairlineWidth,
  },
  totalLabel: {...type.subhead, marginBottom: space.xs},
  amount: {fontWeight: '700'},
  exTitle: {...type.caption, textTransform: 'uppercase', letterSpacing: 0.6},
  exList: {...type.footnote, marginTop: space.xs, lineHeight: 18},
});
