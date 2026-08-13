/**
 * Where the document being exported lives, read out of the Polarion location hash.
 *
 * This is the TypeScript port of the legacy `ExportContext.js` constructor, which the export panel used
 * to load at runtime from the extension's other webapp. The parsing is not trivial - baselines, the
 * implicit `_default` space, documents nested in a collection - and it has to answer the same question
 * for every export surface, which is why it is one module with tests of its own.
 *
 * Everything a DOCX export addresses is a Live Document, so there is no document type here: the
 * extension converts documents and nothing else, and `ExportParams.java` has no field for one.
 */

export interface DocumentLocation {
  /** The project the document belongs to, or null outside any project scope (the global repository). */
  projectId: string | null;
  /** `<space>/<name>`, or undefined where the hash addresses no document. */
  locationPath?: string;
  baselineRevision?: string;
  revision?: string;
  /** The hash's own query parameters, which an export has to carry: the renderer reads the document as the page does. */
  urlQueryParameters?: Record<string, string>;
}

/** {@link DocumentLocation} plus what the endpoints want spelled out separately. */
export interface DocumentIdentity extends DocumentLocation {
  /** `project/<id>/` or the empty string - what the settings endpoints call a scope. */
  scope: string;
  spaceId?: string;
  documentName?: string;
}

/** The path and the query of a location hash, both unescaped as the legacy parser unescaped them. */
function splitHash(hash: string): { path: string; search?: string } {
  const withoutPrefix = decodeURI(hash.substring(2));
  const separator = withoutPrefix.indexOf('?');
  if (separator < 0) {
    return { path: withoutPrefix };
  }
  return { path: withoutPrefix.slice(0, separator), search: withoutPrefix.slice(separator + 1) };
}

const scopeOf = (path: string): string => {
  const match = /project\/([^/]+)\//.exec(path);
  return match ? `project/${match[1]}/` : '';
};

const projectIdOf = (scope: string): string | null => /project\/(.*)\//.exec(scope)?.[1] ?? null;

const baselineRevisionOf = (path: string): string | undefined => /baseline\/([^/]+)\//.exec(path)?.[1];

/**
 * A location path Polarion left the space out of gets `_default/` back, which is where such a document
 * lives. A test run path is returned untouched: the legacy parser recognized one by that prefix, and it
 * is kept so a hash that names one is not silently read as a document in a space called `testruns`.
 */
function addDefaultSpaceIfRequired(extractedPath: string | undefined): string {
  if (!extractedPath) {
    return '';
  }
  if (extractedPath.startsWith('testrun') || extractedPath.includes('/')) {
    return extractedPath;
  }
  return `_default/${extractedPath}`;
}

/** `<space>/<name>` of the document the hash addresses, or undefined where the hash addresses none. */
function locationPathOf(path: string, scope: string): string | undefined {
  if (scope) {
    // Greedy on purpose: a document inside a collection has two path segments before its own `/wiki/`.
    const match = /project\/(.+)\/(wiki\/([^?#]+)|testruns|testrun)/.exec(path);
    return match ? addDefaultSpaceIfRequired(match[3] || match[2]) : undefined;
  }
  const globalMatch = /wiki\/([^/?#]+)/.exec(path);
  return globalMatch ? addDefaultSpaceIfRequired(globalMatch[1]) : undefined;
}

const queryParametersOf = (search: string | undefined): Record<string, string> | undefined =>
  search === undefined ? undefined : Object.fromEntries(new URLSearchParams(search));

/** Where the document lives, from a Polarion location hash such as `#/project/elibrary/wiki/Specs/Doc`. */
export function parseDocumentLocation(hash: string): DocumentLocation {
  const { path, search } = splitHash(hash);
  const scope = scopeOf(path);
  const urlQueryParameters = queryParametersOf(search);

  return {
    projectId: projectIdOf(scope),
    locationPath: locationPathOf(path, scope),
    baselineRevision: baselineRevisionOf(path),
    urlQueryParameters,
    revision: urlQueryParameters?.revision,
  };
}

const pathParts = (locationPath: string | undefined): string[] | undefined =>
  locationPath?.includes('/') ? locationPath.split('/') : undefined;

export const spaceIdOf = (location: DocumentLocation): string | undefined => pathParts(location.locationPath)?.[0];

export const documentNameOf = (location: DocumentLocation): string | undefined => pathParts(location.locationPath)?.[1];

/** The scope the settings endpoints take: `project/<id>/`, or the empty string for the global scope. */
export const scopeFor = (location: DocumentLocation): string =>
  location.projectId ? `project/${location.projectId}/` : '';

/** A location with everything the endpoints ask for spelled out. */
export function toDocumentIdentity(location: DocumentLocation): DocumentIdentity {
  return {
    ...location,
    scope: scopeFor(location),
    spaceId: spaceIdOf(location),
    documentName: documentNameOf(location),
  };
}

/** Where the current page is, for a surface that has no location of its own to point at. */
export const currentDocumentLocation = (): DocumentLocation => parseDocumentLocation(window.location.hash);
