module.exports = {
  preset: 'react-native',
  setupFiles: ['./jest.setup.js'],
  // The native-glass / animation libs ship untranspiled ESM/Flow; allow Babel
  // to transform them (the default RN preset ignores node_modules).
  transformIgnorePatterns: [
    'node_modules/(?!(' +
      '@react-native|react-native|' +
      'react-native-gesture-handler|' +
      'react-native-safe-area-context|@sbaiahmed1/react-native-blur' +
      ')/)',
  ],
};
