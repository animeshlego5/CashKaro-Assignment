import React from 'react';
import {StyleSheet, Text, TouchableOpacity, View} from 'react-native';
import {ParsedResult} from '../native/SmsParser';
import {bankInitials, colors, formatAmount, space} from '../theme';
import Chip from './Chip';
import ConfidenceIndicator from './ConfidenceIndicator';

/**
 * One result row (docs/UI-Requirements.md §2). Included rows show the bank
 * initials, merchant, amount+currency, date, type and confidence; excluded rows
 * are dimmed and show the reason chip, a short SMS preview and confidence.
 */
export default function ResultRow({
  result,
  onPress,
}: {
  result: ParsedResult;
  onPress: () => void;
}): React.JSX.Element {
  const included = result.decision === 'INCLUDE' && result.transaction != null;
  return (
    <TouchableOpacity
      style={[styles.row, !included && styles.rowDimmed]}
      onPress={onPress}
      activeOpacity={0.6}>
      {included ? <IncludedRow result={result} /> : <ExcludedRow result={result} />}
    </TouchableOpacity>
  );
}

function IncludedRow({result}: {result: ParsedResult}): React.JSX.Element {
  const t = result.transaction!;
  const tone = t.type === 'DEBIT' ? colors.debit : colors.credit;
  return (
    <>
      <View style={styles.avatar}>
        <Text style={styles.avatarText}>{bankInitials(t.bank)}</Text>
      </View>
      <View style={styles.body}>
        <View style={styles.line}>
          <Text style={styles.primary} numberOfLines={1}>
            {t.merchant ?? t.bank ?? 'Transaction'}
          </Text>
          <Text style={[styles.amount, {color: tone}]}>{formatAmount(t.amount, t.currency)}</Text>
        </View>
        <View style={styles.line}>
          <Text style={styles.meta} numberOfLines={1}>
            {t.bank ?? 'Unknown bank'}
            {t.cardLastFour ? ` ••${t.cardLastFour}` : ''}
            {t.date ? ` · ${t.date}` : ''}
          </Text>
          <ConfidenceIndicator confidence={result.confidence} />
        </View>
        <View style={styles.chipLine}>
          <Chip label={t.type} color={tone} />
        </View>
      </View>
    </>
  );
}

function ExcludedRow({result}: {result: ParsedResult}): React.JSX.Element {
  return (
    <View style={styles.body}>
      <View style={styles.line}>
        <Chip label={result.excludeReason ?? 'EXCLUDED'} />
        <ConfidenceIndicator confidence={result.confidence} />
      </View>
      <Text style={styles.preview} numberOfLines={2}>
        {result.rawSms}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    backgroundColor: colors.card,
    borderRadius: 10,
    padding: space.md,
    marginBottom: space.sm,
    borderWidth: 1,
    borderColor: colors.border,
  },
  rowDimmed: {backgroundColor: '#fafafa', opacity: 0.85},
  avatar: {
    width: 38,
    height: 38,
    borderRadius: 19,
    backgroundColor: colors.accent,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: space.md,
  },
  avatarText: {color: '#ffffff', fontSize: 13, fontWeight: '800'},
  body: {flex: 1},
  line: {flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between'},
  chipLine: {flexDirection: 'row', marginTop: space.xs},
  primary: {flex: 1, fontSize: 15, fontWeight: '700', color: colors.text, marginRight: space.sm},
  amount: {fontSize: 15, fontWeight: '800'},
  meta: {flex: 1, fontSize: 12, color: colors.subtle, marginRight: space.sm, marginTop: 2},
  preview: {fontSize: 13, color: colors.subtle, marginTop: space.xs, lineHeight: 18},
});
