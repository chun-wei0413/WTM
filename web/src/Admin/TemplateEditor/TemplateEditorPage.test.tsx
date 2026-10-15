// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { template } from './templateFixtures';
import { TemplateEditorPage } from './TemplateEditorPage';

const admin = vi.hoisted(() => ({
  getTemplate: vi.fn(),
  saveProfile: vi.fn(),
  approve: vi.fn(),
  retire: vi.fn(),
  removeSlot: vi.fn(),
  redefineSlot: vi.fn(),
  defineSlot: vi.fn(),
}));
vi.mock('../../api/client', () => ({ api: { admin } }));
vi.mock('../../components/ErrorNotice', () => ({ ErrorNotice: () => <p role="alert">error</p> }));
// the drawing area is tested on its own; here it only has to show which slot is selected
vi.mock('../../components/SlotEditor', () => ({
  SlotEditor: ({ slots, selected, onSelect }: { slots: Array<{ slotNo: number }>; selected: number | null; onSelect: (n: number) => void }) => (
    <div data-testid="canvas" data-selected={String(selected)}>
      {slots.map((s) => (
        <button key={s.slotNo} type="button" onClick={() => onSelect(s.slotNo)}>
          pick {s.slotNo}
        </button>
      ))}
    </div>
  ),
}));

function open() {
  return render(
    <MemoryRouter initialEntries={['/admin/templates/t-1']}>
      <Routes>
        <Route path="/admin/templates/:id" element={<TemplateEditorPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  Object.values(admin).forEach((fn) => fn.mockReset().mockResolvedValue(undefined));
  admin.getTemplate.mockResolvedValue(template());
});
afterEach(cleanup);

describe('TemplateEditorPage', () => {
  it('says it is loading, then shows the template with its slots', async () => {
    open();
    expect(screen.getByText('載入中…')).toBeTruthy();

    expect(await screen.findByRole('heading', { name: /Drake/ })).toBeTruthy();
    expect(screen.getByText('版本 1 · 圖片 400 × 300')).toBeTruthy();
    expect(screen.getByRole('heading', { name: '文字格(2)' })).toBeTruthy();
    expect(admin.getTemplate).toHaveBeenCalledWith('t-1');
  });

  it('shows the error when the template cannot be loaded', async () => {
    admin.getTemplate.mockRejectedValue(new Error('missing'));

    open();

    expect(await screen.findByRole('alert')).toBeTruthy();
  });

  it('opens the form of a slot picked on the picture, and edits it', async () => {
    open();
    await screen.findByRole('heading', { name: /Drake/ });

    fireEvent.click(screen.getByRole('button', { name: 'pick 2' }));
    expect(screen.getByTestId('canvas').dataset.selected).toBe('2');
    const role = screen.getByLabelText('用途名稱') as HTMLInputElement;
    expect(role.value).toBe('slot 2');

    fireEvent.change(role, { target: { value: 'new name' } });
    expect((screen.getByRole('button', { name: '儲存文字格' }) as HTMLButtonElement).disabled).toBe(false);
  });

  it('saves the changed slots', async () => {
    admin.getTemplate.mockResolvedValueOnce(template()).mockResolvedValue(template({ slots: [template().slots[0]!] }));
    open();
    await screen.findByRole('heading', { name: /Drake/ });
    fireEvent.click(screen.getByRole('button', { name: 'pick 2' }));

    fireEvent.click(screen.getByRole('button', { name: '刪除這個文字格' }));
    fireEvent.click(screen.getByRole('button', { name: '儲存文字格' }));

    expect(await screen.findByText('文字格已儲存')).toBeTruthy();
    expect(admin.removeSlot).toHaveBeenCalledWith('t-1', 2);
    expect(screen.getByRole('heading', { name: '文字格(1)' })).toBeTruthy();
  });

  it('only lets a saved, complete description be approved', async () => {
    open();
    await screen.findByRole('heading', { name: /Drake/ });
    const approve = screen.getByRole('button', { name: '核准' }) as HTMLButtonElement;
    expect(approve.disabled).toBe(false);

    fireEvent.change(screen.getByLabelText('這個梗的意思'), { target: { value: 'something else' } });

    expect(approve.disabled).toBe(true);
    expect(screen.getByText('有尚未儲存的變更,請先儲存。')).toBeTruthy();
  });

  it('approves the template', async () => {
    admin.getTemplate.mockResolvedValueOnce(template()).mockResolvedValue(template({ status: 'APPROVED' }));
    open();
    await screen.findByRole('heading', { name: /Drake/ });

    fireEvent.click(screen.getByRole('button', { name: '核准' }));

    expect(await screen.findByText(/模板已核准/)).toBeTruthy();
    expect(admin.approve).toHaveBeenCalledWith('t-1');
    expect(within(screen.getByRole('heading', { name: /Drake/ })).getByText('已核准')).toBeTruthy();
  });

  it('locks a retired template', async () => {
    admin.getTemplate.mockResolvedValue(template({ status: 'RETIRED' }));

    open();

    expect(await screen.findByText('這個模板已下架,不能再修改。')).toBeTruthy();
    await waitFor(() => expect((screen.getByRole('button', { name: '儲存描述' }) as HTMLButtonElement).disabled).toBe(true));
    expect((screen.getByLabelText('這個梗的意思') as HTMLTextAreaElement).disabled).toBe(true);
    expect((screen.getByRole('button', { name: '下架' }) as HTMLButtonElement).disabled).toBe(true);
  });
});
