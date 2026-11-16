import { track } from './analytics';

/** Shares a link with the phone's share sheet, or copies it. Returns what happened, for a short message. */
export async function shareLink(title: string, text: string, url: string): Promise<'shared' | 'copied' | 'failed'> {
  const nav = navigator as Navigator;
  try {
    if (nav.share) {
      await nav.share(url ? { title, text, url } : { title, text });
      track('share', { method: 'share_sheet', content_type: shareKind(url) });
      return 'shared';
    }
    await nav.clipboard.writeText(url ? `${text} ${url}` : text);
    track('share', { method: 'copy', content_type: shareKind(url) });
    return 'copied';
  } catch (e) {
    return (e as Error)?.name === 'AbortError' ? 'shared' : 'failed';
  }
}

/** "join", "cup", "challenge"…: the first path segment, so shares can be counted by kind (never the full link). */
function shareKind(url: string): string {
  try {
    return new URL(url, location.origin).pathname.split('/')[1] || 'home';
  } catch {
    return 'text';
  }
}
