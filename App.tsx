/**
 * CashKaro Bank SMS Parser — app entry. Renders the single parser screen.
 * All parsing happens in the native Kotlin module; JS only calls the bridge
 * and renders (C1).
 *
 * WS-4: wraps the screen in the providers the Liquid Glass UI needs —
 * GestureHandlerRootView (gestures), SafeAreaProvider (notch/inset-correct
 * floating layers) and ThemeProvider (adaptive light/dark palette + reduce
 * motion). The status bar adapts to the colour scheme.
 */
import React from 'react';
import {StatusBar, StyleSheet, useColorScheme, View} from 'react-native';
import {GestureHandlerRootView} from 'react-native-gesture-handler';
import {SafeAreaProvider} from 'react-native-safe-area-context';
import ParserScreen from './src/screens/ParserScreen';
import {ThemeProvider} from './src/theme/ThemeContext';
import {paletteFor} from './src/theme';

function App(): React.JSX.Element {
  const scheme = useColorScheme();
  const palette = paletteFor(scheme);
  return (
    <GestureHandlerRootView style={styles.root}>
      <SafeAreaProvider>
        <ThemeProvider>
          <View
            style={[styles.root, {backgroundColor: palette.systemBackground}]}>
            <StatusBar
              barStyle={scheme === 'dark' ? 'light-content' : 'dark-content'}
              backgroundColor="transparent"
              translucent
            />
            <ParserScreen />
          </View>
        </ThemeProvider>
      </SafeAreaProvider>
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  root: {flex: 1},
});

export default App;
