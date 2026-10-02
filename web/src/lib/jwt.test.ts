import { describe, expect, it } from 'vitest';
import { decodeToken, isExpired } from './jwt';

function base64Url(text: string): string {
  const bytes = new TextEncoder().encode(text);
  const binary = Array.from(bytes, (b) => String.fromCharCode(b)).join('');
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function tokenWith(claims: Record<string, unknown>): string {
  return `${base64Url('{"alg":"HS256"}')}.${base64Url(JSON.stringify(claims))}.signature`;
}

describe('decodeToken', () => {
  it('reads the user, roles and expiry', () => {
    const info = decodeToken(tokenWith({ sub: 'u-1', username: 'frank', roles: ['ADMIN'], exp: 1_900_000_000 }));

    expect(info).toEqual({ userId: 'u-1', username: 'frank', roles: ['ADMIN'], expiresAt: 1_900_000_000_000 });
  });

  it('handles non-ASCII usernames', () => {
    const info = decodeToken(tokenWith({ sub: 'u-1', username: '小明', roles: [], exp: 1 }));

    expect(info?.username).toBe('小明');
  });

  it('copes with missing optional claims', () => {
    const info = decodeToken(tokenWith({ sub: 'u-1', exp: 5 }));

    expect(info).toEqual({ userId: 'u-1', username: '', roles: [], expiresAt: 5000 });
  });

  it('ignores role entries that are not text', () => {
    const info = decodeToken(tokenWith({ sub: 'u-1', roles: ['USER', 7, null], exp: 5 }));

    expect(info?.roles).toEqual(['USER']);
  });

  it.each([
    ['not a token', 'hello'],
    ['too few parts', 'a.b'],
    ['payload that is not base64', 'a.@@@.c'],
    ['payload that is not JSON', `a.${base64Url('nope')}.c`],
    ['payload without a subject', tokenWith({ exp: 1 })],
    ['payload without an expiry', tokenWith({ sub: 'u' })],
    ['payload that is a JSON array', `a.${base64Url('[1]')}.c`],
  ])('returns null for %s', (_label, token) => {
    expect(decodeToken(token)).toBeNull();
  });
});

describe('isExpired', () => {
  const info = { userId: 'u', username: 'x', roles: [], expiresAt: 100_000 };

  it('is false while there is time left', () => {
    expect(isExpired(info, 50_000)).toBe(false);
  });

  it('is true once the expiry has passed', () => {
    expect(isExpired(info, 100_001)).toBe(true);
  });

  it('counts a token as expired slightly early so a request is not sent with a dying one', () => {
    expect(isExpired(info, 96_000, 5_000)).toBe(true);
    expect(isExpired(info, 94_000, 5_000)).toBe(false);
  });
});
