import { describe, expect, it } from 'vitest';
import type { LibraryItem } from '../api/types';
import { withFavorite, withoutFavorite } from './favorites';

const item = (id: string): LibraryItem => ({
  templateId: id,
  name: id,
  imageUrl: `/img/${id}`,
  meaning: null,
  tags: [],
  imageText: null,
  sourceType: null,
  sourceUrl: null,
  attribution: null,
});

describe('favorites list', () => {
  it('puts a new favorite first', () => {
    expect(withFavorite([item('a')], item('b')).map((i) => i.templateId)).toEqual(['b', 'a']);
  });

  it('does not add the same one twice', () => {
    expect(withFavorite([item('a'), item('b')], item('b')).map((i) => i.templateId)).toEqual(['a', 'b']);
  });

  it('removes one and leaves the rest in order', () => {
    expect(withoutFavorite([item('a'), item('b'), item('c')], 'b').map((i) => i.templateId)).toEqual(['a', 'c']);
  });

  it('does not change the list it was given', () => {
    const original = [item('a')];

    withFavorite(original, item('b'));
    withoutFavorite(original, 'a');

    expect(original).toHaveLength(1);
  });
});
