import React from 'react';
import {StyleSheet, Text, View} from 'react-native';
import {confidenceColor} from '../theme';
import {useTheme} from '../theme/ThemeContext';

/** A compact confidence bar + percentage, coloured by bucket (appearance-aware). */
export default function ConfidenceIndicator({
  confidence,
}: {
  confidence: number;
}): React.JSX.Element {
  const {palette} = useTheme();
  const pct = Math.round(confidence * 100);
  const color = confidenceColor(confidence, palette);
  return (
    <View style={styles.row}>
      <View style={[styles.track, {backgroundColor: palette.separator}]}>
        <View
          style={[styles.fill, {width: `${pct}%`, backgroundColor: color}]}
        />
      </View>
      <Text style={[styles.pct, {color}]}>{pct}%</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  row: {flexDirection: 'row', alignItems: 'center'},
  track: {
    width: 44,
    height: 5,
    borderRadius: 3,
    overflow: 'hidden',
    marginRight: 6,
  },
  fill: {height: 5, borderRadius: 3},
  pct: {fontSize: 11, fontWeight: '700', minWidth: 32, textAlign: 'right'},
});
