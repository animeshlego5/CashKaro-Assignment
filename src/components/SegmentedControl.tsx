import React from 'react';
import {StyleSheet, Text, TouchableOpacity, View} from 'react-native';
import {radius, space, type} from '../theme';
import {useTheme} from '../theme/ThemeContext';
import Glass from './Glass';

/**
 * iOS-style glass segmented control (WS-4 / D3). Switches the screen between
 * "Messages" and "Insights". The selected pill is a small concentric glass
 * surface nested inside the larger glass track.
 */
export default function SegmentedControl<T extends string>({
  segments,
  value,
  onChange,
}: {
  segments: readonly T[];
  value: T;
  onChange: (v: T) => void;
}): React.JSX.Element {
  const {palette} = useTheme();
  return (
    <Glass radius={radius.control} bordered style={styles.track}>
      <View style={styles.row}>
        {segments.map(seg => {
          const active = seg === value;
          return (
            <TouchableOpacity
              key={seg}
              activeOpacity={0.8}
              onPress={() => onChange(seg)}
              style={[
                styles.segment,
                active && {backgroundColor: palette.secondarySystemBackground},
              ]}
              accessibilityRole="button"
              accessibilityState={{selected: active}}>
              <Text
                style={[
                  styles.label,
                  {color: active ? palette.label : palette.secondaryLabel},
                  active && styles.labelActive,
                ]}>
                {seg}
              </Text>
            </TouchableOpacity>
          );
        })}
      </View>
    </Glass>
  );
}

const styles = StyleSheet.create({
  track: {alignSelf: 'stretch'},
  row: {flexDirection: 'row', padding: 3},
  segment: {
    flex: 1,
    paddingVertical: space.sm,
    borderRadius: radius.control - 4,
    alignItems: 'center',
    justifyContent: 'center',
  },
  label: {...type.subhead, fontWeight: '600'},
  labelActive: {fontWeight: '700'},
});
