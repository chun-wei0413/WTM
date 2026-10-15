// @vitest-environment jsdom
import { act, cleanup, renderHook, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { template } from './templateFixtures';
import { useTemplate } from './useTemplate';

const { getTemplate } = vi.hoisted(() => ({ getTemplate: vi.fn() }));
vi.mock('../../api/client', () => ({ api: { admin: { getTemplate } } }));

beforeEach(() => {
  getTemplate.mockReset();
});
afterEach(cleanup);

describe('useTemplate', () => {
  it('has no template until the server has answered', async () => {
    getTemplate.mockResolvedValue(template());

    const { result } = renderHook(() => useTemplate('t-1'));

    expect(result.current.template).toBeNull();
    await waitFor(() => expect(result.current.template).toEqual(template()));
    expect(getTemplate).toHaveBeenCalledWith('t-1');
  });

  it('keeps the error when the template cannot be read', async () => {
    const failure = new Error('not found');
    getTemplate.mockRejectedValue(failure);

    const { result } = renderHook(() => useTemplate('t-1'));

    await waitFor(() => expect(result.current.loadError).toBe(failure));
    expect(result.current.template).toBeNull();
  });

  it('reads the template again and hands back what it read', async () => {
    getTemplate.mockResolvedValueOnce(template({ version: 1 })).mockResolvedValueOnce(template({ version: 2 }));
    const { result } = renderHook(() => useTemplate('t-1'));
    await waitFor(() => expect(result.current.template?.version).toBe(1));

    let fresh;
    await act(async () => {
      fresh = await result.current.refresh();
    });

    expect(fresh).toEqual(template({ version: 2 }));
    expect(result.current.template?.version).toBe(2);
  });
});
