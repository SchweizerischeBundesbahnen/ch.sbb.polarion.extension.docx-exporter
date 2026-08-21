/**
 * The conversion protocol: how an export dialog turns a set of export parameters into a downloaded file.
 *
 * The TypeScript port of the legacy `ExportContext.js`'s half that talked to the server - submit a
 * conversion job, poll it, download the result. The requests, the headers read off them and the message
 * built from those headers are unchanged.
 *
 * The legacy code drove `XMLHttpRequest` through callbacks; this is `fetch` through promises, which is
 * what lets a surface await an export instead of threading success and error callbacks.
 */
import type { SendRequest } from '@sbb-polarion/react-sbb-polarion';

/** The two request flavors a conversion needs: the REST base, and a URL the server handed out. */
export interface Remote {
  sendRequest: SendRequest;
  sendAbsoluteRequest: SendRequest;
}

export interface ConversionResult {
  blob: Blob;
  /** The name the server suggests, from the `Export-Filename` header. */
  fileName: string | null;
  /** What the user should know about the result although it was produced - see {@link warningOf}. */
  warning: string | null;
}

/** How long the job is left alone between polls, as `ExportContext.PULL_INTERVAL`. */
export const POLL_INTERVAL = 1000;

const CONVERT_JOBS_URL = '/convert/jobs';

const delay = (milliseconds: number): Promise<void> => new Promise((resolve) => setTimeout(resolve, milliseconds));

/** The message an error response carries, whatever shape it came in, or the empty string. */
export async function errorMessageOf(response: Response | Blob): Promise<string> {
  try {
    const text = await response.text();
    const error = text ? (JSON.parse(text) as { message?: string; errorMessage?: string }) : null;
    return error?.message ?? error?.errorMessage ?? '';
  } catch {
    return '';
  }
}

const failed = async (response: Response): Promise<Error> => new Error(await errorMessageOf(response));

/**
 * What a finished conversion warns about, from the headers of its result.
 *
 * One thing can be wrong with a DOCX that was still produced: work item images that could not be read,
 * for which the renderer substitutes a placeholder. There is no compliance check to report on - that is
 * a PDF variant concern and this extension has no variants.
 */
export function warningOf(headers: Headers): string | null {
  const missingAttachments = Number.parseInt(headers.get('Missing-WorkItem-Attachments-Count') ?? '', 10);
  if (!(missingAttachments > 0)) {
    return null;
  }
  const workItems = headers.get('WorkItem-IDs-With-Missing-Attachment') ?? '';
  return (
    `${missingAttachments} image(s) in WI(s) ${workItems} were not exported. ` +
    "They were replaced with an image containing 'This image is not accessible'."
  );
}

/**
 * Converts one document to DOCX: submits the job and polls it until the file is ready.
 *
 * The poll goes to the absolute `Location` the submission answered with, used verbatim - which is why
 * it takes a {@link Remote} rather than a bare `sendRequest`. A job still running answers 202; anything
 * else that is not OK rejects with the message the server gave, empty when it gave none. The caller
 * decides what to say around it.
 */
export async function convertDocx(
  remote: Remote,
  requestBody: string,
  pollInterval: number = POLL_INTERVAL,
): Promise<ConversionResult> {
  const submitted = await remote.sendRequest({
    method: 'POST',
    url: CONVERT_JOBS_URL,
    contentType: 'application/json',
    body: requestBody,
  });
  if (!submitted.ok) {
    throw await failed(submitted);
  }

  const job = submitted.headers.get('Location');
  if (!job) {
    throw new Error('The conversion job was accepted without a location to poll.');
  }

  for (;;) {
    await delay(pollInterval);
    const polled = await remote.sendAbsoluteRequest({ method: 'GET', url: job });
    if (polled.status === 202) {
      continue;
    }
    if (!polled.ok) {
      throw await failed(polled);
    }
    return {
      blob: await polled.blob(),
      fileName: polled.headers.get('Export-Filename'),
      warning: warningOf(polled.headers),
    };
  }
}

/**
 * Starts a download of the given blob.
 *
 * The link is created in the top window when this runs in an iframe, which is where the panel runs: the
 * document editor is framed, and a download triggered from inside the frame is what browsers block.
 */
export function downloadBlob(blob: Blob, fileName: string): void {
  const objectUrl = (window.URL ?? window.webkitURL).createObjectURL(blob);
  const targetWindow = window.self !== window.top ? (window.top ?? window) : window;

  const link = targetWindow.document.createElement('a');
  link.href = objectUrl;
  link.download = fileName;
  link.target = '_blank';
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(objectUrl), 100);
}
