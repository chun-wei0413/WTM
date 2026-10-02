import type { MemeProfile, Slot } from '../api/types';

/** One item per non-empty line. */
export function parseLines(text: string): string[] {
  return text
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line.length > 0);
}

/** Items separated by commas, in half-width, full-width or the Chinese enumeration form. */
export function parseList(text: string): string[] {
  return text
    .split(/[,，、\n]/)
    .map((item) => item.trim())
    .filter((item) => item.length > 0);
}

export interface ProfileFields {
  meaning: string;
  examples: string;
  emotions: string;
  aliases: string;
}

export function profileToFields(profile: MemeProfile): ProfileFields {
  return {
    meaning: profile.meaning,
    examples: profile.usageExamples.join('\n'),
    emotions: profile.emotions.join('、'),
    aliases: profile.aliases.join('、'),
  };
}

export function fieldsToProfile(fields: ProfileFields): MemeProfile {
  return {
    meaning: fields.meaning.trim(),
    usageExamples: parseLines(fields.examples),
    emotions: parseList(fields.emotions),
    aliases: parseList(fields.aliases),
  };
}

/** What an approved template needs: the same rule the server applies. */
export function profileIsComplete(profile: MemeProfile): boolean {
  return profile.meaning.trim().length > 0 && profile.usageExamples.length > 0;
}

export interface SlotChanges {
  /** Slot numbers that exist on the server but are gone from the working copy. */
  remove: number[];
  /** Slots on both sides whose content differs. */
  change: Slot[];
  /** Slots that only exist in the working copy. */
  add: Slot[];
}

const sameSlot = (a: Slot, b: Slot): boolean =>
  a.role === b.role &&
  a.maxChars === b.maxChars &&
  a.required === b.required &&
  a.x === b.x &&
  a.y === b.y &&
  a.width === b.width &&
  a.height === b.height;

/** The steps that turn the server's slots into the working copy. */
export function diffSlots(server: ReadonlyArray<Slot>, draft: ReadonlyArray<Slot>): SlotChanges {
  const serverByNo = new Map(server.map((s) => [s.slotNo, s]));
  const draftNos = new Set(draft.map((s) => s.slotNo));
  return {
    remove: server.filter((s) => !draftNos.has(s.slotNo)).map((s) => s.slotNo),
    change: draft.filter((s) => {
      const before = serverByNo.get(s.slotNo);
      return before !== undefined && !sameSlot(before, s);
    }),
    add: draft.filter((s) => !serverByNo.has(s.slotNo)),
  };
}

export function hasChanges(changes: SlotChanges): boolean {
  return changes.remove.length > 0 || changes.change.length > 0 || changes.add.length > 0;
}

/** A problem a person has to fix before the slots can be saved, or null. */
export function slotProblem(slots: ReadonlyArray<Slot>): string | null {
  for (const slot of slots) {
    if (slot.role.trim().length === 0) return `文字格 ${slot.slotNo} 需要填寫用途名稱`;
    if (!Number.isInteger(slot.maxChars) || slot.maxChars < 1) return `文字格 ${slot.slotNo} 的字數上限至少要是 1`;
  }
  return null;
}
