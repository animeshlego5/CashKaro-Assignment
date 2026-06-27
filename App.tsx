/**
 * CashKaro Bank SMS Parser — app entry. Renders the single parser screen.
 * All parsing happens in the native Kotlin module; JS only calls the bridge
 * and renders (C1).
 */
import React from 'react';
import {SafeAreaView, StatusBar, StyleSheet} from 'react-native';
import ParserScreen from './src/screens/ParserScreen';
import {colors} from './src/theme';

function App(): React.JSX.Element {
  return (
    <SafeAreaView style={styles.root}>
      <StatusBar barStyle="dark-content" backgroundColor={colors.bg} />
      <ParserScreen />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: {flex: 1, backgroundColor: colors.bg},
});

export default App;
