import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Analytics } from '../core/analytics';

/** Asks once whether Google Analytics may count this visit. Nothing is sent before a yes. */
@Component({
  selector: 'mv-consent-banner',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (analytics.ask()) {
      <div class="consent card" role="region" aria-label="Analytics choice">
        <p>Can we use Google Analytics to count visits and see which games people like? It sets cookies; no ads.
          <a routerLink="/privacy">Privacy policy</a></p>
        <div class="row">
          <button type="button" class="btn" (click)="analytics.choose('denied')">No thanks</button>
          <button type="button" class="btn btn-primary" (click)="analytics.choose('granted')">Allow</button>
        </div>
      </div>
    }
  `,
  styles: `
    .consent { position: fixed; left: 16px; right: 16px; bottom: 16px; z-index: 50; max-width: 560px; margin: 0 auto;
      display: flex; gap: 12px; align-items: center; flex-wrap: wrap; padding: 14px 18px; box-shadow: 0 10px 40px rgb(0 0 0 / 0.35); }
    p { margin: 0; flex: 1 1 260px; }
    .row { display: flex; gap: 8px; }
  `,
})
export class ConsentBanner {
  protected readonly analytics = inject(Analytics);
}
