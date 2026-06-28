/**
 * Jest setup — mock the native-only libraries added in WS-4/WS-5 so the
 * device-free UI smoke tests run with no Android module / emulator. These
 * libraries ship codegen native components (real blur, gesture handler, SAF
 * picker, fs) that have no JS implementation under the `react-native` jest preset.
 */
/* eslint-env jest */
/* global jest */

// @sbaiahmed1/react-native-blur — render plain Views in place of native blur.
jest.mock('@sbaiahmed1/react-native-blur', () => {
  const React = require('react');
  const {View} = require('react-native');
  const passthrough = props => React.createElement(View, props, props.children);
  return {
    __esModule: true,
    BlurView: passthrough,
    LiquidGlassView: passthrough,
    LiquidGlassContainer: passthrough,
    ProgressiveBlurView: passthrough,
    VibrancyView: passthrough,
    BlurSwitch: passthrough,
  };
});

// react-native-safe-area-context — provide zero insets + passthrough provider.
jest.mock('react-native-safe-area-context', () => {
  const React = require('react');
  const {View} = require('react-native');
  const inset = {top: 0, right: 0, bottom: 0, left: 0};
  return {
    __esModule: true,
    SafeAreaProvider: ({children}) =>
      React.createElement(React.Fragment, null, children),
    SafeAreaView: props => React.createElement(View, props, props.children),
    useSafeAreaInsets: () => inset,
    useSafeAreaFrame: () => ({x: 0, y: 0, width: 0, height: 0}),
    initialWindowMetrics: {
      insets: inset,
      frame: {x: 0, y: 0, width: 0, height: 0},
    },
  };
});

// react-native-gesture-handler — official jest mock.
try {
  require('react-native-gesture-handler/jestSetup');
} catch (e) {
  // optional
}

// react-native-document-picker (WS-5) — native SAF picker; no JS impl in jest.
jest.mock('react-native-document-picker', () => ({
  __esModule: true,
  default: {pick: jest.fn(), types: {}},
  pick: jest.fn(),
  isCancel: () => false,
  types: {json: 'application/json', plainText: 'text/plain', allFiles: '*/*'},
}));

// react-native-fs (WS-5) — native filesystem; no JS impl in jest.
jest.mock('react-native-fs', () => ({
  __esModule: true,
  default: {readFile: jest.fn()},
  readFile: jest.fn(),
}));
