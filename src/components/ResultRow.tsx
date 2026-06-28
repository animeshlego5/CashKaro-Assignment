import React from 'react';
import {StyleSheet, Text, TouchableOpacity, View} from 'react-native';
import {EnrichedResult, isEnriched, ParsedResult} from '../native/SmsParser';
import {
  bankInitials,
  elevation,
  formatAmount,
  radius,
  space,
  type,
} from '../theme';
import {useTheme} from '../theme/ThemeContext';
import Chip from './Chip';
import ConfidenceIndicator from './ConfidenceIndicator';
import EnrichmentLine from './EnrichmentLine';
import Glass from './Glass';

/**
 * One result row (docs/UI-Requirements.md §2), styled as a Liquid Glass card.
 * Included rows show the bank initials, merchant, amount+currency, date, type
 * and confidence; excluded rows are dimmed and show the reason chip, a short
 * SMS preview and confidence.
 *
 * WS-6: when the row carries contextual-engine enrichment (i.e. it came from
 * `parseSmsSession`), an additive enrichment line is shown on INCLUDED rows —
 * the canonical merchant, a category chip and a "Recurring" badge where flagged.
 * The core parsed fields above it are unchanged (V1/V6); a plain `parseSms`
 * result (no enrichment keys) renders exactly as before.
 */
export default function ResultRow({
  result,
  onPress,
}: {
  result: ParsedResult | EnrichedResult;
  onPress: () => void;
}): React.JSX.Element {
  const included = result.decision === 'INCLUDE' && result.transaction != null;
  return (
    <TouchableOpacity onPress={onPress} activeOpacity={0.7} style={styles.wrap}>
      <Glass
        radius={radius.card}
        elevation={elevation.card}
        style={included ? undefined : styles.dimmed}>
        <View style={styles.inner}>
          {included ? (
            <IncludedRow result={result} />
          ) : (
            <ExcludedRow result={result} />
          )}
        </View>
      </Glass>
    </TouchableOpacity>
  );
}

function IncludedRow({
  result,
}: {
  result: ParsedResult | EnrichedResult;
}): React.JSX.Element {
  const {palette} = useTheme();
  const t = result.transaction!;
  const tone = t.type === 'DEBIT' ? palette.debit : palette.credit;
  return (
    <View style={styles.row}>
      <View style={[styles.avatar, {backgroundColor: palette.avatarBg}]}>
        <Text style={[styles.avatarText, {color: palette.avatarText}]}>
          {bankInitials(t.bank)}
        </Text>
      </View>
      <View style={styles.body}>
        <View style={styles.line}>
          <Text
            style={[styles.primary, {color: palette.label}]}
            numberOfLines={1}>
            {t.merchant ?? t.bank ?? 'Transaction'}
          </Text>
          <Text style={[styles.amount, {color: tone}]}>
            {formatAmount(t.amount, t.currency)}
          </Text>
        </View>
        <View style={styles.line}>
          <Text
            style={[styles.meta, {color: palette.secondaryLabel}]}
            numberOfLines={1}>
            {t.bank ?? 'Unknown bank'}
            {t.cardLastFour ? ` ••${t.cardLastFour}` : ''}
            {t.date ? ` · ${t.date}` : ''}
          </Text>
          <ConfidenceIndicator confidence={result.confidence} />
        </View>
        <View style={styles.chipLine}>
          <Chip label={t.type} color={tone} />
        </View>
        {isEnriched(result) ? <EnrichmentLine result={result} /> : null}
      </View>
    </View>
  );
}

function ExcludedRow({result}: {result: ParsedResult}): React.JSX.Element {
  const {palette} = useTheme();
  return (
    <View style={styles.body}>
      <View style={styles.line}>
        <Chip label={result.excludeReason ?? 'EXCLUDED'} />
        <ConfidenceIndicator confidence={result.confidence} />
      </View>
      <Text
        style={[styles.preview, {color: palette.secondaryLabel}]}
        numberOfLines={2}>
        {result.rawSms}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: {marginBottom: space.sm},
  inner: {padding: space.md},
  dimmed: {opacity: 0.72},
  row: {flexDirection: 'row'},
  avatar: {
    width: 40,
    height: 40,
    borderRadius: radius.control,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: space.md,
  },
  avatarText: {fontSize: 13, fontWeight: '800'},
  body: {flex: 1},
  line: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  chipLine: {flexDirection: 'row', marginTop: space.sm},
  primary: {flex: 1, ...type.headline, marginRight: space.sm},
  amount: {fontSize: 17, fontWeight: '700'},
  meta: {flex: 1, ...type.footnote, marginRight: space.sm, marginTop: 2},
  preview: {...type.footnote, marginTop: space.sm, lineHeight: 18},
});
