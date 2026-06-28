import React from 'react';
import {Platform, StyleProp, StyleSheet, View, ViewStyle} from 'react-native';
import {BlurView} from '@sbaiahmed1/react-native-blur';
import {useTheme} from '../theme/ThemeContext';
import {radius} from '../theme';

/**
 * Glass — the single, library-agnostic translucent surface for the whole UI
 * (buildphase-v2.md WS-4 / §6). Everything that should read as "Liquid Glass"
 * (the floating toolbar, the segmented control, summary + result cards, the
 * detail sheet) renders through this one wrapper, so the rest of the app never
 * imports a blur library directly. Swapping the underlying glass library is a
 * one-file change here.
 *
 * V5 — Graceful platform fallback:
 *   - Real GPU blur (@sbaiahmed1/react-native-blur's native BlurView) needs a
 *     capable renderer. On Android, AGSL/RenderEffect blur is API 33+; below
 *     that we render a SOLID TRANSLUCENT surface (rgba(250,250,252,0.72) light /
 *     rgba(28,28,30,0.72) dark) + a hairline border so the app still looks
 *     intentional down to minSdk 23.
 *   - We always layer a faint appearance tint over real blur so it reads as
 *     light glass in light mode and dark glass in dark mode.
 */

/** True when the platform can render real native blur for the glass effect. */
export function isGlassSupported(): boolean {
  // iOS supports UIVisualEffectView broadly; Android needs API 33+ (AGSL /
  // RenderEffect) for a real-time blur — below that we fall back (V5).
  if (Platform.OS === 'ios') {
    return true;
  }
  if (Platform.OS === 'android') {
    return Number(Platform.Version) >= 33;
  }
  return false;
}

export interface GlassProps {
  children?: React.ReactNode;
  style?: StyleProp<ViewStyle>;
  /** Corner radius of the surface (concentric radii live in theme.radius). */
  radius?: number;
  /** Blur strength 0-100 (only used on the real-blur path). Default 24. */
  intensity?: number;
  /** Render a hairline border on the surface. Default true. */
  bordered?: boolean;
  /** Apply an iOS-style elevation/shadow. Pass a shadow style object. */
  elevation?: ViewStyle;
}

export default function Glass({
  children,
  style,
  radius: r = radius.card,
  intensity = 24,
  bordered = true,
  elevation,
}: GlassProps): React.JSX.Element {
  const {palette, isDark} = useTheme();
  const supported = isGlassSupported();

  const border: ViewStyle = bordered
    ? {borderWidth: StyleSheet.hairlineWidth, borderColor: palette.glassBorder}
    : {};

  if (!supported) {
    // V5 fallback: solid translucent surface so it still looks like glass.
    return (
      <View
        style={[
          styles.clip,
          {backgroundColor: palette.glassFallback, borderRadius: r},
          border,
          elevation,
          style,
        ]}>
        {children}
      </View>
    );
  }

  // Real native blur. We clip to the radius and tint to the appearance so the
  // blur reads as adaptive Liquid Glass.
  return (
    <View style={[styles.clip, {borderRadius: r}, border, elevation, style]}>
      <BlurView
        blurType={isDark ? 'dark' : 'light'}
        blurAmount={intensity}
        overlayColor={palette.glassTint}
        style={StyleSheet.absoluteFill}
      />
      {children}
    </View>
  );
}

const styles = StyleSheet.create({
  // Clip children (and the blur layer) to the rounded surface.
  clip: {overflow: 'hidden'},
});
