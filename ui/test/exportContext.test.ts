import { describe, expect, it } from 'vitest';
import { parseDocumentLocation, toDocumentIdentity } from '../src/services/exportContext';

// Reading the editor URL, which the panel used to get from the product's ExportContext.js. The parsing
// looks small and is not: the space Polarion leaves out, baselines, a document inside a collection, and
// the query parameters an export has to carry are all decided here.

const identity = (hash: string) => toDocumentIdentity(parseDocumentLocation(hash));

describe('where the document is', () => {
  it('reads a document in a named space', () => {
    const document = identity('#/project/elibrary/wiki/Specification/Product Specification');

    expect(document.projectId).toBe('elibrary');
    expect(document.scope).toBe('project/elibrary/');
    expect(document.locationPath).toBe('Specification/Product Specification');
    expect(document.spaceId).toBe('Specification');
    expect(document.documentName).toBe('Product Specification');
  });

  it('puts back the `_default` space Polarion leaves out of the URL', () => {
    const document = identity('#/project/elibrary/wiki/Home Page');

    expect(document.locationPath).toBe('_default/Home Page');
    expect(document.spaceId).toBe('_default');
  });

  it('unescapes the path, the way the legacy parser did', () => {
    expect(identity('#/project/elibrary/wiki/Specs/Cross%20Link%20Issue').documentName).toBe('Cross Link Issue');
  });

  it('reads a document of the global repository, which has no project', () => {
    const document = identity('#/wiki/Templates');

    expect(document.projectId).toBeNull();
    expect(document.scope).toBe('');
    expect(document.locationPath).toBe('_default/Templates');
  });

  it('reads a document inside a collection, whose path has a segment of its own before the wiki part', () => {
    const document = identity('#/project/elibrary/collection/42/wiki/Specs/Doc');

    expect(document.projectId).toBe('elibrary');
    expect(document.locationPath).toBe('Specs/Doc');
  });

  it('reads the baseline revision out of a baseline URL', () => {
    expect(identity('#/project/elibrary/baseline/1234/wiki/Specs/Doc').baselineRevision).toBe('1234');
  });

  it('carries the query parameters, and takes the revision out of them', () => {
    const document = identity('#/project/elibrary/wiki/Specs/Doc?revision=99&query=type:requirement');

    expect(document.revision).toBe('99');
    expect(document.urlQueryParameters).toEqual({ revision: '99', query: 'type:requirement' });
  });

  it('addresses no document where the hash addresses none', () => {
    const document = identity('#/project/elibrary/workitems');

    expect(document.projectId).toBe('elibrary');
    expect(document.locationPath).toBeUndefined();
    expect(document.spaceId).toBeUndefined();
    expect(document.documentName).toBeUndefined();
  });

  it('leaves a test run path alone rather than reading it as a space', () => {
    // A DOCX export addresses only documents, so this is not a case the panel serves - but reading
    // `testruns` as a space would silently produce `_default/testruns`, which is worse than nothing.
    expect(parseDocumentLocation('#/project/elibrary/testruns').locationPath).toBe('testruns');
  });
});
