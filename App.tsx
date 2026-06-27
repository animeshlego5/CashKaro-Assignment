/**
 * CashKaro Bank SMS Parser — Phase 0 bring-up screen.
 *
 * Proves the JS -> Kotlin bridge round-trip: loads samples.json, passes the SMS
 * texts to the native Kotlin parser, and renders how many results came back.
 * The real UI (summary header, result list, detail modal) lands in Phase 4.
 */
import React, {useEffect, useState} from 'react';
import {
  SafeAreaView,
  ScrollView,
  StatusBar,
  StyleSheet,
  Text,
  View,
} from 'react-native';

import samples from './src/data/samples.json';
import {parseSms, ParsedResult} from './src/native/SmsParser';

function App(): React.JSX.Element {
  const [results, setResults] = useState<ParsedResult[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    // C6: accept any-length sample array; never assume exactly 25.
    const texts = samples.map(s => s.text);
    parseSms(texts)
      .then(setResults)
      .catch(e => setError(String(e?.message ?? e)));
  }, []);

  const included =
    results?.filter(r => r.decision === 'INCLUDE').length ?? 0;
  const excluded =
    results?.filter(r => r.decision === 'EXCLUDE').length ?? 0;

  return (
    <SafeAreaView style={styles.container}>
      <StatusBar barStyle="dark-content" backgroundColor="#ffffff" />
      <ScrollView contentContainerStyle={styles.content}>
        <Text style={styles.title}>CashKaro SMS Parser</Text>
        {error ? (
          <Text style={styles.error}>Bridge error: {error}</Text>
        ) : results === null ? (
          <Text style={styles.muted}>Parsing {samples.length} samples…</Text>
        ) : (
          <View>
            <Text style={styles.big}>{results.length} results received</Text>
            <Text style={styles.muted}>
              Included: {included} · Excluded: {excluded}
            </Text>
            <Text style={styles.note}>
              Phase 0 stub bridge — every result is EXCLUDE / LOW_CONFIDENCE for
              now. Real parsing arrives in later phases.
            </Text>
          </View>
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {flex: 1, backgroundColor: '#ffffff'},
  content: {padding: 24},
  title: {fontSize: 22, fontWeight: '700', marginBottom: 16, color: '#111111'},
  big: {fontSize: 28, fontWeight: '800', color: '#00aa77'},
  muted: {fontSize: 16, color: '#555555', marginTop: 6},
  note: {fontSize: 13, color: '#888888', marginTop: 16, lineHeight: 18},
  error: {fontSize: 15, color: '#cc0000'},
});

export default App;
