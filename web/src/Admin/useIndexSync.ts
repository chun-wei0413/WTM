import { useState } from 'react';
import { api } from '../api/client';
import type { IndexSyncResult } from '../api/types';

/**
 * Brings the search index up to date at once, instead of waiting for the next regular sync.
 * `onSynced`, when given, runs after a sync that went through.
 */
export function useIndexSync(onSynced?: () => Promise<void>) {
  const [syncing, setSyncing] = useState(false);
  const [synced, setSynced] = useState<IndexSyncResult | null>(null);
  const [syncError, setSyncError] = useState<unknown>(null);

  async function sync() {
    setSyncing(true);
    setSyncError(null);
    setSynced(null);
    try {
      setSynced(await api.admin.syncIndex());
      if (onSynced) await onSynced();
    } catch (e) {
      setSyncError(e);
    } finally {
      setSyncing(false);
    }
  }

  return { syncing, synced, syncError, sync };
}
