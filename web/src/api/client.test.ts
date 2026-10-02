import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, api, configureAuth } from './client';

type FetchCall = { url: string; init: RequestInit };

let calls: FetchCall[];
let unauthorized: number;

function respond(body: unknown, init: ResponseInit = {}): void {
  const isJson = typeof body === 'object' && body !== null;
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, requestInit: RequestInit) => {
      calls.push({ url, init: requestInit });
      return new Response(body === undefined ? null : isJson ? JSON.stringify(body) : String(body), init);
    }),
  );
}

beforeEach(() => {
  calls = [];
  unauthorized = 0;
  configureAuth({ getToken: () => 'the-token', onUnauthorized: () => void unauthorized++ });
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('requests', () => {
  it('sends the bearer token and a JSON body', async () => {
    respond({ jobId: 'j1' }, { status: 202 });

    const result = await api.submitGeneration('週一又要上班');

    expect(result).toEqual({ jobId: 'j1' });
    expect(calls[0]?.url).toBe('/api/generations');
    expect(calls[0]?.init.method).toBe('POST');
    expect(calls[0]?.init.headers).toMatchObject({ Authorization: 'Bearer the-token', 'Content-Type': 'application/json' });
    expect(JSON.parse(String(calls[0]?.init.body))).toEqual({ situation: '週一又要上班' });
  });

  it('does not send a token when signing in', async () => {
    respond({ token: 't', expiresAt: 'x' });

    await api.login('frank', 'secret-password');

    expect(calls[0]?.init.headers).not.toHaveProperty('Authorization');
  });

  it('percent-encodes values placed in the URL', async () => {
    respond([]);

    await api.search('老闆 改需求&more', 5);

    expect(calls[0]?.url).toBe(`/api/templates/search?q=${encodeURIComponent('老闆 改需求&more')}&limit=5`);
  });

  it('sends an upload as multipart form data and leaves the content type to the browser', async () => {
    respond({ id: 't1' }, { status: 201 });

    await api.admin.draftTemplate('Drake', new File(['x'], 'drake.png', { type: 'image/png' }));

    const init = calls[0]?.init;
    expect(init?.body).toBeInstanceOf(FormData);
    expect((init?.body as FormData).get('name')).toBe('Drake');
    expect(init?.headers).not.toHaveProperty('Content-Type');
  });

  it('sends every picture of a batch under the same form field', async () => {
    respond([]);

    await api.admin.collection.addFiles([new File(['x'], 'a.png'), new File(['y'], 'b.png')]);

    expect(calls[0]?.url).toBe('/api/admin/collection/files');
    const files = (calls[0]?.init.body as FormData).getAll('files');
    expect(files.map((f) => (f as File).name)).toEqual(['a.png', 'b.png']);
  });

  it('starts a collection run with the source, the limit and the changed options', async () => {
    respond({ runId: 'r1' }, { status: 202 });

    await api.admin.collection.startRun('IMGFLIP', 20, { query: '困惑' });

    expect(calls[0]?.url).toBe('/api/admin/collection/runs');
    expect(JSON.parse(String(calls[0]?.init.body))).toEqual({ source: 'IMGFLIP', limit: 20, options: { query: '困惑' } });
  });

  it('adds a picture by address, sending no title when there is none', async () => {
    respond({ fileName: 'u', status: 'IMPORTED', templateId: 't', reason: null });

    await api.admin.collection.addUrl('https://example.com/m.jpg');

    expect(JSON.parse(String(calls[0]?.init.body))).toEqual({ url: 'https://example.com/m.jpg', title: null });
  });

  it('returns nothing for a 204', async () => {
    respond(undefined, { status: 204 });

    await expect(api.keepMeme('m1')).resolves.toBeUndefined();
  });
});

describe('errors', () => {
  it('turns a problem-details body into an ApiError carrying the status and the explanation', async () => {
    respond({ status: 409, detail: 'Only a draft template can be approved' }, { status: 409 });

    const error = await api.admin.approve('t1').catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 409, detail: 'Only a draft template can be approved' });
  });

  it('reads Retry-After from a 429', async () => {
    respond({ detail: 'slow down' }, { status: 429, headers: { 'Retry-After': '30' } });

    const error = (await api.submitGeneration('x').catch((e: unknown) => e)) as ApiError;

    expect(error.status).toBe(429);
    expect(error.retryAfterSeconds).toBe(30);
  });

  it('copes with an error body that is not JSON', async () => {
    respond('<html>Bad gateway</html>', { status: 502 });

    const error = (await api.listMemes().catch((e: unknown) => e)) as ApiError;

    expect(error.status).toBe(502);
    expect(error.retryAfterSeconds).toBeNull();
  });

  it('reports status 0 when the server cannot be reached', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => Promise.reject(new TypeError('Failed to fetch'))));

    const error = (await api.listMemes().catch((e: unknown) => e)) as ApiError;

    expect(error.status).toBe(0);
  });

  it('tells the app when the session has been rejected', async () => {
    respond({ detail: 'expired' }, { status: 401 });

    await api.listMemes().catch(() => undefined);

    expect(unauthorized).toBe(1);
  });

  it('a wrong password is not a rejected session', async () => {
    respond({ detail: 'Invalid username or password' }, { status: 401 });

    const error = (await api.login('frank', 'wrong').catch((e: unknown) => e)) as ApiError;

    expect(error.status).toBe(401);
    expect(unauthorized).toBe(0);
  });
});
