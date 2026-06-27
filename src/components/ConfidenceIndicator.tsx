import React from 'react';
import {StyleSheet, Text, View} from 'react-native';
import {colors, confidenceColor} from '../theme';

/** A compact confidence bar + percentage, coloured by bucket. */
export default function ConfidenceIndicator({confidence}: {confidence: number}): React.JSX.Element {
  const pct = Math.round(confidence * 100);
  const color = confidenceColor(confidence);
  return (
    <View style={styles.row}>
      <View style={styles.track}>
        <View style={[styles.fill, {width: `${pct}%`, backgroundColor: color}]} />
      </View>
      <Text style={[styles.pct, {color}]}>{pct}%</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  row: {flexDirection: 'row', alignItems: 'center'},
  track: {
    width: 40,
    height: 5,
    borderRadius: 3,
    backgroundColor: colors.border,
    overflow: 'hidden',
    marginRight: 6,
  },
  fill: {height: 5, borderRadius: 3},
  pct: {fontSize: 11, fontWeight: '700', minWidth: 32, textAlign: 'right'},
});
