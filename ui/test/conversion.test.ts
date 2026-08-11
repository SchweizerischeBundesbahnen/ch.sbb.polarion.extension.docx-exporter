import { afterEach, describe, expect, it, vi } from 'vitest';
import { convertDocx, errorMessageOf, warningOf } from '../src/services/conversion';
import type { Remote } from '../src/services/conversion';
import { installFetchMock, jsonResponse } from './mockFetch';

// The conversion protocol: submit a job, poll it, read the result's headers. The legacy ExportContext.js
// drove this with XMLHttpRequest and callbacks and had no test of its own.

const REST_BASE = '/polarion/docx-exporter/rest/internal';
const JOB_URL = `${REST_BASE}/convert/jobs/job-1`;

/** A Remote shaped exactly as useRemote's: one sender prefixes the REST base, one does not. */
const remote: Remote = {
  sendRequest: ({ method, url, body, contentType }) =>
    fetch(`${REST_BASE}${url}`, {
      method,
      body,
      headers: contentType ? { 'Content-Type': contentType } : undefined,
    }),
  sendAbsoluteRequest: ({ method, url }) => fetch(url, { method }),
};

const docx = (headers: Record<string, string> = {}) =>
  new Response(new Blob(['PK'], { type: 'application/octet-stream' }), { status: 200, headers });

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('warningOf', () => {
  it('says nothing when no attachment was missing', () => {
    expect(warningOf(new Headers())).toBeNull();
  });

  it('reports the work items whose images could not be exported', () => {
    const warning = warningOf(
      new Headers({
        'Missing-WorkItem-Attachments-Count': '2',
        'WorkItem-IDs-With-Missing-Attachment': 'EL-1, EL-2',
      }),
    );

    expect(warning).toContain('2 image(s) in WI(s) EL-1, EL-2 were not exported');
    expect(warning).toContain('This image is not accessible');
  });

  it('ignores a zero or unparseable attachment count', () => {
    expect(warningOf(new Headers({ 'Missing-WorkItem-Attachments-Count': '0' }))).toBeNull();
    expect(warningOf(new Headers({ 'Missing-WorkItem-Attachments-Count': 'x' }))).toBeNull();
  });
});

describe('errorMessageOf', () => {
  it('prefers message, falls back to errorMessage, and tolerates anything else', async () => {
    await expect(errorMessageOf(jsonResponse({ message: 'boom' }, 500))).resolves.toBe('boom');
    await expect(errorMessageOf(jsonResponse({ errorMessage: 'bang' }, 500))).resolves.toBe('bang');
    await expect(errorMessageOf(new Response('not json', { status: 500 }))).resolves.toBe('');
    await expect(errorMessageOf(new Response('', { status: 500 }))).resolves.toBe('');
  });
});

describe('converting a document', () => {
  it('submits the job and downloads the result the Location header points at', async () => {
    const fetchMock = installFetchMock([
      {
        method: 'POST',
        match: /\/convert\/jobs$/,
        respond: () => new Response(null, { status: 202, headers: { Location: JOB_URL } }),
      },
      { method: 'GET', match: /\/convert\/jobs\/job-1/, respond: () => docx({ 'Export-Filename': 'Doc.docx' }) },
    ]);

    const result = await convertDocx(remote, '{"projectId":"elibrary"}', 0);

    expect(result.fileName).toBe('Doc.docx');
    expect(result.warning).toBeNull();
    expect(await result.blob.text()).toBe('PK');
    // The job URL is polled as the server handed it out, not rebuilt against the REST base
    expect(fetchMock.mock.calls.map(([url]) => String(url))).toEqual([`${REST_BASE}/convert/jobs`, JOB_URL]);
  });

  it('keeps polling while the job answers 202', async () => {
    let polls = 0;
    installFetchMock([
      {
        method: 'POST',
        match: /\/convert\/jobs$/,
        respond: () => new Response(null, { status: 202, headers: { Location: JOB_URL } }),
      },
      {
        method: 'GET',
        match: /\/convert\/jobs\/job-1/,
        respond: () => (++polls < 3 ? new Response(null, { status: 202 }) : docx()),
      },
    ]);

    await convertDocx(remote, '{}', 0);

    expect(polls).toBe(3);
  });

  it('carries the warning the result headers state', async () => {
    installFetchMock([
      {
        method: 'POST',
        match: /\/convert\/jobs$/,
        respond: () => new Response(null, { status: 202, headers: { Location: JOB_URL } }),
      },
      {
        method: 'GET',
        match: /\/convert\/jobs\/job-1/,
        respond: () =>
          docx({
            'Missing-WorkItem-Attachments-Count': '1',
            'WorkItem-IDs-With-Missing-Attachment': 'EL-7',
          }),
      },
    ]);

    const result = await convertDocx(remote, '{}', 0);

    expect(result.warning).toContain('1 image(s) in WI(s) EL-7 were not exported');
  });

  it('rejects with the reason a refused submission gave', async () => {
    installFetchMock([
      { method: 'POST', match: /\/convert\/jobs$/, status: 400, json: { message: 'Missing export parameters' } },
    ]);

    await expect(convertDocx(remote, '{}', 0)).rejects.toThrow('Missing export parameters');
  });

  it('rejects with the reason a failed job gave', async () => {
    installFetchMock([
      {
        method: 'POST',
        match: /\/convert\/jobs$/,
        respond: () => new Response(null, { status: 202, headers: { Location: JOB_URL } }),
      },
      { method: 'GET', match: /\/convert\/jobs\/job-1/, status: 409, json: { errorMessage: 'Conversion failed' } },
    ]);

    await expect(convertDocx(remote, '{}', 0)).rejects.toThrow('Conversion failed');
  });

  it('says so when the job was accepted with nothing to poll', async () => {
    installFetchMock([
      { method: 'POST', match: /\/convert\/jobs$/, respond: () => new Response(null, { status: 202 }) },
    ]);

    await expect(convertDocx(remote, '{}', 0)).rejects.toThrow('without a location to poll');
  });
});
