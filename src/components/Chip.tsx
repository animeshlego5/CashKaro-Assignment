import React from 'react';
import {StyleSheet, Text, View} from 'react-native';
import {radius, space} from '../theme';
import {useTheme} from '../theme/ThemeContext';

/** A small rounded badge — used for exclude reasons, txn types and categories. */
export default function Chip({
  label,
  color,
}: {
  label: string;
  color?: string;
}): React.JSX.Element {
  const {palette} = useTheme();
  const tint = color ?? palette.chipText;
  return (
    <View
      style={[
        styles.chip,
        {backgroundColor: color ? color + '22' : palette.chipBg},
      ]}>
      <Text style={[styles.text, {color: tint}]}>{label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  chip: {
    borderRadius: radius.chip,
    paddingHorizontal: space.sm,
    paddingVertical: 3,
    alignSelf: 'flex-start',
  },
  text: {fontSize: 11, fontWeight: '700', letterSpacing: 0.3},
});
