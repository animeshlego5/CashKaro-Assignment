/**
 * @format
 *
 * Unit tests for the pure file-import mapping (WS-5 / WS-8). No native module is
 * involved — `parseImport` is dependency-free, so these run without the bridge,
 * the document picker or the filesystem. They cover the two accepted formats
 * (.json / .txt), the bare-string-array shape, receivedAt/sender passthrough,
 * conservative handling of malformed input, and the large-file cap.
 */
import {describe, expect, it} from '@jest/globals';
import {MAX_RECORDS, parseImport} from '../src/import/parseImport';

describe('parseImport — JSON', () => {
  it('maps the samples.json shape [{text}] to records', () => {
    const json = JSON.stringify([
      {id: 1, text: 'INR 100 spent on HDFC Credit Card xx1234 at SWIGGY'},
      {id: 2, text: 'INR 200 spent on HDFC Credit Card xx1234 at AMAZON'},
    ]);
    const out = parseImport(json, 'more.json');
    expect(out.error).toBeNull();
    expect(out.records).toHaveLength(2);
    expect(out.records[0]).toEqual({text: expect.stringContaining('SWIGGY')});
  });

  it('accepts a bare string array', () => {
    const json = JSON.stringify(['first sms', 'second sms']);
    const out = parseImport(json, 'bare.json');
    expect(out.error).toBeNull();
    expect(out.records).toEqual([{text: 'first sms'}, {text: 'second sms'}]);
  });

  it('honours receivedAt and sender when present', () => {
    const json = JSON.stringify([
      {text: 'with meta', receivedAt: 1700000000000, sender: 'HDFCBK'},
    ]);
    const out = parseImport(json, 'meta.json');
    expect(out.records[0]).toEqual({
      text: 'with meta',
      receivedAt: 1700000000000,
      sender: 'HDFCBK',
    });
  });

  it('tolerates a stringified receivedAt', () => {
    const json = JSON.stringify([{text: 'x', receivedAt: '1700000000000'}]);
    const out = parseImport(json, 'meta.json');
    expect(out.records[0].receivedAt).toBe(1700000000000);
  });

  it('skips entries with no usable text (conservative)', () => {
    const json = JSON.stringify([
      {text: 'kept'},
      {text: '   '},
      {notText: 'dropped'},
      42,
      null,
      'also kept',
    ]);
    const out = parseImport(json, 'mixed.json');
    expect(out.records).toEqual([{text: 'kept'}, {text: 'also kept'}]);
  });

  it('returns an error (not a throw) for malformed JSON', () => {
    const out = parseImport('{not valid json', 'broken.json');
    expect(out.records).toHaveLength(0);
    expect(out.error).toMatch(/valid JSON/i);
  });

  it('returns an error for non-array JSON', () => {
    const out = parseImport('{"text":"single object"}', 'obj.json');
    expect(out.records).toHaveLength(0);
    expect(out.error).toMatch(/array/i);
  });
});

describe('parseImport — TXT', () => {
  it('maps one SMS per line and skips blank lines', () => {
    const txt = 'line one\n\n  line two  \r\nline three\n   \n';
    const out = parseImport(txt, 'inbox.txt');
    expect(out.error).toBeNull();
    expect(out.records).toEqual([
      {text: 'line one'},
      {text: 'line two'},
      {text: 'line three'},
    ]);
  });
});

describe('parseImport — detection & edge cases', () => {
  it('sniffs JSON content when the extension is missing', () => {
    const out = parseImport('["sniffed"]');
    expect(out.records).toEqual([{text: 'sniffed'}]);
  });

  it('treats unknown-extension non-JSON content as line text', () => {
    const out = parseImport('a\nb', 'notes.dat');
    expect(out.records).toEqual([{text: 'a'}, {text: 'b'}]);
  });

  it('reports an empty file as a friendly notice, not an error', () => {
    const out = parseImport('   \n  ', 'empty.txt');
    expect(out.records).toHaveLength(0);
    expect(out.error).toBeNull();
    expect(out.notice).toMatch(/empty/i);
  });

  it('reports a notice when a JSON array has no usable messages', () => {
    const out = parseImport('[{"foo":1}]', 'none.json');
    expect(out.error).toBeNull();
    expect(out.notice).toMatch(/no messages/i);
  });
});

describe('parseImport — large-file cap', () => {
  it('caps oversized imports with a VISIBLE notice (no silent truncation)', () => {
    const lines = Array.from({length: MAX_RECORDS + 25}, (_, i) => `sms ${i}`);
    const out = parseImport(lines.join('\n'), 'big.txt');
    expect(out.error).toBeNull();
    expect(out.records).toHaveLength(MAX_RECORDS);
    expect(out.notice).toMatch(
      new RegExp(String(MAX_RECORDS.toLocaleString())),
    );
    expect(out.notice).toMatch(/imported the first/i);
  });

  it('does not flag a notice when exactly at the cap', () => {
    const lines = Array.from({length: MAX_RECORDS}, (_, i) => `sms ${i}`);
    const out = parseImport(lines.join('\n'), 'atcap.txt');
    expect(out.records).toHaveLength(MAX_RECORDS);
    expect(out.notice).toBeNull();
  });
});
