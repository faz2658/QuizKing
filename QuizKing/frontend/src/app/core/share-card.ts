/** What a share card says. Kept apart from the drawing so it can be tested without a canvas. */
export interface ShareInfo {
  player: string;
  title: string;
  score: number;
  maxScore: number;
  percentage: number;
  correct: number;
  total: number;
  /** e.g. "#3 of 41 today" for the Daily Challenge. */
  rankLine?: string | null;
  url: string;
}

export function shareText(i: ShareInfo): string {
  const rank = i.rankLine ? ` (${i.rankLine})` : '';
  return `👑 I scored ${i.score}/${i.maxScore} (${i.correct}/${i.total} right) in ${i.title} on Quiz King${rank}. Can you beat me? ${i.url}`;
}

/** Draws a 1080x1080 result card (Instagram/WhatsApp friendly) and returns it as a PNG. */
export async function drawShareCard(i: ShareInfo): Promise<Blob> {
  const size = 1080;
  const canvas = document.createElement('canvas');
  canvas.width = size;
  canvas.height = size;
  const g = canvas.getContext('2d');
  if (!g) {
    throw new Error('Canvas is not available');
  }
  const bg = g.createLinearGradient(0, 0, size, size);
  bg.addColorStop(0, '#2a0f5c');
  bg.addColorStop(0.55, '#7b1fa2');
  bg.addColorStop(1, '#ff6a3d');
  g.fillStyle = bg;
  g.fillRect(0, 0, size, size);

  // soft glow behind the score
  const glow = g.createRadialGradient(size / 2, 560, 40, size / 2, 560, 420);
  glow.addColorStop(0, 'rgba(255,255,255,0.28)');
  glow.addColorStop(1, 'rgba(255,255,255,0)');
  g.fillStyle = glow;
  g.fillRect(0, 0, size, size);

  g.textAlign = 'center';
  g.fillStyle = '#ffffff';
  g.font = '900 64px system-ui, -apple-system, Segoe UI, sans-serif';
  g.fillText('👑 Quiz King', size / 2, 140);

  g.font = '600 46px system-ui, -apple-system, Segoe UI, sans-serif';
  g.fillStyle = 'rgba(255,255,255,0.85)';
  g.fillText(clip(i.title, 34), size / 2, 250);

  g.fillStyle = '#ffffff';
  g.font = '900 260px system-ui, -apple-system, Segoe UI, sans-serif';
  g.fillText(`${i.percentage}%`, size / 2, 600);

  g.font = '700 54px system-ui, -apple-system, Segoe UI, sans-serif';
  g.fillText(`${i.score} / ${i.maxScore} points · ${i.correct}/${i.total} right`, size / 2, 710);

  if (i.rankLine) {
    g.font = '800 56px system-ui, -apple-system, Segoe UI, sans-serif';
    g.fillStyle = '#ffd54f';
    g.fillText(i.rankLine, size / 2, 800);
  }

  g.fillStyle = 'rgba(255,255,255,0.9)';
  g.font = '600 44px system-ui, -apple-system, Segoe UI, sans-serif';
  g.fillText(`${clip(i.player, 24)} · Can you beat me?`, size / 2, 930);
  g.font = '500 34px system-ui, -apple-system, Segoe UI, sans-serif';
  g.fillStyle = 'rgba(255,255,255,0.7)';
  g.fillText(i.url.replace(/^https?:\/\//, ''), size / 2, 990);

  return new Promise((resolve, reject) => canvas.toBlob((b) => (b ? resolve(b) : reject(new Error('Could not draw the card'))), 'image/png'));
}

function clip(s: string, max: number): string {
  return s.length > max ? s.slice(0, max - 1) + '…' : s;
}

/** Opens the phone's share sheet with the card, or downloads it where sharing files isn't supported. */
export async function shareCard(i: ShareInfo): Promise<'shared' | 'downloaded'> {
  const blob = await drawShareCard(i);
  const file = new File([blob], 'quiz-king-result.png', { type: 'image/png' });
  const nav = navigator as Navigator & { canShare?: (d: ShareData) => boolean };
  if (nav.share && nav.canShare?.({ files: [file] })) {
    await nav.share({ files: [file], text: shareText(i), title: 'Quiz King' });
    return 'shared';
  }
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = file.name;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 2000);
  return 'downloaded';
}
