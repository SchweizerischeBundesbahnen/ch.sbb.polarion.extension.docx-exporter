import { useCallback, useMemo } from 'react';
import useRemote from './useRemote';

/** The MIME type of a Word document, as the download and the upload filter both name it. */
export const DOCX_MIME_TYPE = 'application/vnd.openxmlformats-officedocument.wordprocessingml.document';

/**
 * An attached template, held as bytes over a plain ArrayBuffer. Spelled out because `Uint8Array` alone
 * also admits a view over a SharedArrayBuffer, which neither `Blob` nor `fetch` accepts as a body.
 */
export type DocxBytes = Uint8Array<ArrayBuffer>;

/** What the server reads out of a reference template, and what the page displays about it. */
export interface TemplateDetails {
  styleCount: number;
  /** Absent when the document carries no core properties, which is legal in OOXML. */
  modifiedDate?: string;
}

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
 * The two template endpoints that are not part of the generic named-settings REST shape
 * `useNamedSettings` covers: reading the details of a DOCX, and downloading the reference document
 * pandoc ships with.
 *
 * `readDetails` doubles as the validator of an upload. It used to run in the browser on JSZip, which
 * meant the administration page carried a zip library to answer two questions and to decide whether a
 * file was a DOCX at all. The server answers both now, so a rejected file is rejected by the runtime
 * that would have had to read it later.
 */
export default function useDocxTemplate() {
  const { sendRequest } = useRemote();

  const readDetails = useCallback(
    async (template: DocxBytes): Promise<TemplateDetails> => {
      const response = await sendRequest({
        method: 'POST',
        url: '/template/details',
        contentType: 'application/octet-stream',
        // A copy, because the fetch body must be a plain ArrayBuffer view the caller does not reuse.
        body: new Uint8Array(template),
      });
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      return (await response.json()) as TemplateDetails;
    },
    [sendRequest],
  );

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

  return useMemo(() => ({ readDetails, downloadBuiltInTemplate }), [readDetails, downloadBuiltInTemplate]);
}
