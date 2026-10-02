/** What the app reads from a sign-in token to decide what to show. The server still enforces everything. */
export interface TokenInfo {
  userId: string;
  username: string;
  roles: string[];
  /** Milliseconds since the epoch. */
  expiresAt: number;
}

function base64UrlToBytes(value: string): Uint8Array {
  const base64 = value.replace(/-/g, '+').replace(/_/g, '/');
  const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4);
  const binary = atob(padded);
  return Uint8Array.from(binary, (c) => c.charCodeAt(0));
}

/** Reads the claims of a JWT without verifying it. Returns null when it is not a usable token. */
export function decodeToken(token: string): TokenInfo | null {
  const parts = token.split('.');
  if (parts.length !== 3 || !parts[1]) return null;
  try {
    const claims: unknown = JSON.parse(new TextDecoder().decode(base64UrlToBytes(parts[1])));
    if (typeof claims !== 'object' || claims === null) return null;
    const c = claims as Record<string, unknown>;
    if (typeof c.sub !== 'string' || typeof c.exp !== 'number') return null;
    return {
      userId: c.sub,
      username: typeof c.username === 'string' ? c.username : '',
      roles: Array.isArray(c.roles) ? c.roles.filter((r): r is string => typeof r === 'string') : [],
      expiresAt: c.exp * 1000,
    };
  } catch {
    return null;
  }
}

/** True when the token has expired, or will within `skewMs` (so a request is not sent with a dying token). */
export function isExpired(info: TokenInfo, now: number = Date.now(), skewMs = 5_000): boolean {
  return info.expiresAt - skewMs <= now;
}
