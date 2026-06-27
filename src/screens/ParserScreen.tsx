import React, {useEffect, useState} from 'react';
import {ActivityIndicator, FlatList, StyleSheet, Text, View} from 'react-native';
import samples from '../data/samples.json';
import {ParsedResult, parseSms} from '../native/SmsParser';
import DetailModal from '../components/DetailModal';
import ResultRow from '../components/ResultRow';
import SummaryHeader from '../components/SummaryHeader';
import {colors, space} from '../theme';

/**
 * The single screen (docs/UI-Requirements.md): calls the native parser on mount,
 * renders the summary header + a row per result, and opens a detail modal on tap.
 * All parsing is in Kotlin (C1); this only calls the bridge and renders.
 */
export default function ParserScreen(): React.JSX.Element {
  const [results, setResults] = useState<ParsedResult[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<ParsedResult | null>(null);

  useEffect(() => {
    // C6: pass whatever samples.json holds (any length) to the native parser.
    parseSms(samples.map(s => s.text))
      .then(setResults)
      .catch(e => setError(String(e?.message ?? e)));
  }, []);

  if (error) {
    return (
      <View style={styles.center}>
        <Text style={styles.error}>Bridge error</Text>
        <Text style={styles.errorDetail}>{error}</Text>
      </View>
    );
  }

  if (results === null) {
    return (
      <View style={styles.center}>
        <ActivityIndicator size="large" color={colors.accent} />
        <Text style={styles.loading}>Parsing {samples.length} SMS…</Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <FlatList
        data={results}
        keyExtractor={(_, index) => String(index)}
        ListHeaderComponent={<SummaryHeader results={results} />}
        renderItem={({item}) => <ResultRow result={item} onPress={() => setSelected(item)} />}
        contentContainerStyle={styles.list}
        showsVerticalScrollIndicator={false}
      />
      <DetailModal result={selected} visible={selected !== null} onClose={() => setSelected(null)} />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {flex: 1, backgroundColor: colors.bg},
  list: {padding: space.md},
  center: {flex: 1, alignItems: 'center', justifyContent: 'center', padding: space.xl, backgroundColor: colors.bg},
  loading: {marginTop: space.md, fontSize: 15, color: colors.subtle},
  error: {fontSize: 17, fontWeight: '700', color: colors.debit},
  errorDetail: {marginTop: space.sm, fontSize: 13, color: colors.subtle, textAlign: 'center'},
});
