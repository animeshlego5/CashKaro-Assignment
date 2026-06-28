/**
 * Native file-pick + read wrapper (WS-5, buildphase-v2.md §6).
 *
 * Isolates the two native dependencies — `react-native-document-picker` (system
 * SAF picker) and `react-native-fs` (read the picked file) — behind a single
 * tiny function so the rest of the app, and the unit tests, never import them.
 * The pure mapping (`parseImport`) is kept in its own native-free module.
 *
 * No new Android permissions are needed: a user-initiated SAF pick grants
 * scoped read access to exactly the chosen file.
 */
import DocumentPicker, {isCancel, types} from 'react-native-document-picker';
import RNFS from 'react-native-fs';

/** The raw bytes of a picked file plus its display name (for format detection). */
export interface PickedFile {
  fileName: string | null;
  content: string;
}

/** Best-effort display name: prefer the picker's name, else derive from the URI. */
function nameFrom(name: string | null, uri: string): string | null {
  if (name && name.trim() !== '') {
    return name;
  }
  // content:// URIs often end with an encoded "…/document/primary:Downloads/foo.txt".
  try {
    const decoded = decodeURIComponent(uri);
    const tail = decoded.split(/[/:]/).pop();
    return tail && tail.includes('.') ? tail : null;
  } catch {
    return null;
  }
}

/**
 * Open the system picker for a .json / .txt file, read it, and return its
 * content. Resolves to `null` when the user cancels (a normal outcome, not an
 * error). Any other failure rejects so the caller can show a banner.
 */
export async function pickSmsFile(): Promise<PickedFile | null> {
  let res;
  try {
    [res] = await DocumentPicker.pick({
      // Accept JSON + plain text; allFiles keeps providers that mis-type files
      // (common on Android) still selectable.
      type: [types.json, types.plainText, types.allFiles],
      // Copy into app cache so RNFS can read it reliably off a stable path
      // rather than a transient content:// stream.
      copyTo: 'cachesDirectory',
    });
  } catch (err) {
    if (isCancel(err)) {
      return null;
    }
    throw err;
  }

  const readPath = res.fileCopyUri ?? res.uri;
  const content = await RNFS.readFile(readPath, 'utf8');
  return {fileName: nameFrom(res.name, res.uri), content};
}
