import { ChangeDetectionStrategy, Component, computed, inject, input, OnInit, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { BoardsApi } from '../core/api';
import { AuthService, rememberInvite } from '../core/auth.service';
import { errorMessage } from '../core/http-errors';
import { shareLink } from '../core/invite';
import { InviteView } from '../core/models';

/** /join/:code from a friend's link: remembers the code and opens sign-up. */
@Component({
  selector: 'mv-join',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="skeleton" style="height: 200px"></div>`,
})
export class Join implements OnInit {
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  readonly code = input.required<string>();

  ngOnInit(): void {
    if (/^[0-9a-z]{1,7}$/i.test(this.code())) {
      rememberInvite(this.code().toLowerCase());
    }
    // already signed in with an account: nothing to reward, just go play
    this.router.navigateByUrl(this.auth.user()?.role === 'USER' || this.auth.user()?.role === 'ADMIN' ? '/' : '/welcome', { replaceUrl: true });
  }
}

/** Your invite link and what it has earned you. */
@Component({
  selector: 'mv-invite',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="badge"><span class="dot"></span>Invite friends</span>
    <h1>Bring a friend, you both get a 🛡️</h1>
    <p class="muted lead">When a friend creates an account from your link, you each get a streak shield: it saves your
      streak if you miss a day. Invite 1 friend for the Recruiter badge, 5 for Ambassador.</p>

    @if (guest()) {
      <div class="card">
        <p>Invite links are for accounts, so your rewards are kept.</p>
        <a class="btn btn-primary" routerLink="/welcome">Create a free account</a>
      </div>
    } @else if (error()) {
      <div class="alert" role="alert">{{ error() }}</div>
    } @else if (view(); as v) {
      <div class="card link">
        <code>{{ link() }}</code>
        <button class="btn btn-primary" (click)="share()">{{ shareLabel() }}</button>
      </div>
      <div class="stats">
        <div class="card"><strong>{{ v.invited }}</strong><span class="muted">{{ v.invited === 1 ? 'friend joined' : 'friends joined' }}</span></div>
        <div class="card"><strong>🛡️ {{ v.shields }}</strong><span class="muted">streak {{ v.shields === 1 ? 'shield' : 'shields' }}</span></div>
        <div class="card"><strong>{{ v.invited >= 5 ? '📣' : v.invited >= 1 ? '🤝' : '–' }}</strong>
          <span class="muted">{{ v.invited >= 5 ? 'Ambassador' : v.invited >= 1 ? 'Recruiter · ' + (5 - v.invited) + ' to Ambassador' : 'No badge yet' }}</span></div>
      </div>
    } @else {
      <div class="skeleton" style="height: 180px"></div>
    }
  `,
  styles: `
    :host { display: block; max-width: 720px; margin: 0 auto; padding-bottom: 56px; }
    h1 { margin: 8px 0; }
    .card { padding: 18px 22px; }
    .link { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; margin: 20px 0; }
    .link code { flex: 1; min-width: 0; overflow-wrap: anywhere; }
    .stats { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; }
    .stats .card { display: flex; flex-direction: column; align-items: center; gap: 4px; text-align: center; }
    .stats strong { font-size: 1.8rem; }
    @media (max-width: 560px) { .stats { grid-template-columns: 1fr; } }
  `,
})
export class Invite implements OnInit {
  private readonly api = inject(BoardsApi);
  private readonly auth = inject(AuthService);
  protected readonly view = signal<InviteView | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly shareLabel = signal('Share my link');
  protected readonly guest = computed(() => this.auth.user()?.role === 'GUEST');
  protected readonly link = computed(() => `${location.origin}/join/${this.view()?.code ?? ''}`);

  ngOnInit(): void {
    if (!this.guest()) {
      this.api.invites().subscribe({ next: (v) => this.view.set(v), error: (e) => this.error.set(errorMessage(e)) });
    }
  }

  protected async share(): Promise<void> {
    const how = await shareLink('Quiz King', 'Play Quiz King with me! Sign up with my link and we both get a streak shield:', this.link());
    this.shareLabel.set(how === 'copied' ? 'Link copied!' : how === 'failed' ? 'Copy failed' : 'Share my link');
    if (how !== 'shared') setTimeout(() => this.shareLabel.set('Share my link'), 2500);
  }
}
