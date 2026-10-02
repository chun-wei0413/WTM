import { describe, expect, it } from 'vitest';
import type { TemplateStatus, TemplateSummary } from '../api/types';
import { countByStatus, filterTemplates } from './templateList';

const entry = (over: Partial<TemplateSummary> & { id: string; status: TemplateStatus }): TemplateSummary => ({
  name: '未命名梗圖',
  version: 1,
  imageUrl: '/i',
  updatedAt: '2026-01-01T00:00:00Z',
  meaning: null,
  tags: [],
  sourceType: null,
  attribution: null,
  ...over,
});

const list = [
  entry({ id: 'a', status: 'APPROVED', name: 'Distracted Boyfriend', meaning: '男友被別的女生吸引', tags: ['男友', '劈腿'], sourceType: 'IMGFLIP' }),
  entry({ id: 'b', status: 'APPROVED', name: 'Epic Handshake', meaning: '兩個人握手', tags: ['友情'], sourceType: 'IMGFLIP' }),
  entry({ id: 'c', status: 'RETIRED', attribution: 'PTT StupidClown · someone · [眼殘] 噴霧器', sourceType: 'PTT' }),
  entry({ id: 'd', status: 'DRAFT' }),
];

describe('filtering the administrator list', () => {
  it('shows one status, or all of them', () => {
    expect(filterTemplates(list, 'APPROVED', '').map((e) => e.id)).toEqual(['a', 'b']);
    expect(filterTemplates(list, 'RETIRED', '').map((e) => e.id)).toEqual(['c']);
    expect(filterTemplates(list, 'ALL', '')).toHaveLength(4);
  });

  it('finds words in the name, the meaning, the tags, the source and the credit', () => {
    expect(filterTemplates(list, 'ALL', 'handshake').map((e) => e.id)).toEqual(['b']);
    expect(filterTemplates(list, 'ALL', '握手').map((e) => e.id)).toEqual(['b']);
    expect(filterTemplates(list, 'ALL', '劈腿').map((e) => e.id)).toEqual(['a']);
    expect(filterTemplates(list, 'ALL', 'ptt').map((e) => e.id)).toEqual(['c']);
    expect(filterTemplates(list, 'ALL', '噴霧器').map((e) => e.id)).toEqual(['c']);
  });

  it('needs every word of the search to be found, in any order and whatever the capitals', () => {
    expect(filterTemplates(list, 'ALL', 'IMGFLIP 友情').map((e) => e.id)).toEqual(['b']);
    expect(filterTemplates(list, 'ALL', '友情 男友')).toEqual([]);
  });

  it('can combine a status with a search', () => {
    expect(filterTemplates(list, 'RETIRED', 'handshake')).toEqual([]);
    expect(filterTemplates(list, 'APPROVED', 'imgflip')).toHaveLength(2);
  });

  it('ignores spaces around the search', () => {
    expect(filterTemplates(list, 'ALL', '   ')).toHaveLength(4);
  });
});

describe('counting by status', () => {
  it('counts every status and the total', () => {
    expect(countByStatus(list)).toEqual({ ALL: 4, APPROVED: 2, RETIRED: 1, DRAFT: 1 });
  });

  it('counts nothing for an empty list', () => {
    expect(countByStatus([])).toEqual({ ALL: 0, APPROVED: 0, RETIRED: 0, DRAFT: 0 });
  });
});
