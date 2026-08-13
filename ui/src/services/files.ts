/**
 * Saves a blob under the given file name, the way a download link would.
 *
 * The files this app offers arrive as response bodies rather than navigable URLs, because the endpoints
 * behind them need the session headers `useRemote` adds. So the page has to hand the result to the
 * browser itself.
 */
export function saveBlob(blob: Blob, fileName: string): void {
  const objectUrl = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = objectUrl;
  anchor.download = fileName;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(objectUrl);
}
