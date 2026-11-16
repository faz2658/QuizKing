import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { track } from '../core/analytics';

/** Where tips go: the QuizCadia page on Ko-fi (paid out to the owner's PayPal). */
export const SUPPORT_URL = 'https://ko-fi.com/quizcadia';

/**
 * "Support QuizCadia" (tips on Ko-fi). `link` is a plain text link (footer); `card` is a short note with a button
 * (results screen). Clicks are counted in analytics, for visitors who allowed it, to see which spot works.
 */
@Component({
  selector: 'mv-support',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (kind() === 'card') {
      <aside class="card support fade-up" aria-label="Support QuizCadia">
        <span class="cup" aria-hidden="true">☕</span>
        <p><strong>Enjoying QuizCadia?</strong> It's free, has no ads, and is made by one person. A small tip keeps the
          servers running and new games coming.</p>
        <a class="btn" [href]="url" target="_blank" rel="noopener" (click)="clicked()">Support QuizCadia</a>
      </aside>
    } @else {
      <a [href]="url" target="_blank" rel="noopener" (click)="clicked()">☕ Support QuizCadia</a>
    }
  `,
  styles: `
    :host { display: contents; }
    .support { display: flex; align-items: center; gap: 16px; margin: 18px 0 8px; padding: 16px 20px; }
    .support p { margin: 0; flex: 1; color: var(--text-muted); }
    .support strong { color: var(--text); }
    .cup { font-size: 1.8rem; }
    .support .btn { white-space: nowrap; }
    @media (max-width: 560px) { .support { flex-wrap: wrap; } .support .btn { width: 100%; } }
  `,
})
export class Support {
  readonly kind = input<'link' | 'card'>('link');
  /** Where the link sits, for analytics. */
  readonly placement = input('footer');
  protected readonly url = SUPPORT_URL;

  protected clicked(): void {
    track('support_click', { placement: this.placement() });
  }
}
