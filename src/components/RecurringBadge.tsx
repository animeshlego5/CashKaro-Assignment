import React from 'react';
import {StyleSheet, Text, View} from 'react-native';
import {radius, space} from '../theme';
import {useTheme} from '../theme/ThemeContext';

/**
 * A small "Recurring" pill (WS-6) for canonical merchants the contextual engine
 * flagged as recurring/subscription (appears >=2x in the session, or matches a
 * known-subscription token, or carries a future-auto-debit signal). Uses the
 * accent tint so it reads as an additive enrichment signal, distinct from the
 * neutral category chip and the txn-type chip.
 */
export default function RecurringBadge(): React.JSX.Element {
  const {palette} = useTheme();
  return (
    <View style={[styles.badge, {backgroundColor: palette.chipBg}]}>
      <Text style={[styles.dot, {color: palette.accent}]}>↻</Text>
      <Text style={[styles.text, {color: palette.accent}]}>Recurring</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  badge: {
    flexDirection: 'row',
    alignItems: 'center',
    borderRadius: radius.chip,
    paddingHorizontal: space.sm,
    paddingVertical: 3,
    alignSelf: 'flex-start',
  },
  dot: {fontSize: 11, fontWeight: '700', marginRight: 3},
  text: {fontSize: 11, fontWeight: '700', letterSpacing: 0.3},
});
