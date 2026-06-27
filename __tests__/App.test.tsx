/**
 * @format
 *
 * Device-free UI smoke tests: the native bridge is mocked, so these run with no
 * Android module / emulator. They verify the summary's INR totals EXCLUDE
 * foreign currency (C7), that included + excluded rows render, and that the
 * screen calls parseSms on mount and feeds the result into the UI.
 * (On-device visual verification is done separately once a phone is connected.)
 */
import 'react-native';
import React from 'react';
import renderer, {act} from 'react-test-renderer';
import {describe, expect, it, jest} from '@jest/globals';

// Mock the native bridge wrapper so no Android module / emulator is needed.
jest.mock('../src/native/SmsParser', () => ({
  __esModule: true,
  parseSms: jest.fn(),
}));

import {ParsedResult, parseSms} from '../src/native/SmsParser';
import ParserScreen from '../src/screens/ParserScreen';
import ResultRow from '../src/components/ResultRow';
import SummaryHeader from '../src/components/SummaryHeader';

/** Collect rendered text by walking children only (avoids circular element props). */
function collectText(node: unknown): string {
  if (node == null) return '';
  if (typeof node === 'string') return node;
  if (typeof node === 'number') return String(node);
  if (Array.isArray(node)) return node.map(collectText).join(' ');
  return collectText((node as {children?: unknown}).children);
}

const fixture: ParsedResult[] = [
  {
    rawSms: 'INR 1,250.00 spent on HDFC Bank Credit Card xx5678 at SWIGGY',
    decision: 'INCLUDE',
    excludeReason: null,
    transaction: {amount: 1250, currency: 'INR', bank: 'HDFC Bank', cardLastFour: '5678', merchant: 'SWIGGY', type: 'DEBIT', date: '2026-04-03'},
    confidence: 0.95,
  },
  {
    rawSms: 'USD 49.99 spent on your Axis Bank Card at NETFLIX',
    decision: 'INCLUDE',
    excludeReason: null,
    transaction: {amount: 49.99, currency: 'USD', bank: 'Axis Bank', cardLastFour: '9876', merchant: 'NETFLIX', type: 'DEBIT', date: '2026-04-13'},
    confidence: 0.85,
  },
  {
    rawSms: 'Refund of Rs 450.00 credited to your HDFC Card xx5678 from BIGBASKET',
    decision: 'INCLUDE',
    excludeReason: null,
    transaction: {amount: 450, currency: 'INR', bank: 'HDFC Bank', cardLastFour: '5678', merchant: 'BIGBASKET', type: 'REFUND', date: '2026-04-12'},
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
      tree = renderer.create(<ResultRow result={fixture[0]} onPress={() => {}} />);
    });
    const text = collectText(tree!.toJSON());
    expect(text).toContain('SWIGGY');
    expect(text).toContain('1,250');
    expect(text).toContain('DEBIT');
  });

  it('renders a dimmed excluded row with reason chip + SMS preview', () => {
    let tree: ReturnType<typeof renderer.create> | undefined;
    act(() => {
      tree = renderer.create(<ResultRow result={fixture[3]} onPress={() => {}} />);
    });
    const text = collectText(tree!.toJSON());
    expect(text).toContain('OTP');
    expect(text).toContain('458219'); // raw SMS preview
  });
});

describe('ParserScreen', () => {
  it('calls parseSms on mount and feeds the native output into the UI', async () => {
    jest.mocked(parseSms).mockResolvedValue(fixture);
    let tree: ReturnType<typeof renderer.create> | undefined;
    await act(async () => {
      tree = renderer.create(<ParserScreen />);
    });
    expect(parseSms).toHaveBeenCalledTimes(1);
    const text = collectText(tree!.toJSON());
    expect(text).toContain('Credit-Card Transactions'); // summary header rendered
    expect(text).toContain('OTP: 1'); // fixture's excluded OTP aggregated => real data flowed
  });
});
