/**
 * @format
 *
 * Device-free UI smoke tests: the native bridge is mocked, so these run with no
 * Android module / emulator. They verify the summary's INR totals EXCLUDE
 * foreign currency (C7), that included + excluded rows render, the enrichment
 * line on an included row (WS-6), that the screen runs the contextual engine
 * (parseSmsSession) on mount and feeds Messages from its results, and that the
 * Insights segment renders threads / merchant rollups / recurring (WS-6).
 * (On-device visual verification is done separately once a phone is connected.)
 */
import 'react-native';
import React from 'react';
import renderer, {act} from 'react-test-renderer';
import {describe, expect, it, jest} from '@jest/globals';

// Mock the native bridge wrapper so no Android module / emulator is needed.
// The screen (WS-6) drives the contextual engine (parseSmsSession); parseSms is
// stubbed too so the module's named bindings all resolve. isEnriched is the real
// implementation (a pure type guard) so enriched rows light up the WS-6 line.
jest.mock('../src/native/SmsParser', () => {
  const actual = jest.requireActual('../src/native/SmsParser') as object;
  return {
    __esModule: true,
    ...actual,
    parseSms: jest.fn(),
    parseSmsSession: jest.fn(),
  };
});

import {
  EnrichedResult,
  ParsedResult,
  parseSmsSession,
  SessionResult,
} from '../src/native/SmsParser';
import ParserScreen from '../src/screens/ParserScreen';
import ResultRow from '../src/components/ResultRow';
import SummaryHeader from '../src/components/SummaryHeader';

/** Collect rendered text by walking children only (avoids circular element props). */
function collectText(node: unknown): string {
  if (node == null) {
    return '';
  }
  if (typeof node === 'string') {
    return node;
  }
  if (typeof node === 'number') {
    return String(node);
  }
  if (Array.isArray(node)) {
    return node.map(collectText).join(' ');
  }
  return collectText((node as {children?: unknown}).children);
}

const fixture: ParsedResult[] = [
  {
    rawSms: 'INR 1,250.00 spent on HDFC Bank Credit Card xx5678 at SWIGGY',
    decision: 'INCLUDE',
    excludeReason: null,
    transaction: {
      amount: 1250,
      currency: 'INR',
      bank: 'HDFC Bank',
      cardLastFour: '5678',
      merchant: 'SWIGGY',
      type: 'DEBIT',
      date: '2026-04-03',
    },
    confidence: 0.95,
  },
  {
    rawSms: 'USD 49.99 spent on your Axis Bank Card at NETFLIX',
    decision: 'INCLUDE',
    excludeReason: null,
    transaction: {
      amount: 49.99,
      currency: 'USD',
      bank: 'Axis Bank',
      cardLastFour: '9876',
      merchant: 'NETFLIX',
      type: 'DEBIT',
      date: '2026-04-13',
    },
    confidence: 0.85,
  },
  {
    rawSms:
      'Refund of Rs 450.00 credited to your HDFC Card xx5678 from BIGBASKET',
    decision: 'INCLUDE',
    excludeReason: null,
    transaction: {
      amount: 450,
      currency: 'INR',
      bank: 'HDFC Bank',
      cardLastFour: '5678',
      merchant: 'BIGBASKET',
      type: 'REFUND',
      date: '2026-04-12',
    },
    confidence: 0.9,
  },
  {
    rawSms: 'Use 458219 as your OTP for HDFC Bank Net Banking login.',
    decision: 'EXCLUDE',
    excludeReason: 'OTP',
    transaction: null,
    confidence: 0.95,
  },
];

/** Lift a ParsedResult into an EnrichedResult with given enrichment. */
function enrich(
  base: ParsedResult,
  id: string,
  extra: Partial<EnrichedResult> = {},
): EnrichedResult {
  return {
    ...base,
    id,
    receivedAt: null,
    threadId: null,
    merchantCanonical: null,
    category: null,
    recurring: false,
    linkedTo: null,
    ...extra,
  };
}

