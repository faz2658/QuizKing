/**
 * The game was called Quiz King, and browsers still hold its saved values under "quizking.*" (the sign-in session,
 * sound and vibration settings, the analytics choice, the country, an invite code). Moves each one to its
 * "quizcadia.*" name once, before the app starts, so the rename signs nobody out and resets no setting.
 */
export function moveOldStorageKeys(storage: Storage | undefined = globalThis.localStorage): void {
  if (!storage) {
    return;
  }
  try {
    const old: string[] = [];
    for (let i = 0; i < storage.length; i++) {
      const key = storage.key(i);
      if (key?.startsWith('quizking.')) {
        old.push(key);
      }
    }
    for (const key of old) {
      const renamed = 'quizcadia.' + key.slice('quizking.'.length);
      const value = storage.getItem(key);
      if (value !== null && storage.getItem(renamed) === null) {
        storage.setItem(renamed, value);
      }
      storage.removeItem(key);
    }
  } catch {
    // storage blocked (private mode, site data disabled): nothing to move
  }
}
