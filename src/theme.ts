/**
 * Design tokens + formatting helpers for the parser screen.
 *
 * WS-4 (buildphase-v2.md §6 / §9): restyled to Apple's iOS 26 "Liquid Glass"
 * language — a translucent floating control layer over a scrolling content
 * layer. Tokens mirror the iOS system palette + type scale, an 8-pt spacing
 * grid, concentric corner radii and iOS-style elevation, with full light/dark
 * support (Liquid Glass is adaptive).
 *
 * SF Pro is NOT licensable for Android bundling (buildphase-v2.md risk note), so
 * we use the platform default system font. The existing helpers
 * (formatAmount / bankInitials / confidenceColor) are preserved verbatim.
 */
import {ColorSchemeName, Platform} from 'react-native';

/** iOS system palette for one appearance (light or dark). */
export interface Palette {
  /** systemBackground — the base canvas behind everything. */
  systemBackground: string;
  /** secondarySystemBackground — grouped content background. */
  secondarySystemBackground: string;
  /** tertiarySystemBackground — raised cards. */
  tertiarySystemBackground: string;
  /** label — primary text. */
  label: string;
  /** secondaryLabel — captions / metadata. */
  secondaryLabel: string;
  /** tertiaryLabel — the faintest text. */
  tertiaryLabel: string;
  /** separator (hairline, slightly translucent). */
  separator: string;
  /** opaqueSeparator (solid hairline for borders on glass). */
  opaqueSeparator: string;
  /** systemBlue — the iOS accent / tint. */
  accent: string;
  /** Spend (red, system red). */
  debit: string;
  /** Credit / refund (green, system green). */
  credit: string;
  chipBg: string;
  chipText: string;
  confHigh: string;
  confMid: string;
  confLow: string;
  /** Glass surface fallback colour (V5) for API < 33 — a solid translucent fill. */
  glassFallback: string;
  /** Hairline border drawn on glass surfaces. */
  glassBorder: string;
  /** Tint colour layered over real blur to match the appearance. */
  glassTint: string;
  /** Avatar circle background. */
  avatarBg: string;
  /** Avatar text colour. */
  avatarText: string;
  /** Grabber handle on the detail sheet. */
  grabber: string;
}

/** Light appearance — iOS 26 system colours. */
const light: Palette = {
  systemBackground: '#f2f2f7',
  secondarySystemBackground: '#ffffff',
  tertiarySystemBackground: '#ffffff',
  label: '#000000',
  secondaryLabel: '#3c3c43',
  tertiaryLabel: '#8e8e93',
  separator: 'rgba(60,60,67,0.18)',
  opaqueSeparator: 'rgba(60,60,67,0.29)',
  accent: '#007aff',
  debit: '#ff3b30',
  credit: '#34c759',
  chipBg: 'rgba(0,122,255,0.12)',
  chipText: '#0a84ff',
  confHigh: '#34c759',
  confMid: '#ff9500',
  confLow: '#ff3b30',
  glassFallback: 'rgba(250,250,252,0.72)',
  glassBorder: 'rgba(255,255,255,0.55)',
  glassTint: 'rgba(255,255,255,0.30)',
  avatarBg: 'rgba(0,122,255,0.16)',
  avatarText: '#007aff',
  grabber: 'rgba(60,60,67,0.30)',
};

/** Dark appearance — iOS 26 system colours. */
const dark: Palette = {
  systemBackground: '#000000',
  secondarySystemBackground: '#1c1c1e',
  tertiarySystemBackground: '#2c2c2e',
  label: '#ffffff',
  secondaryLabel: '#ebebf5',
  tertiaryLabel: '#8e8e93',
  separator: 'rgba(84,84,88,0.40)',
  opaqueSeparator: 'rgba(84,84,88,0.65)',
  accent: '#0a84ff',
  debit: '#ff453a',
  credit: '#30d158',
  chipBg: 'rgba(10,132,255,0.22)',
  chipText: '#64d2ff',
  confHigh: '#30d158',
  confMid: '#ff9f0a',
  confLow: '#ff453a',
  glassFallback: 'rgba(28,28,30,0.72)',
  glassBorder: 'rgba(255,255,255,0.12)',
  glassTint: 'rgba(28,28,30,0.30)',
  avatarBg: 'rgba(10,132,255,0.24)',
  avatarText: '#64d2ff',
  grabber: 'rgba(235,235,245,0.30)',
};

export const palettes = {light, dark};

/** Resolve the palette for a given system colour scheme (default light). */
export function paletteFor(scheme: ColorSchemeName): Palette {
  return scheme === 'dark' ? dark : light;
}

/**
 * Back-compat default export of colour tokens.
 *
 * Older components import `{colors}` directly; we keep that working by mapping
 * the original token names onto the light palette. New glass components consume
 * the appearance-aware `paletteFor()` via the ThemeProvider instead.
 */
