import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { errorMessage } from '../core/http-errors';

const shared = `
  :host { display: flex; justify-content: center; padding: 32px 0 56px; }
  .panel { width: min(440px, 100%); padding: 28px; }
  h1 { margin: 0 0 8px; font-size: 1.6rem; letter-spacing: -0.02em; }
  p { margin: 0 0 18px; }
  .wide { width: 100%; padding: 14px; }
  .done { border-radius: var(--radius); padding: 12px 16px; background: var(--good-bg); color: var(--good); font-weight: 600;
    border: 1px solid color-mix(in srgb, var(--good) 35%, transparent); }
  .back { display: inline-block; margin-top: 18px; color: var(--text-muted); }
  .hint { font-weight: 400; font-size: 0.8rem; }
`;

/** "Forgot password": asks for the email and sends a reset link to it. */
@Component({
  selector: 'mv-forgot-password',
  imports: [FormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="card panel fade-up">
      <h1>Forgot your password?</h1>
      @if (available() === false) {
        <div class="done" role="status">
          Password reset by email isn't available yet. If you signed up with Google, use <strong>Sign in with Google</strong>
          on the log-in page. Otherwise, ask the site's admin for a reset link.
        </div>
      } @else if (sent()) {
        <div class="done" role="status">
          If an account uses {{ sentTo() }}, a reset link is on its way. It works for 30 minutes.
          Check your spam folder if it doesn't arrive.
        </div>
      } @else {
        <p class="muted">Enter the email you signed up with and we'll send you a link to choose a new password.</p>
        <form class="stack" (ngSubmit)="submit()">
          <label class="field">Email
            <input class="input" name="email" type="email" [(ngModel)]="email" required autocomplete="email" />
          </label>
          @if (error()) { <div class="alert" role="alert">{{ error() }}</div> }
          <button class="btn btn-primary wide" type="submit" [disabled]="busy()">
            {{ busy() ? 'Sending…' : 'Send reset link' }} <span aria-hidden="true">→</span>
          </button>
        </form>
      }
      <a class="back" routerLink="/welcome">← Back to log in</a>
    </div>
  `,
  styles: shared,
})
export class ForgotPassword {
  private readonly auth = inject(AuthService);
  /** null while we ask the server; false when it can't email reset links. */
  protected readonly available = signal<boolean | null>(null);

  constructor() {
    this.auth.providers().subscribe({ next: (p) => this.available.set(!!p?.passwordReset), error: () => this.available.set(null) });
  }
  protected email = '';
  protected readonly busy = signal(false);
  protected readonly sent = signal(false);
  protected readonly sentTo = signal('');
  protected readonly error = signal<string | null>(null);

  protected submit(): void {
    const email = this.email.trim();
    if (!/^[^@\s]+@[^@\s]+$/.test(email)) {
      this.error.set('Enter the email address of your account.');
      return;
    }
    this.error.set(null);
    this.busy.set(true);
    this.auth.forgotPassword(email).subscribe({
      next: () => {
        this.sentTo.set(email);
        this.sent.set(true);
        this.busy.set(false);
      },
      error: (e) => {
        this.busy.set(false);
        this.error.set(errorMessage(e));
      },
    });
  }
}

/** Opened from the emailed link (/reset-password?token=...): choose a new password, then you're signed in. */
@Component({
  selector: 'mv-reset-password',
  imports: [FormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="card panel fade-up">
      <h1>Choose a new password</h1>
      @if (!token()) {
        <div class="alert" role="alert">This link is incomplete. Open the link from the email again, or ask for a new one.</div>
        <a class="back" routerLink="/forgot-password">Send a new link →</a>
      } @else {
        <form class="stack" (ngSubmit)="submit()">
          <label class="field">New password
            <input class="input" name="password" type="password" [(ngModel)]="password" required minlength="8" maxlength="72"
              autocomplete="new-password" />
            <span class="muted hint">At least 8 characters</span>
          </label>
          <label class="field">Type it again
            <input class="input" name="confirm" type="password" [(ngModel)]="confirm" required autocomplete="new-password" />
          </label>
          @if (error()) {
            <div class="alert" role="alert">{{ error() }}</div>
            @if (expired()) { <a routerLink="/forgot-password">Send a new link →</a> }
          }
          <button class="btn btn-primary wide" type="submit" [disabled]="busy()">
            {{ busy() ? 'Saving…' : 'Save and sign in' }} <span aria-hidden="true">→</span>
          </button>
        </form>
      }
    </div>
  `,
  styles: shared,
})
export class ResetPassword {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  /** From the link's ?token= (router input binding). */
  readonly token = input<string>();
  protected password = '';
  protected confirm = '';
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly expired = signal(false);

  protected submit(): void {
    this.expired.set(false);
    if (this.password.length < 8 || this.password.length > 72) {
      this.error.set('The password needs 8 to 72 characters.');
      return;
    }
    if (this.password !== this.confirm) {
      this.error.set("The two passwords don't match.");
      return;
    }
    this.error.set(null);
    this.busy.set(true);
    this.auth.resetPassword(this.token() ?? '', this.password).subscribe({
      next: () => this.router.navigateByUrl('/'),
      error: (e) => {
        this.busy.set(false);
        this.expired.set(e?.status === 400 && !!e?.error?.detail?.includes('expired'));
        this.error.set(errorMessage(e));
      },
    });
  }
}
