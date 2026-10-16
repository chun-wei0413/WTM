import type { LoginResponse } from './types';
import { request } from './http';

export const authApi = {
  login: (username: string, password: string) =>
    request<LoginResponse>('POST', '/api/auth/login', { json: { username, password }, authenticated: false }),

  register: (username: string, password: string) =>
    request<{ id: string; username: string }>('POST', '/api/auth/register', {
      json: { username, password },
      authenticated: false,
    }),
};
