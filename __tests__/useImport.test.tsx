/**
 * @format
 *
 * Tests the import-flow controller (WS-5, integrated by WS-6): pick -> parse ->
 * hand records to the screen. The native picker (`pickFile`) is mocked, so this
 * runs with no device. It proves the handler maps a picked .json / .txt file to
 * `SmsRecord[]` and reports them to the caller (the screen, which re-runs the
 * CONTEXTUAL engine over the combined batch — see App.test.tsx), surfaces a
 * friendly banner on a malformed file instead of crashing, and propagates a
 * thrown error from the screen's re-parse as a banner.
 */
import 'react-native';
import React from 'react';
import renderer, {act} from 'react-test-renderer';
import {beforeEach, describe, expect, it, jest} from '@jest/globals';

jest.mock('../src/import/pickFile', () => ({
  __esModule: true,
  pickSmsFile: jest.fn(),
}));
jest.mock('../src/native/SmsParser', () => ({
  __esModule: true,
  parseSms: jest.fn(),
  parseSmsSession: jest.fn(),
}));

import {pickSmsFile} from '../src/import/pickFile';
import {SmsRecord} from '../src/native/SmsParser';
import {useImport, UseImport} from '../src/import/useImport';

const mockPick = jest.mocked(pickSmsFile);

/** Render the hook and capture its latest return value for assertions. */
function renderHook(onImported: (r: SmsRecord[]) => void | Promise<void>): {
  current: UseImport;
} {
  const ref: {current: UseImport} = {current: undefined as never};
  function Harness(): React.JSX.Element | null {
    ref.current = useImport(onImported);
    return null;
  }
  act(() => {
    renderer.create(<Harness />);
  });
  return ref;
}

beforeEach(() => {
  mockPick.mockReset();
});

describe('useImport', () => {
  it('maps a picked .json file to records and hands them to the screen', async () => {
    mockPick.mockResolvedValue({
      fileName: 'more.json',
      content: JSON.stringify([{text: 'INR 100 at SWIGGY'}]),
    });
    const onImported = jest.fn(() => Promise.resolve());
    const hook = renderHook(onImported);

    await act(async () => {
      hook.current.importFile();
    });

    expect(onImported).toHaveBeenCalledTimes(1);
    expect(onImported).toHaveBeenCalledWith([{text: 'INR 100 at SWIGGY'}]);
    expect(hook.current.banner).toBeNull();
  });

  it('maps a picked .txt file (one SMS per line) to records', async () => {
    mockPick.mockResolvedValue({
      fileName: 'inbox.txt',
      content: 'first\n\nsecond\n',
    });
    const onImported = jest.fn(() => Promise.resolve());
    const hook = renderHook(onImported);

    await act(async () => {
      hook.current.importFile();
    });

    expect(onImported).toHaveBeenCalledWith([
      {text: 'first'},
      {text: 'second'},
    ]);
  });

  it('does nothing when the user cancels the picker', async () => {
    mockPick.mockResolvedValue(null);
    const onImported = jest.fn(() => Promise.resolve());
    const hook = renderHook(onImported);

    await act(async () => {
      hook.current.importFile();
    });

    expect(onImported).not.toHaveBeenCalled();
    expect(hook.current.banner).toBeNull();
  });

  it('shows a non-blocking error banner for a malformed file (no crash)', async () => {
    mockPick.mockResolvedValue({fileName: 'broken.json', content: '{bad'});
    const onImported = jest.fn(() => Promise.resolve());
    const hook = renderHook(onImported);

    await act(async () => {
      hook.current.importFile();
    });

    expect(onImported).not.toHaveBeenCalled();
    expect(hook.current.banner).not.toBeNull();
    expect(hook.current.banner?.variant).toBe('error');
  });

  it('shows an error banner when the screen re-parse (native session) throws', async () => {
    mockPick.mockResolvedValue({
      fileName: 'ok.txt',
      content: 'a real sms line',
    });
    const onImported = jest.fn(() =>
      Promise.reject(new Error('bridge exploded')),
    );
    const hook = renderHook(onImported);

    await act(async () => {
      hook.current.importFile();
    });

    expect(hook.current.banner?.variant).toBe('error');
    expect(hook.current.banner?.message).toMatch(/bridge exploded/);
  });
});
