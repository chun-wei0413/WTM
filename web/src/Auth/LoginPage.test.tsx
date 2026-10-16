// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { LoginPage } from './LoginPage';

const h = vi.hoisted(() => ({ auth: { user: null as unknown, login: vi.fn(), register: vi.fn() } }));
vi.mock('./AuthContext', () => ({ useAuth: () => h.auth }));
vi.mock('../components/ErrorNotice', () => ({
  ErrorNotice: ({ context }: { context?: string }) => <p role="alert">error in {context}</p>,
}));

function open(state?: { from: string }) {
  return render(
    <MemoryRouter initialEntries={[{ pathname: '/login', state }]}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="*" element={<p>signed in, went elsewhere</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

function fill(username: string, password: string) {
  fireEvent.change(screen.getByLabelText(/帳號/), { target: { value: username } });
  fireEvent.change(screen.getByLabelText(/密碼/), { target: { value: password } });
}

const submitButton = (name: string) => screen.getByRole('button', { name }) as HTMLButtonElement;

beforeEach(() => {
  h.auth.user = null;
  h.auth.login.mockReset().mockResolvedValue(undefined);
  h.auth.register.mockReset().mockResolvedValue(undefined);
});
afterEach(cleanup);

describe('LoginPage', () => {
  it('shows the sign-in form first', () => {
    open();

    expect(screen.getByRole('heading', { name: 'WTM' })).toBeTruthy();
    expect(screen.getByRole('tab', { name: '登入' }).getAttribute('aria-selected')).toBe('true');
    expect(screen.queryByText('至少 8 個字元')).toBeNull();
  });

  it('signs in with the trimmed name and the password', async () => {
    open();
    fill('  frank ', 'secret123');

    fireEvent.click(submitButton('登入'));

    await waitFor(() => expect(h.auth.login).toHaveBeenCalledWith('frank', 'secret123'));
    expect(h.auth.register).not.toHaveBeenCalled();
  });

  it('shows the rules of a new account when registering, and creates it', async () => {
    open();
    fireEvent.click(screen.getByRole('tab', { name: '註冊' }));

    expect(screen.getByText('至少 8 個字元')).toBeTruthy();
    expect(screen.getByText(/3 到 32 個字元/)).toBeTruthy();
    expect((screen.getByLabelText(/密碼/) as HTMLInputElement).minLength).toBe(8);
    fill('frank', 'secret123');
    fireEvent.click(submitButton('建立帳號並登入'));

    await waitFor(() => expect(h.auth.register).toHaveBeenCalledWith('frank', 'secret123'));
    expect(h.auth.login).not.toHaveBeenCalled();
  });

  it('keeps the error of a sign-in that failed, says it is about signing in, and lets the visitor try again', async () => {
    h.auth.login.mockRejectedValue(new Error('wrong password'));
    open();
    fill('frank', 'nope');

    fireEvent.click(submitButton('登入'));

    expect(await screen.findByRole('alert')).toBeTruthy();
    expect(screen.getByText('error in login')).toBeTruthy();
    expect(submitButton('登入').disabled).toBe(false);
  });

  it('forgets the error when the visitor switches tabs', async () => {
    h.auth.login.mockRejectedValue(new Error('wrong password'));
    open();
    fill('frank', 'nope');
    fireEvent.click(submitButton('登入'));
    await screen.findByRole('alert');

    fireEvent.click(screen.getByRole('tab', { name: '註冊' }));

    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('says it is waiting while it signs in', async () => {
    let finish!: () => void;
    h.auth.login.mockReturnValue(new Promise<void>((resolve) => (finish = resolve)));
    open();
    fill('frank', 'secret123');

    fireEvent.click(submitButton('登入'));

    expect(submitButton('請稍候…').disabled).toBe(true);
    finish();
  });

  it('sends a visitor who is already signed in away from the page', () => {
    h.auth.user = { id: 'u-1' };

    open({ from: '/admin' });

    expect(screen.getByText('signed in, went elsewhere')).toBeTruthy();
    expect(screen.queryByRole('heading', { name: 'WTM' })).toBeNull();
  });
});
