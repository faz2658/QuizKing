/**
 * A tab opened before a deploy still runs the old app. Its pages' code files are named by content hash, and a
 * deploy replaces them, so opening a page the tab hadn't loaded yet asks for a file that no longer exists (404)
 * and the browser can't load it. The fix: reload into the new version, at the page the player was going to.
 */

/** Browsers word a failed `import()` of a page's code differently. */
export function isMissingChunk(error: unknown): boolean {
  const text = String((error as { message?: unknown })?.message ?? error ?? '');
  return /Failed to fetch dynamically imported module|Importing a module script failed|error loading dynamically imported module|Loading chunk [\w-]+ failed|ChunkLoadError/i
    .test(text);
}

const KEY = 'quizcadia.reloadedFor';
const MIN_GAP_MS = 30_000;

/**
 * Reloads at `url` unless we already did so for it in the last 30 seconds (then something else is wrong, and
 * reloading again would loop). Returns whether it reloaded.
 */
export function reloadIntoNewVersion(
  url: string,
  go: (url: string) => void = (u) => window.location.assign(u),
  storage: Pick<Storage, 'getItem' | 'setItem'> | null = safeSession(),
  now = Date.now(),
): boolean {
  try {
    const last = JSON.parse(storage?.getItem(KEY) ?? 'null') as { url: string; at: number } | null;
    if (last && last.url === url && now - last.at < MIN_GAP_MS) {
      return false;
    }
    storage?.setItem(KEY, JSON.stringify({ url, at: now }));
  } catch {
    // storage blocked: reload anyway, once per page load at most (the reload itself resets this code)
  }
  go(url);
  return true;
}

function safeSession(): Storage | null {
  try {
    return globalThis.sessionStorage ?? null;
  } catch {
    return null;
  }
}
