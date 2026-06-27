import React from 'react';
import {StyleSheet, Text, View} from 'react-native';
import {ParsedResult} from '../native/SmsParser';
import {colors, formatAmount, space} from '../theme';

/**
 * Summary header (docs/UI-Requirements.md §1): included/excluded counts, INR
 * debit + INR credit/refund totals, and the count-by-exclude-reason.
 *
 * C7: INR totals sum ONLY currency === 'INR' rows — foreign-currency
 * transactions (e.g. the USD Netflix spend) are excluded from the INR figures.
 */
export default function SummaryHeader({results}: {results: ParsedResult[]}): React.JSX.Element {
  const included = results.filter(r => r.decision === 'INCLUDE');
  const excluded = results.filter(r => r.decision === 'EXCLUDE');

  const inrDebit = included
    .filter(r => r.transaction?.currency === 'INR' && r.transaction?.type === 'DEBIT')
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
    <View style={styles.card}>
      <Text style={styles.title}>Credit-Card Transactions</Text>

      <View style={styles.countsRow}>
        <Stat label="Included" value={included.length} color={colors.credit} />
        <Stat label="Excluded" value={excluded.length} color={colors.subtle} />
      </View>

      <View style={styles.totals}>
        <Text style={styles.totalLabel}>
          INR Debit:{' '}
          <Text style={[styles.amount, {color: colors.debit}]}>{formatAmount(inrDebit, 'INR')}</Text>
        </Text>
        <Text style={styles.totalLabel}>
          INR Credit/Refund:{' '}
          <Text style={[styles.amount, {color: colors.credit}]}>
            {formatAmount(inrCreditRefund, 'INR')}
          </Text>
        </Text>
      </View>

      <Text style={styles.exTitle}>Top Exclusions</Text>
      <Text style={styles.exList}>{topExclusions || '—'}</Text>
    </View>
  );
}

function Stat({label, value, color}: {label: string; value: number; color: string}): React.JSX.Element {
  return (
    <View style={styles.stat}>
      <Text style={[styles.statValue, {color}]}>{value}</Text>
      <Text style={styles.statLabel}>{label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: colors.card,
    borderRadius: 12,
    padding: space.lg,
    marginBottom: space.md,
    borderWidth: 1,
    borderColor: colors.border,
  },
  title: {fontSize: 18, fontWeight: '800', color: colors.text, marginBottom: space.md},
  countsRow: {flexDirection: 'row', marginBottom: space.md},
  stat: {marginRight: space.xl},
  statValue: {fontSize: 26, fontWeight: '800'},
  statLabel: {fontSize: 12, color: colors.subtle, textTransform: 'uppercase', letterSpacing: 0.5},
  totals: {marginBottom: space.md},
  totalLabel: {fontSize: 15, color: colors.text, marginBottom: space.xs},
  amount: {fontWeight: '800'},
  exTitle: {fontSize: 12, color: colors.subtle, textTransform: 'uppercase', letterSpacing: 0.5},
  exList: {fontSize: 13, color: colors.text, marginTop: space.xs, lineHeight: 18},
});
