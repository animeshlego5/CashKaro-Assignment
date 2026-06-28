import React from 'react';
import {
  ActivityIndicator,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import {radius, space, type} from '../theme';
import {useTheme} from '../theme/ThemeContext';
import Glass from './Glass';

/**
 * Floating glass toolbar (WS-4 / D3): the large title + an Import action,
 * rendered on a translucent surface that the list scrolls beneath — the
 * defining iOS-26 content/controls separation.
 *
 * The Import button opens the system file picker (WS-5); while a pick/parse is
 * in flight it shows a spinner and is disabled to prevent re-entry.
 */
export default function Toolbar({
  title,
  onImport,
  importing = false,
}: {
  title: string;
  onImport?: () => void;
  importing?: boolean;
}): React.JSX.Element {
  const {palette} = useTheme();
  return (
    <Glass radius={radius.outer} bordered style={styles.bar}>
      <View style={styles.row}>
        <Text style={[styles.title, {color: palette.label}]} numberOfLines={1}>
          {title}
        </Text>
        <TouchableOpacity
          activeOpacity={0.7}
          onPress={onImport}
          disabled={importing}
          accessibilityRole="button"
          accessibilityLabel="Import SMS"
          accessibilityState={{disabled: importing, busy: importing}}
          style={[
            styles.importBtn,
            {backgroundColor: palette.chipBg},
            importing && styles.importBtnBusy,
          ]}>
          {importing ? (
            <ActivityIndicator size="small" color={palette.accent} />
          ) : (
            <Text style={[styles.importText, {color: palette.accent}]}>
              Import
            </Text>
          )}
        </TouchableOpacity>
      </View>
    </Glass>
  );
}

const styles = StyleSheet.create({
  bar: {alignSelf: 'stretch'},
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: space.lg,
    paddingVertical: space.md,
  },
  title: {...type.title, flex: 1, marginRight: space.md},
  importBtn: {
    paddingHorizontal: space.md,
    paddingVertical: space.sm,
    borderRadius: radius.control,
    minWidth: 72,
    alignItems: 'center',
    justifyContent: 'center',
  },
  importBtnBusy: {opacity: 0.7},
  importText: {...type.subhead, fontWeight: '700'},
});
