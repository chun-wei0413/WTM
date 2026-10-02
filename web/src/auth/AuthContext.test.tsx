// @vitest-environment jsdom
import { cleanup, render, waitFor } from '@testing-library/react';
import { useEffect } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api, configureAuth } from '../api/client';
import { AuthProvider } from './AuthContext';

function base64Url(text: string): string {
  return btoa(text).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function tokenExpiringIn(seconds: number): string {
  const claims = { sub: 'u-1', username: 'frank', roles: ['USER'], exp: Math.floor(Date.now() / 1000) + seconds };
  return `${base64Url('{"alg":"HS256"}')}.${base64Url(JSON.stringify(claims))}.signature`;
}

/** A page that asks the server for something the moment it appears, as every page here does. */
function PageThatFetchesOnLoad() {
  useEffect(() => {
    void api.listMemes().catch(() => undefined);
  }, []);
  return null;
}

let requests: Array<{ headers: Record<string, string> }>;

beforeEach(() => {
  requests = [];
  sessionStorage.clear();
  vi.stubGlobal(
    'fetch',
    vi.fn(async (_url: string, init: RequestInit) => {
      requests.push({ headers: (init.headers ?? {}) as Record<string, string> });
      return new Response('[]', { status: 200 });
    }),
  );
});

afterEach(() => {
  cleanup();
  // The API client keeps its token hooks at module level; start every test without any.
  configureAuth({ getToken: () => null, onUnauthorized: () => undefined });
  vi.unstubAllGlobals();
  sessionStorage.clear();
});

describe('AuthProvider', () => {
  it('sends the saved token with the very first request a page makes when it loads', async () => {
    // This is what happens when someone reloads the tab while signed in. React runs a child's effects
    // before its parent's, so the token must be ready before any page effect runs. (Not wrapped in
    // StrictMode on purpose: StrictMode runs effects twice in development and would hide a late setup.)
    const token = tokenExpiringIn(3600);
    sessionStorage.setItem('wtm.token', token);

    render(
      <AuthProvider>
        <PageThatFetchesOnLoad />
      </AuthProvider>,
    );

    await waitFor(() => expect(requests.length).toBeGreaterThan(0));
    expect(requests[0]?.headers.Authorization).toBe(`Bearer ${token}`);
  });

  it('ignores a saved token that has already expired', async () => {
    sessionStorage.setItem('wtm.token', tokenExpiringIn(-60));

    render(
      <AuthProvider>
        <PageThatFetchesOnLoad />
      </AuthProvider>,
    );

    await waitFor(() => expect(requests.length).toBeGreaterThan(0));
    expect(requests[0]?.headers).not.toHaveProperty('Authorization');
  });
});
