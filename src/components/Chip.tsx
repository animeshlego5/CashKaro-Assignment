import React from 'react';
import {StyleSheet, Text, View} from 'react-native';
import {colors, space} from '../theme';

/** A small rounded badge — used for exclude reasons and transaction types. */
export default function Chip({label, color}: {label: string; color?: string}): React.JSX.Element {
  return (
    <View style={[styles.chip, color ? {backgroundColor: color + '22'} : null]}>
      <Text style={[styles.text, color ? {color} : null]}>{label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  chip: {
    backgroundColor: colors.chipBg,
    borderRadius: 6,
    paddingHorizontal: space.sm,
    paddingVertical: 2,
    alignSelf: 'flex-start',
  },
  text: {color: colors.chipText, fontSize: 11, fontWeight: '700', letterSpacing: 0.3},
});
