module.exports = {
  presets: ['module:@react-native/babel-preset'],
  // No reanimated/plugin: the detail-sheet animation uses RN's built-in Animated
  // (see DetailModal.tsx), so the glass UI builds without reanimated's NDK toolchain.
};
