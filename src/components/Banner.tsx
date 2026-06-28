import React from 'react';
import {StyleSheet, Text, TouchableOpacity, View} from 'react-native';
import {radius, space, type} from '../theme';
import {useTheme} from '../theme/ThemeContext';
import Glass from './Glass';

/**
 * A non-blocking, dismissable banner shown below the floating glass toolbar
 * (WS-5, buildphase-v2.md §6 task 4). Used for friendly import errors
 * ("variant: error") and for visible notices such as the large-file truncation
 * cap ("variant: notice"). It overlays the content layer and never blocks it —
 * a malformed file informs the user instead of crashing the app.
 */
export type BannerVariant = 'error' | 'notice';

export default function Banner({
  message,
  variant = 'notice',
  onDismiss,
}: {
  message: string;
  variant?: BannerVariant;
  onDismiss?: () => void;
}): React.JSX.Element {
  const {palette} = useTheme();
  const accent = variant === 'error' ? palette.debit : palette.accent;
  return (
    <Glass radius={radius.control} bordered style={styles.banner}>
      <View style={[styles.stripe, {backgroundColor: accent}]} />
      <Text
        style={[styles.message, {color: palette.label}]}
        accessibilityLiveRegion="polite">
        {message}
      </Text>
      {onDismiss ? (
        <TouchableOpacity
          onPress={onDismiss}
          accessibilityRole="button"
          accessibilityLabel="Dismiss"
          hitSlop={{top: 8, bottom: 8, left: 8, right: 8}}
          style={styles.dismiss}>
          <Text style={[styles.dismissText, {color: palette.secondaryLabel}]}>
            ✕
          </Text>
        </TouchableOpacity>
      ) : null}
    </Glass>
  );
}

const styles = StyleSheet.create({
  banner: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: space.sm,
    paddingHorizontal: space.md,
  },
  stripe: {
    width: 3,
    alignSelf: 'stretch',
    borderRadius: radius.pill,
    marginRight: space.sm,
  },
  message: {flex: 1, ...type.footnote, lineHeight: 18},
  dismiss: {marginLeft: space.sm, padding: space.xs},
  dismissText: {fontSize: 14, fontWeight: '600'},
});
