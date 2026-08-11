import { useCallback, useMemo } from 'react';
import useRemote from './useRemote';

/** The MIME type of a Word document, as the download and the upload filter both name it. */
export const DOCX_MIME_TYPE = 'application/vnd.openxmlformats-officedocument.wordprocessingml.document';

/**
 * An attached template, held as bytes over a plain ArrayBuffer. Spelled out because `Uint8Array` alone
 * also admits a view over a SharedArrayBuffer, which neither `Blob` nor `fetch` accepts as a body.
 */
export type DocxBytes = Uint8Array<ArrayBuffer>;

/** Content of one named `templates` configuration: the reference DOCX, base64, or none. */
export interface TemplatesSettings {
  template?: string | null;
}

// atob/btoa work on binary strings, and a template runs to hundreds of kilobytes, so the conversion is
// chunked: String.fromCharCode applied to a whole document's worth of arguments overflows the stack.
const CHUNK = 8192;

export function base64ToBytes(base64: string): DocxBytes {
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}

export function bytesToBase64(bytes: DocxBytes): string {
  let binary = '';
  for (let i = 0; i < bytes.length; i += CHUNK) {
    binary += String.fromCharCode(...bytes.subarray(i, i + CHUNK));
  }
  return btoa(binary);
}

/**
 * The one template endpoint that is not part of the generic named-settings REST shape
 * `useNamedSettings` covers: downloading the reference document pandoc ships with.
 *
 * Nothing here inspects an attached document. The page used to read a style count and a modification
 * date out of it - on JSZip in the browser first, then over a REST endpoint - and displays its size
 * instead. Whether a file may be stored at all is decided where it is stored: `TemplatesSettings`
 * rejects anything past `templateMaxSizeMB` or not opening like a zip container.
 */
export default function useDocxTemplate() {
  const { sendRequest } = useRemote();

  /**
   * The reference document built into pandoc, which the hint on the page offers as the starting point
   * for a custom one. Fetched rather than linked: under `vite dev` a plain anchor would miss the bearer
   * token `useRemote` adds.
   */
  const downloadBuiltInTemplate = useCallback(async (): Promise<Blob> => {
    const response = await sendRequest({ method: 'GET', url: '/template' });
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`);
    }
    return response.blob();
  }, [sendRequest]);

  return useMemo(() => ({ downloadBuiltInTemplate }), [downloadBuiltInTemplate]);
}
