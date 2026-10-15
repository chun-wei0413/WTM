// @vitest-environment jsdom
import { act, cleanup, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { TemplateView } from '../../api/types';
import { slot, template } from './templateFixtures';
import { useTemplateEditor } from './useTemplateEditor';

const admin = vi.hoisted(() => ({
  removeSlot: vi.fn(),
  redefineSlot: vi.fn(),
  defineSlot: vi.fn(),
  saveProfile: vi.fn(),
  approve: vi.fn(),
  retire: vi.fn(),
}));
vi.mock('../../api/client', () => ({ api: { admin } }));

let refresh: ReturnType<typeof vi.fn<() => Promise<TemplateView>>>;

function editor(start: TemplateView = template()) {
  return renderHook(() => useTemplateEditor('t-1', start, refresh));
}

beforeEach(() => {
  Object.values(admin).forEach((fn) => fn.mockReset().mockResolvedValue(undefined));
  refresh = vi.fn<() => Promise<TemplateView>>().mockResolvedValue(template());
});
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

describe('useTemplateEditor', () => {
  it('starts from the saved template with nothing to save', () => {
    const { result } = editor();

    expect(result.current.slots).toEqual(template().slots);
    expect(result.current.fields.meaning).toBe('prefers one thing');
    expect(result.current.fields.examples).toBe('no docs');
    expect(result.current.slotsDirty).toBe(false);
    expect(result.current.profileDirty).toBe(false);
    expect(result.current.anyUnsaved).toBe(false);
    expect(result.current.savedProfileComplete).toBe(true);
    expect(result.current.imageSize).toEqual({ width: 400, height: 300 });
  });

  it('keeps a slot that is moved inside the picture, and marks the slots as changed', () => {
    const { result } = editor();

    act(() => result.current.updateSlot(1, { x: 5000, y: -20 }));

    const moved = result.current.slots[0];
    expect(moved?.x).toBe(300); // 400 - width 100
    expect(moved?.y).toBe(0);
    expect(result.current.slotsDirty).toBe(true);
  });

  it('does not clamp a change that is not about the rectangle', () => {
    const { result } = editor();

    act(() => result.current.updateSlot(2, { role: 'renamed' }));

    expect(result.current.slots[1]).toEqual(slot(2, { role: 'renamed' }));
  });

  it('refuses to save a slot without a name', () => {
    const { result } = editor();

    act(() => result.current.updateSlot(1, { role: '  ' }));

    expect(result.current.problem).toContain('文字格 1');
  });

  it('removes a slot and selects nothing', () => {
    const { result } = editor();
    act(() => result.current.setSelected(2));

    act(() => result.current.removeSlot(2));

    expect(result.current.slots.map((s) => s.slotNo)).toEqual([1]);
    expect(result.current.selected).toBeNull();
    expect(result.current.selectedSlot).toBeNull();
  });

  it('saves the slots removals first, then changes, then additions, and takes the server copy back', async () => {
    const calls: string[] = [];
    admin.removeSlot.mockImplementation(async (_id: string, no: number) => void calls.push(`remove ${no}`));
    admin.redefineSlot.mockImplementation(async (_id: string, s: { slotNo: number }) => void calls.push(`change ${s.slotNo}`));
    admin.defineSlot.mockImplementation(async (_id: string, s: { slotNo: number }) => void calls.push(`add ${s.slotNo}`));
    const saved = template({ slots: [slot(1, { role: 'changed' }), slot(3)] });
    refresh.mockResolvedValue(saved);
    const { result } = editor(template({ slots: [slot(1), slot(2)] }));
    act(() => {
      result.current.setSlots([slot(1, { role: 'changed' }), slot(3)]);
    });

    await act(() => result.current.saveSlots());

    expect(calls).toEqual(['remove 2', 'change 1', 'add 3']);
    expect(refresh).toHaveBeenCalledTimes(1);
    expect(result.current.notice).toBe('文字格已儲存');
    expect(result.current.error).toBeNull();
    expect(result.current.slots).toEqual(saved.slots);
    expect(result.current.busy).toBeNull();
  });

  it('shows what the server really has when saving the slots fails half way', async () => {
    const failure = new Error('conflict');
    admin.redefineSlot.mockRejectedValue(failure);
    refresh.mockResolvedValue(template({ slots: [slot(1), slot(2, { role: 'server copy' })] }));
    const { result } = editor();
    act(() => result.current.updateSlot(2, { role: 'mine' }));

    await act(() => result.current.saveSlots());

    expect(result.current.error).toBe(failure);
    expect(result.current.notice).toBeNull();
    expect(refresh).toHaveBeenCalledTimes(1);
    expect(result.current.slots[1]?.role).toBe('server copy');
  });

  it('is busy while it saves, so the other buttons can be switched off', async () => {
    let finish!: () => void;
    admin.saveProfile.mockReturnValue(new Promise<void>((resolve) => (finish = resolve)));
    const { result } = editor();
    act(() => result.current.setFields({ ...result.current.fields, meaning: 'new meaning' }));

    let saving!: Promise<void>;
    act(() => {
      saving = result.current.saveProfile();
    });
    expect(result.current.busy).toBe('profile');

    await act(async () => {
      finish();
      await saving;
    });
    expect(result.current.busy).toBeNull();
  });

  it('saves the description without throwing away unsaved edits to the slots', async () => {
    refresh.mockResolvedValue(template({ profile: { ...template().profile, meaning: 'new meaning' } }));
    const { result } = editor();
    act(() => {
      result.current.updateSlot(1, { role: 'edited but not saved' });
      result.current.setFields({ ...result.current.fields, meaning: 'new meaning' });
    });

    await act(() => result.current.saveProfile());

    expect(admin.saveProfile).toHaveBeenCalledWith('t-1', expect.objectContaining({ meaning: 'new meaning' }));
    expect(result.current.notice).toBe('描述已儲存');
    expect(result.current.slots[0]?.role).toBe('edited but not saved');
  });

  it('saves the slots without throwing away unsaved edits to the description', async () => {
    refresh.mockResolvedValue(template({ slots: [slot(1, { role: 'saved' }), slot(2)] }));
    const { result } = editor();
    act(() => {
      result.current.updateSlot(1, { role: 'saved' });
      result.current.setFields({ ...result.current.fields, meaning: 'typed, not saved' });
    });

    await act(() => result.current.saveSlots());

    expect(result.current.fields.meaning).toBe('typed, not saved');
  });

  it('drops the selection after saving when the selected slot is no longer there', async () => {
    refresh.mockResolvedValue(template({ slots: [slot(1)] }));
    const { result } = editor();
    act(() => {
      result.current.removeSlot(2);
      result.current.setSelected(2);
    });

    await act(() => result.current.saveSlots());

    expect(result.current.selected).toBeNull();
  });

  it('keeps the selection after saving when the selected slot is still there', async () => {
    refresh.mockResolvedValue(template({ slots: [slot(1, { role: 'renamed' }), slot(2)] }));
    const { result } = editor();
    act(() => {
      result.current.updateSlot(1, { role: 'renamed' });
      result.current.setSelected(1);
    });

    await act(() => result.current.saveSlots());

    expect(result.current.selected).toBe(1);
  });

  it('approves the template and reads it again', async () => {
    const { result } = editor();

    await act(() => result.current.approve());

    expect(admin.approve).toHaveBeenCalledWith('t-1');
    expect(refresh).toHaveBeenCalledTimes(1);
    expect(result.current.notice).toContain('模板已核准');
  });

  it('retires the template only after the visitor confirms', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false);
    const { result } = editor();

    await act(async () => result.current.retire());
    expect(admin.retire).not.toHaveBeenCalled();

    confirm.mockReturnValue(true);
    await act(async () => result.current.retire());
    await vi.waitFor(() => expect(result.current.notice).toBe('模板已下架'));
    expect(admin.retire).toHaveBeenCalledWith('t-1');
  });

  it('shows the error of a failed approval', async () => {
    const failure = new Error('no');
    admin.approve.mockRejectedValue(failure);
    const { result } = editor();

    await act(() => result.current.approve());

    expect(result.current.error).toBe(failure);
    expect(result.current.notice).toBeNull();
  });

  it('knows a retired template, and an incomplete description', () => {
    const { result } = editor(template({ status: 'RETIRED', profile: { ...template().profile, meaning: ' ' } }));

    expect(result.current.retired).toBe(true);
    expect(result.current.savedProfileComplete).toBe(false);
  });
});
