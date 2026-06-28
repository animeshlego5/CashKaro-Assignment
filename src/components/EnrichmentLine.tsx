import React from 'react';
import {StyleSheet, Text, View} from 'react-native';
import {EnrichedResult} from '../native/SmsParser';
import {categoryLabel, space, type} from '../theme';
import {useTheme} from '../theme/ThemeContext';
import Chip from './Chip';
import RecurringBadge from './RecurringBadge';

/**
 * The additive enrichment line shown on INCLUDED rows in the Messages segment
 * (WS-6 task 1): the canonical merchant the engine resolved (e.g. "Netflix"), a
 * category chip, and a "Recurring" badge where flagged. Purely additive — the
 * core parsed fields above it are untouched (V1/V6). Renders nothing when the
 * engine produced no enrichment for this row (conservative; null when no token
 * hit), so plain non-enriched rows are unaffected.
 */
export default function EnrichmentLine({
  result,
}: {
  result: EnrichedResult;
}): React.JSX.Element | null {
  const {palette} = useTheme();
  const category = categoryLabel(result.category);
  const hasCanonical = !!result.merchantCanonical;
  // Nothing the engine added for this row -> render nothing (keeps the row clean).
  if (!hasCanonical && !category && !result.recurring) {
    return null;
  }
  return (
    <View
      style={[styles.line, {borderTopColor: palette.separator}]}
      accessibilityLabel="Enrichment">
      {hasCanonical ? (
        <Text
          style={[styles.canonical, {color: palette.secondaryLabel}]}
          numberOfLines={1}>
          {result.merchantCanonical}
        </Text>
      ) : (
        <View style={styles.spacer} />
      )}
      <View style={styles.badges}>
        {category ? <Chip label={category} /> : null}
        {result.recurring ? <RecurringBadge /> : null}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  line: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginTop: space.sm,
    paddingTop: space.sm,
    borderTopWidth: StyleSheet.hairlineWidth,
  },
  spacer: {flex: 1},
  canonical: {
    flex: 1,
    ...type.footnote,
    fontWeight: '600',
    marginRight: space.sm,
  },
  badges: {flexDirection: 'row', alignItems: 'center', gap: space.xs},
});
