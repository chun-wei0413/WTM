import { useState } from 'react';
import { api } from '../../api/client';
import type { Slot, TemplateView } from '../../api/types';
import { clampRect } from '../../lib/slotGeometry';
import {
  diffSlots,
  fieldsToProfile,
  hasChanges,
  profileIsComplete,
  profileToFields,
  slotProblem,
  type ProfileFields,
} from '../../lib/templateForm';

/** What the editor is waiting for, so every other button can be switched off meanwhile. */
export type Busy = null | 'slots' | 'profile' | 'approve' | 'retire';

/**
 * The working copy of one template: the slots and the description being edited, what has not been saved yet,
 * and the actions that save, approve or retire it. `refresh` reads the template from the server again.
 */
export function useTemplateEditor(id: string, template: TemplateView, refresh: () => Promise<TemplateView>) {
  const [slots, setSlots] = useState<Slot[]>(template.slots);
  const [fields, setFields] = useState<ProfileFields>(() => profileToFields(template.profile));
  const [selected, setSelected] = useState<number | null>(null);
  const [busy, setBusy] = useState<Busy>(null);
  const [error, setError] = useState<unknown>(null);
  const [notice, setNotice] = useState<string | null>(null);

  /** Reads the template again. Each part of the working copy is only replaced when asked, so saving one part never throws away unsaved edits to the other. */
  async function reload(replace: { slots?: boolean; profile?: boolean } = {}) {
    const fresh = await refresh();
    if (replace.slots) {
      setSlots(fresh.slots);
      setSelected((current) => (fresh.slots.some((s) => s.slotNo === current) ? current : null));
    }
    if (replace.profile) setFields(profileToFields(fresh.profile));
  }

  const retired = template.status === 'RETIRED';
  const imageSize = { width: template.imageWidth, height: template.imageHeight };
  const changes = diffSlots(template.slots, slots);
  const slotsDirty = hasChanges(changes);
  const problem = slotProblem(slots);
  const draftProfile = fieldsToProfile(fields);
  const profileDirty = JSON.stringify(draftProfile) !== JSON.stringify(fieldsToProfile(profileToFields(template.profile)));
  const savedProfileComplete = profileIsComplete(template.profile);
  const selectedSlot = slots.find((s) => s.slotNo === selected) ?? null;
  const anyUnsaved = slotsDirty || profileDirty;

  async function run(kind: Exclude<Busy, null>, work: () => Promise<void>) {
    setBusy(kind);
    setError(null);
    setNotice(null);
    try {
      await work();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(null);
    }
  }

  const saveSlots = () =>
    run('slots', async () => {
      try {
        // Removals first, so a number freed by deleting a slot is never in the way of a new one.
        for (const slotNo of changes.remove) await api.admin.removeSlot(id, slotNo);
        for (const slot of changes.change) await api.admin.redefineSlot(id, slot);
        for (const slot of changes.add) await api.admin.defineSlot(id, slot);
        await reload({ slots: true });
        setNotice('文字格已儲存');
      } catch (e) {
        // Some steps may have gone through; show what the server really has before reporting the problem.
        await reload({ slots: true }).catch(() => undefined);
        throw e;
      }
    });

  const saveProfile = () =>
    run('profile', async () => {
      await api.admin.saveProfile(id, draftProfile);
      await reload({ profile: true });
      setNotice('描述已儲存');
    });

  const approve = () =>
    run('approve', async () => {
      await api.admin.approve(id);
      await reload();
      setNotice('模板已核准。搜尋索引會在約 15 秒內更新,也可以到列表頁按「同步搜尋索引」立刻更新。');
    });

  const retire = () => {
    if (!window.confirm('下架後,這個模板就不能再用來做新的梗圖,而且無法恢復。確定要下架嗎?')) return;
    void run('retire', async () => {
      await api.admin.retire(id);
      await reload();
      setNotice('模板已下架');
    });
  };

  function updateSlot(slotNo: number, patch: Partial<Slot>) {
    setSlots((current) =>
      current.map((s) => {
        if (s.slotNo !== slotNo) return s;
        const merged = { ...s, ...patch };
        const touchesRect = 'x' in patch || 'y' in patch || 'width' in patch || 'height' in patch;
        return touchesRect ? { ...merged, ...clampRect(merged, imageSize) } : merged;
      }),
    );
  }

  function removeSlot(slotNo: number) {
    setSlots((current) => current.filter((s) => s.slotNo !== slotNo));
    setSelected(null);
  }

  return {
    slots,
    setSlots,
    fields,
    setFields,
    selected,
    setSelected,
    selectedSlot,
    busy,
    error,
    notice,
    retired,
    imageSize,
    slotsDirty,
    problem,
    profileDirty,
    savedProfileComplete,
    anyUnsaved,
    saveSlots,
    saveProfile,
    approve,
    retire,
    updateSlot,
    removeSlot,
  };
}
