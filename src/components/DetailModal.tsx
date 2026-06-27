import React from 'react';
import {Modal, ScrollView, StyleSheet, Text, TouchableOpacity, View} from 'react-native';
import {ParsedResult} from '../native/SmsParser';
import {colors, formatAmount, space} from '../theme';

/**
 * Detail modal (docs/UI-Requirements.md §3): raw SMS, decision, exclude reason
 * (if any), parsed transaction fields (if any) and confidence.
 */
export default function DetailModal({
  result,
  visible,
  onClose,
}: {
  result: ParsedResult | null;
  visible: boolean;
  onClose: () => void;
}): React.JSX.Element {
  return (
    <Modal visible={visible} animationType="slide" transparent onRequestClose={onClose}>
      <View style={styles.backdrop}>
        <View style={styles.sheet}>
          <View style={styles.header}>
            <Text style={styles.title}>Parsed Result</Text>
            <TouchableOpacity onPress={onClose} hitSlop={{top: 8, bottom: 8, left: 8, right: 8}}>
              <Text style={styles.close}>Close</Text>
            </TouchableOpacity>
          </View>

          {result ? (
            <ScrollView>
              <Field label="Decision" value={result.decision} />
              {result.excludeReason ? <Field label="Exclude reason" value={result.excludeReason} /> : null}
              <Field label="Confidence" value={`${Math.round(result.confidence * 100)}%`} />

              {result.transaction ? (
                <>
                  <Text style={styles.section}>Transaction</Text>
                  <Field
                    label="Amount"
                    value={formatAmount(result.transaction.amount, result.transaction.currency)}
                  />
                  <Field label="Currency" value={result.transaction.currency} />
                  <Field label="Type" value={result.transaction.type} />
                  <Field label="Bank (issuer)" value={result.transaction.bank ?? '—'} />
                  <Field label="Card last 4" value={result.transaction.cardLastFour ?? '—'} />
                  <Field label="Merchant" value={result.transaction.merchant ?? '—'} />
                  <Field label="Date" value={result.transaction.date ?? '—'} />
                </>
              ) : null}

              <Text style={styles.section}>Raw SMS</Text>
              <Text style={styles.raw}>{result.rawSms}</Text>
            </ScrollView>
          ) : null}
        </View>
      </View>
    </Modal>
  );
}

function Field({label, value}: {label: string; value: string}): React.JSX.Element {
  return (
    <View style={styles.field}>
      <Text style={styles.fieldLabel}>{label}</Text>
      <Text style={styles.fieldValue}>{value}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  backdrop: {flex: 1, backgroundColor: '#00000066', justifyContent: 'flex-end'},
  sheet: {
    backgroundColor: colors.card,
    borderTopLeftRadius: 16,
    borderTopRightRadius: 16,
    padding: space.lg,
    maxHeight: '85%',
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: space.md,
  },
  title: {fontSize: 18, fontWeight: '800', color: colors.text},
  close: {fontSize: 15, fontWeight: '700', color: colors.accent},
  section: {
    fontSize: 12,
    fontWeight: '700',
    color: colors.subtle,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
    marginTop: space.md,
    marginBottom: space.xs,
  },
  field: {flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 6, borderBottomWidth: 1, borderBottomColor: colors.border},
  fieldLabel: {fontSize: 14, color: colors.subtle},
  fieldValue: {fontSize: 14, fontWeight: '600', color: colors.text, flexShrink: 1, textAlign: 'right', marginLeft: space.md},
  raw: {fontSize: 13, color: colors.text, lineHeight: 19, marginTop: space.xs},
});
