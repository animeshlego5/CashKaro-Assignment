import React, {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react';
import {AccessibilityInfo, useColorScheme} from 'react-native';
import {paletteFor, Palette} from '../theme';

/**
 * Appearance + motion context for the Liquid Glass UI (WS-4).
 *
 * Exposes the active iOS system palette (light/dark, adaptive) and whether the
 * user has Reduce Motion enabled, so spring transitions can be toned down
 * (buildphase-v2.md §6 task 5 / AccessibilityInfo.isReduceMotionEnabled).
 */
export interface Theme {
  palette: Palette;
  isDark: boolean;
  reduceMotion: boolean;
}

const fallback: Theme = {
  palette: paletteFor('light'),
  isDark: false,
  reduceMotion: false,
};

const ThemeContext = createContext<Theme>(fallback);

export function ThemeProvider({
  children,
}: {
  children: React.ReactNode;
}): React.JSX.Element {
  const scheme = useColorScheme();
  const isDark = scheme === 'dark';
  const [reduceMotion, setReduceMotion] = useState(false);

  useEffect(() => {
    let mounted = true;
    AccessibilityInfo.isReduceMotionEnabled()
      .then(v => {
        if (mounted) {
          setReduceMotion(v);
        }
      })
      .catch(() => {});
    const sub = AccessibilityInfo.addEventListener('reduceMotionChanged', v =>
      setReduceMotion(v),
    );
    return () => {
      mounted = false;
      // RN >= 0.65 returns a subscription with .remove()
      sub?.remove?.();
    };
  }, []);

  const value = useMemo<Theme>(
    () => ({palette: paletteFor(scheme), isDark, reduceMotion}),
    [scheme, isDark, reduceMotion],
  );

  return (
    <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
  );
}

/** Read the active theme (palette + appearance + reduce-motion flag). */
export function useTheme(): Theme {
  return useContext(ThemeContext);
}
