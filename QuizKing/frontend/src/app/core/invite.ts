/** Shares a link with the phone's share sheet, or copies it. Returns what happened, for a short message. */
export async function shareLink(title: string, text: string, url: string): Promise<'shared' | 'copied' | 'failed'> {
  const nav = navigator as Navigator;
  try {
    if (nav.share) {
      await nav.share(url ? { title, text, url } : { title, text });
      return 'shared';
    }
    await nav.clipboard.writeText(url ? `${text} ${url}` : text);
    return 'copied';
  } catch (e) {
    return (e as Error)?.name === 'AbortError' ? 'shared' : 'failed';
  }
}
