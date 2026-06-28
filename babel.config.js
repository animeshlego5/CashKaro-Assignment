module.exports = {
  presets: ['module:@react-native/babel-preset'],
  // react-native-reanimated/plugin MUST be listed last (WS-4). It rewrites
  // worklets used by the spring transitions / morphing toolbar.
  plugins: ['react-native-reanimated/plugin'],
};
