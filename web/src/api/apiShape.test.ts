import { describe, expect, it } from 'vitest';
import { ApiError, api, configureAuth } from './client';

/** Every call the pages make, by the name they use, so a call cannot be lost when the files are rearranged. */
const EXPECTED = [
  'admin.approve',
  'admin.collection.addFiles',
  'admin.collection.addUrl',
  'admin.collection.stats',
  'admin.defineSlot',
  'admin.draftTemplate',
  'admin.getTemplate',
  'admin.listTemplates',
  'admin.redefineSlot',
  'admin.removeSlot',
  'admin.reports.apply',
  'admin.reports.automatic',
  'admin.reports.dismiss',
  'admin.reports.list',
  'admin.reports.reanalyze',
  'admin.reports.undo',
  'admin.retire',
  'admin.saveProfile',
  'admin.syncIndex',
  'favorites.add',
  'favorites.list',
  'favorites.remove',
  'library.hotSearches',
  'library.image',
  'library.random',
  'login',
  'pick',
  'register',
  'reports.submit',
  'search',
];

function functionsOf(group: object, prefix = ''): string[] {
  return Object.entries(group)
    .flatMap(([name, value]) => (typeof value === 'function' ? [prefix + name] : functionsOf(value as object, `${prefix}${name}.`)))
    .sort();
}

describe('the api', () => {
  it('has every call the pages use, and no other', () => {
    expect(functionsOf(api)).toEqual(EXPECTED);
  });

  it('exports what the sign-in and the error display need', () => {
    expect(typeof configureAuth).toBe('function');
    expect(new ApiError(404, 'missing')).toBeInstanceOf(Error);
  });
});