export const colors = {
  bg: light.systemBackground,
  card: light.secondarySystemBackground,
  text: light.label,
  subtle: light.secondaryLabel,
  border: light.separator,
  debit: light.debit,
  credit: light.credit,
  accent: light.accent,
  chipBg: light.chipBg,
  chipText: light.chipText,
  confHigh: light.confHigh,
  confMid: light.confMid,
  confLow: light.confLow,
};

/** 8-pt spacing grid (iOS). xs=4 kept for hairline gaps. */
export const space = {xs: 4, sm: 8, md: 12, lg: 16, xl: 24, xxl: 32};

/** Concentric corner radii — outer surfaces large, nested ones smaller. */
export const radius = {
  /** Floating toolbar / large sheets. */
  outer: 28,
  /** Cards inside the scroll. */
  card: 20,
  /** Nested controls (segmented control, buttons). */
  control: 14,
  /** Chips / small nested elements. */
  chip: 9,
  /** Fully-rounded pills. */
  pill: 999,
};

/** iOS type scale (sizes/weights) — Large Title, Title, Body, Footnote, etc. */
export const type = {
  largeTitle: {fontSize: 34, fontWeight: '700' as const, letterSpacing: 0.4},
  title: {fontSize: 28, fontWeight: '700' as const, letterSpacing: 0.3},
  title3: {fontSize: 20, fontWeight: '600' as const},
  headline: {fontSize: 17, fontWeight: '600' as const},
  body: {fontSize: 17, fontWeight: '400' as const},
  callout: {fontSize: 16, fontWeight: '400' as const},
  subhead: {fontSize: 15, fontWeight: '400' as const},
  footnote: {fontSize: 13, fontWeight: '400' as const},
  caption: {fontSize: 12, fontWeight: '400' as const},
  /** System font family: platform default (no SF Pro bundling — licence). */
  family: Platform.select({
    ios: undefined,
    android: undefined,
    default: undefined,
  }),
};

/** iOS-style elevation/shadow presets (subtle, soft). */
export const elevation = {
  /** Floating glass control layer — sits above content. */
  floating: {
    shadowColor: '#000000',
    shadowOpacity: 0.18,
    shadowRadius: 20,
    shadowOffset: {width: 0, height: 8},
    elevation: 12,
  },
  /** Cards in the scroll. */
  card: {
    shadowColor: '#000000',
    shadowOpacity: 0.08,
    shadowRadius: 10,
    shadowOffset: {width: 0, height: 4},
    elevation: 4,
  },
  /** Modal sheet. */
  sheet: {
    shadowColor: '#000000',
    shadowOpacity: 0.25,
    shadowRadius: 24,
    shadowOffset: {width: 0, height: -6},
    elevation: 16,
  },
};

const SYMBOL: Record<string, string> = {
  INR: '₹',
  USD: 'US$',
  EUR: '€',
  AED: 'AED ',
};

/** Indian digit grouping for the integer part (145300 -> "1,45,300"). */
function groupIndian(intPart: string): string {
  if (intPart.length <= 3) {
    return intPart;
  }
  const last3 = intPart.slice(-3);
  const rest = intPart.slice(0, -3);
  return rest.replace(/\B(?=(\d{2})+(?!\d))/g, ',') + ',' + last3;
}

/** Human amount: "₹1,45,300.00" for INR; "US$49.99" / "€89.50" otherwise. */
export function formatAmount(amount: number, currency: string): string {
  const symbol = SYMBOL[currency] ?? `${currency} `;
  const [intPart, decPart] = amount.toFixed(2).split('.');
  const grouped =
    currency === 'INR'
      ? groupIndian(intPart)
      : intPart.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  return `${symbol}${grouped}.${decPart}`;
}

/** Bank initials for the row avatar: "HDFC Bank" -> "HB"; "State Bank of India" -> "SBI"; null -> "?". */
export function bankInitials(bank: string | null): string {
  if (!bank) {
    return '?';
  }
  const words = bank
    .split(/\s+/)
    .filter(w => !['of', 'the', 'and'].includes(w.toLowerCase()));
  const initials = words.map(w => w.charAt(0).toUpperCase()).join('');
  return initials.slice(0, 3) || '?';
}

/**
 * Human-readable category label for the enrichment chip (WS-6): the Kotlin
 * engine emits lowercase category ids ("entertainment", "food"); we present them
 * Capitalised. null/empty -> null so callers can omit the chip.
 */
export function categoryLabel(category: string | null): string | null {
  if (!category) {
    return null;
  }
  const trimmed = category.trim();
  if (trimmed.length === 0) {
    return null;
  }
  return trimmed.charAt(0).toUpperCase() + trimmed.slice(1);
}

/**
 * Confidence bucket colour (high >= 0.85, mid >= 0.6, else low).
 *
 * Back-compat signature: uses the light palette by default. Pass a palette to
 * get the appearance-correct colour.
 */
export function confidenceColor(
  confidence: number,
  p: Palette = light,
): string {
  if (confidence >= 0.85) {
    return p.confHigh;
  }
  if (confidence >= 0.6) {
    return p.confMid;
  }
  return p.confLow;
}
