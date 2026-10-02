import { describe, expect, it } from 'vitest';
import { ApiError } from '../api/client';
import { describeWait, friendlyError } from './errors';

describe('friendlyError', () => {
  it('says "wrong password" for a failed sign-in, but "session over" anywhere else', () => {
    const unauthorized = new ApiError(401, 'Invalid username or password');

    expect(friendlyError(unauthorized, 'login').title).toBe('帳號或密碼錯誤');
    expect(friendlyError(unauthorized).title).toBe('登入已過期,請重新登入');
  });

  it('tells the user how long to wait when the server says so', () => {
    expect(friendlyError(new ApiError(429, 'Too many', 90)).title).toContain('2 分鐘');
    expect(friendlyError(new ApiError(429, 'Too many', 30)).title).toContain('30 秒');
    expect(friendlyError(new ApiError(429, 'Daily limit')).title).toContain('稍後');
  });

  it('keeps the server explanation as a secondary line for rule violations', () => {
    expect(friendlyError(new ApiError(409, 'Only a draft template can be approved'))).toEqual({
      title: '這個操作和目前的狀態衝突',
      detail: 'Only a draft template can be approved',
    });
  });

  it('describes an unreachable server', () => {
    expect(friendlyError(new ApiError(0, 'Could not reach the server')).title).toContain('連不上伺服器');
  });

  it('falls back to a generic message for anything unexpected', () => {
    expect(friendlyError(new Error('boom')).title).toContain('未預期');
    expect(friendlyError('nope').title).toContain('未預期');
    expect(friendlyError(new ApiError(500, '')).title).toContain('未預期');
  });
});

describe('describeWait', () => {
  it('uses seconds, minutes or hours as appropriate', () => {
    expect(describeWait(10)).toBe('10 秒');
    expect(describeWait(60)).toBe('1 分鐘');
    expect(describeWait(61)).toBe('2 分鐘');
    expect(describeWait(3540)).toBe('59 分鐘');
    expect(describeWait(3600)).toBe('1 小時');
    expect(describeWait(3601)).toBe('2 小時');
  });
});