/** A session over the fixture: one Netflix recurring merchant + a refund thread. */
function sessionFixture(): SessionResult {
  const results: EnrichedResult[] = [
    enrich(fixture[0], '0', {
      threadId: 't1',
      merchantCanonical: 'Swiggy',
      category: 'food',
      linkedTo: ['2'],
    }),
    enrich(fixture[1], '1', {
      merchantCanonical: 'Netflix',
      category: 'entertainment',
      recurring: true,
    }),
    enrich(fixture[2], '2', {
      threadId: 't1',
      merchantCanonical: 'Swiggy',
      category: 'food',
      linkedTo: ['0'],
    }),
    enrich(fixture[3], '3'),
  ];
  return {
    results,
    threads: [
      {
        threadId: 't1',
        card4: '5678',
        merchantCanonical: 'Swiggy',
        netAmount: 800,
        events: [results[0], results[2]],
      },
    ],
    merchants: [
      {
        canonical: 'Swiggy',
        category: 'food',
        count: 2,
        totalSpend: 800,
        recurring: false,
      },
      {
        canonical: 'Netflix',
        category: 'entertainment',
        count: 1,
        totalSpend: 49.99,
        recurring: true,
      },
    ],
  };
}

describe('SummaryHeader', () => {
  it('computes INR totals excluding foreign currency (C7)', () => {
    let tree: ReturnType<typeof renderer.create> | undefined;
    act(() => {
      tree = renderer.create(<SummaryHeader results={fixture} />);
    });
    const text = collectText(tree!.toJSON());
    expect(text).toContain('1,250'); // INR debit total (HDFC DEBIT)
    expect(text).not.toContain('49.99'); // USD spend excluded from INR totals
    expect(text).toContain('450'); // INR credit/refund total
    expect(text).toContain('Included');
    expect(text).toContain('OTP: 1'); // top exclusions
  });
});

describe('ResultRow', () => {
  it('renders an included transaction row', () => {
    let tree: ReturnType<typeof renderer.create> | undefined;
    act(() => {
      tree = renderer.create(
        <ResultRow result={fixture[0]} onPress={() => {}} />,
      );
    });
    const text = collectText(tree!.toJSON());
    expect(text).toContain('SWIGGY');
    expect(text).toContain('1,250');
    expect(text).toContain('DEBIT');
  });

  it('renders a dimmed excluded row with reason chip + SMS preview', () => {
    let tree: ReturnType<typeof renderer.create> | undefined;
    act(() => {
      tree = renderer.create(
        <ResultRow result={fixture[3]} onPress={() => {}} />,
      );
    });
    const text = collectText(tree!.toJSON());
    expect(text).toContain('OTP');
    expect(text).toContain('458219'); // raw SMS preview
  });

  it('shows the enrichment line (canonical + category + recurring) on an enriched included row (WS-6)', () => {
    const row = enrich(fixture[1], '1', {
      merchantCanonical: 'Netflix',
      category: 'entertainment',
      recurring: true,
    });
    let tree: ReturnType<typeof renderer.create> | undefined;
    act(() => {
      tree = renderer.create(<ResultRow result={row} onPress={() => {}} />);
    });
    const text = collectText(tree!.toJSON());
    expect(text).toContain('Netflix'); // canonical merchant
    expect(text).toContain('Entertainment'); // category chip (capitalised)
    expect(text).toContain('Recurring'); // recurring badge
  });
});

describe('ParserScreen', () => {
  it('runs the contextual engine on mount and feeds Messages from its results (WS-6)', async () => {
    jest.mocked(parseSmsSession).mockResolvedValue(sessionFixture());
    let tree: ReturnType<typeof renderer.create> | undefined;
    await act(async () => {
      tree = renderer.create(<ParserScreen />);
    });
    expect(parseSmsSession).toHaveBeenCalledTimes(1);
    const text = collectText(tree!.toJSON());
    expect(text).toContain('Credit-Card Transactions'); // summary header rendered
    expect(text).toContain('OTP: 1'); // excluded OTP aggregated => real data flowed
    expect(text).toContain('Netflix'); // enrichment line on the Messages list
  });

  it('renders the Insights segment (threads / merchants / recurring) when switched (WS-6)', async () => {
    jest.mocked(parseSmsSession).mockResolvedValue(sessionFixture());
    let tree: ReturnType<typeof renderer.create> | undefined;
    await act(async () => {
      tree = renderer.create(<ParserScreen />);
    });
    // Switch to the Insights segment via the segmented control.
    const insightsBtn = tree!.root.findAll(
      n =>
        n.props.accessibilityRole === 'button' &&
        n.props.accessibilityState?.selected === false,
    );
    await act(async () => {
      insightsBtn.forEach(b => b.props.onPress?.());
    });
    const text = collectText(tree!.toJSON());
    expect(text).toContain('Threads');
    expect(text).toContain('Merchants');
    expect(text).toContain('Recurring');
    expect(text).toContain('Netflix'); // merchant rollup
    expect(text).toContain('NET'); // thread net amount label
  });
});
