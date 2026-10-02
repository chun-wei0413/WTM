import { describe, expect, it } from 'vitest';
import type { Slot } from '../api/types';
import {
  diffSlots,
  fieldsToProfile,
  hasChanges,
  parseLines,
  parseList,
  profileIsComplete,
  profileToFields,
  slotProblem,
} from './templateForm';

const slot = (slotNo: number, patch: Partial<Slot> = {}): Slot => ({
  slotNo,
  role: `role ${slotNo}`,
  maxChars: 10,
  required: true,
  x: 0,
  y: 0,
  width: 100,
  height: 50,
  ...patch,
});

describe('parseLines / parseList', () => {
  it('keeps one item per non-empty line and trims them', () => {
    expect(parseLines('  a  \n\n b\r\n   \nc')).toEqual(['a', 'b', 'c']);
  });

  it('splits lists on every kind of comma', () => {
    expect(parseList('嫌棄, 偏好，得意、驕傲\n  ,')).toEqual(['嫌棄', '偏好', '得意', '驕傲']);
  });

  it('gives nothing for blank input', () => {
    expect(parseLines('  \n ')).toEqual([]);
    expect(parseList(' , ')).toEqual([]);
  });
});

describe('profile fields', () => {
  it('survives a round trip between the form and the server shape', () => {
    const profile = {
      meaning: '拒絕一件事,偏好另一件事',
      usageExamples: ['不想寫文件', '拒絕加班'],
      emotions: ['嫌棄', '偏好'],
      aliases: ['Drake'],
      imageText: 'NO / YES',
      tags: ['饒舌歌手', '對比'],
    };

    expect(fieldsToProfile(profileToFields(profile))).toEqual(profile);
  });

  it('a profile needs a meaning and at least one usage example', () => {
    const base = { emotions: [], aliases: [], imageText: '', tags: [] };
    expect(profileIsComplete({ ...base, meaning: 'x', usageExamples: ['y'] })).toBe(true);
    expect(profileIsComplete({ ...base, meaning: '  ', usageExamples: ['y'] })).toBe(false);
    expect(profileIsComplete({ ...base, meaning: 'x', usageExamples: [] })).toBe(false);
  });

  it('keeps the tags and the picture text, so saving a description never wipes them', () => {
    const fields = profileToFields({
      meaning: 'm',
      usageExamples: ['u'],
      emotions: [],
      aliases: [],
      imageText: 'I am once again asking',
      tags: ['政治', '請願'],
    });

    expect(fieldsToProfile(fields)).toMatchObject({ imageText: 'I am once again asking', tags: ['政治', '請願'] });
  });
});

describe('diffSlots', () => {
  it('finds nothing to do when the working copy equals the server', () => {
    const changes = diffSlots([slot(1), slot(2)], [slot(1), slot(2)]);

    expect(changes).toEqual({ remove: [], change: [], add: [] });
    expect(hasChanges(changes)).toBe(false);
  });

  it('sorts changes into added, changed and removed', () => {
    const server = [slot(1), slot(2), slot(3)];
    const draft = [slot(1), slot(2, { maxChars: 20 }), slot(4)];

    const changes = diffSlots(server, draft);

    expect(changes.remove).toEqual([3]);
    expect(changes.change.map((s) => s.slotNo)).toEqual([2]);
    expect(changes.add.map((s) => s.slotNo)).toEqual([4]);
    expect(hasChanges(changes)).toBe(true);
  });

  it('notices a change in any single field', () => {
    for (const patch of [{ role: 'x' }, { maxChars: 3 }, { required: false }, { x: 1 }, { y: 1 }, { width: 5 }, { height: 5 }]) {
      expect(diffSlots([slot(1)], [slot(1, patch)]).change).toHaveLength(1);
    }
  });

  it('treats a deleted and re-drawn slot with a reused number as a change, not a remove and an add', () => {
    const changes = diffSlots([slot(1)], [slot(1, { width: 90 })]);

    expect(changes.remove).toEqual([]);
    expect(changes.add).toEqual([]);
    expect(changes.change).toHaveLength(1);
  });
});

describe('slotProblem', () => {
  it('accepts good slots', () => {
    expect(slotProblem([slot(1), slot(2)])).toBeNull();
  });

  it('names the slot that needs attention', () => {
    expect(slotProblem([slot(1), slot(2, { role: '  ' })])).toContain('2');
    expect(slotProblem([slot(3, { maxChars: 0 })])).toContain('3');
    expect(slotProblem([slot(4, { maxChars: 2.5 })])).toContain('4');
  });
});
