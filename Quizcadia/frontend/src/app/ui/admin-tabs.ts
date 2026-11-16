import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

/** Switches between the admin pages. */
@Component({
  selector: 'mv-admin-tabs',
  imports: [RouterLink, RouterLinkActive],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <nav class="tabs" aria-label="Admin">
      <a class="btn" routerLink="/admin/stats" routerLinkActive="btn-primary" ariaCurrentWhenActive="page">Users &amp; activity</a>
      <a class="btn" routerLink="/admin/analytics" routerLinkActive="btn-primary" ariaCurrentWhenActive="page">Website analytics</a>
      <a class="btn" routerLink="/admin" routerLinkActive="btn-primary" [routerLinkActiveOptions]="{ exact: true }" ariaCurrentWhenActive="page">Questions &amp; games</a>
    </nav>
  `,
  styles: `.tabs { display: flex; gap: 8px; flex-wrap: wrap; margin: 4px 0 16px; }`,
})
export class AdminTabs {}
