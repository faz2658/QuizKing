import { ChangeDetectionStrategy, Component, computed, inject, input, OnInit, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';
import { QuestionBankApi } from '../core/api';
import { AuthService, pendingInvite } from '../core/auth.service';
import { errorMessage } from '../core/http-errors';
import { TopicStats } from '../core/models';
import { BlurText } from '../motion/blur-text';
import { CountUp } from '../motion/count-up';
import { Tilt } from '../motion/tilt';
import { GoogleButton } from '../ui/google-button';

type Tab = 'login' | 'register' | 'guest';

@Component({
  selector: 'mv-welcome',
  imports: [FormsModule, RouterLink, BlurText, CountUp, Tilt, GoogleButton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="hero">
      <span class="badge fade-up"><span class="dot"></span>New questions every game</span>
      <h1 class="display"><mv-blur-text text="Think fast. Play *smart.*" /></h1>
      <p class="lead muted fade-up" style="animation-delay: 450ms">
        Timed rounds where harder questions pay more, adaptive games that follow your level,
        and fastest-finger battles against other players.
      </p>

      <div class="stats fade-up" style="animation-delay: 600ms">
        <div><strong><mv-count-up [value]="questionCount()" [duration]="1400" />+</strong><span class="muted">questions</span></div>
        <div><strong><mv-count-up [value]="topicCount()" [duration]="1400" /></strong><span class="muted">topics</span></div>
        <div><strong>3</strong><span class="muted">ways to play</span></div>
      </div>
    </section>

    <section class="split">
      <ul class="modes">
        @for (m of modes; track m.title; let i = $index) {
          <li class="card spotlight fade-up" mvTilt="6" [style.animation-delay.ms]="700 + i * 120">
            <span class="icon" aria-hidden="true">{{ m.icon }}</span>
            <div><h3>{{ m.title }}</h3><p class="muted">{{ m.text }}</p></div>
          </li>
        }
      </ul>

      <div class="card panel fade-up" style="animation-delay: 800ms">
        <div class="pill-tabs tabs" role="tablist" aria-label="Sign in options">
          @for (t of tabs; track t.id) {
            <button type="button" role="tab" [attr.aria-selected]="tab() === t.id" (click)="tab.set(t.id); error.set(null)">{{ t.label }}</button>
          }
        </div>

        <form class="stack" (ngSubmit)="submit()">
          @if (tab() !== 'login') {
            <label class="field">Name
              <input class="input" name="userName" [(ngModel)]="userName" required maxlength="60" autocomplete="nickname" />
            </label>
          }
          @if (tab() !== 'guest') {
            <label class="field">Email
              <input class="input" name="email" type="email" [(ngModel)]="email" required autocomplete="email" />
            </label>
            <label class="field">Password
              <input class="input" name="password" type="password" [(ngModel)]="password" required minlength="8"
                [attr.autocomplete]="tab() === 'register' ? 'new-password' : 'current-password'" />
              @if (tab() === 'register') { <span class="muted hint">At least 8 characters</span> }
            </label>
            @if (tab() === 'login' && canReset()) { <a class="forgot" routerLink="/forgot-password">Forgot password?</a> }
          } @else {
            <p class="muted">Jump straight in. Guest stats are kept in this browser only.</p>
          }
          @if (invited() && tab() === 'register') { <p class="invite-note">🛡️ A friend invited you: sign up and you both get a streak shield.</p> }
          @if (error()) { <div class="alert" role="alert">{{ error() }}</div> }
          <button class="btn btn-primary wide" type="submit" [disabled]="busy()">
            {{ busy() ? 'One moment…' : tab() === 'login' ? 'Log in' : tab() === 'register' ? 'Create account' : 'Start playing' }}
            <span aria-hidden="true">→</span>
          </button>
        </form>
        @if (tab() !== 'guest') {
          <mv-google-button [context]="tab() === 'register' ? 'signup' : 'signin'" (credential)="google($event)" />
        }
      </div>
    </section>
  `,
  styles: `
    .hero { text-align: center; display: flex; flex-direction: column; align-items: center; gap: 22px; padding: 28px 0 44px; }
    .hero h1 { margin: 0; max-width: 14ch; }
    .lead { font-size: clamp(1rem, 2vw, 1.2rem); max-width: 52ch; margin: 0; }
    .stats { display: flex; gap: clamp(20px, 5vw, 56px); margin-top: 6px; }
    .stats div { display: flex; flex-direction: column; align-items: center; }
    .stats strong { font-size: clamp(1.6rem, 3.5vw, 2.3rem); font-weight: 850; letter-spacing: -0.03em; }
    .split { display: grid; grid-template-columns: 1.05fr 0.95fr; gap: 28px; align-items: start; }
    .modes { list-style: none; padding: 0; margin: 0; display: grid; gap: 14px; }
    .modes li { display: flex; gap: 16px; align-items: flex-start; padding: 20px; }
    .modes h3 { margin: 0 0 4px; font-size: 1.1rem; }
    .modes p { margin: 0; font-size: 0.95rem; }
    .icon { font-size: 1.6rem; width: 48px; height: 48px; flex: none; display: grid; place-items: center; border-radius: 14px; background: var(--surface-strong); border: 1px solid var(--border); }
    .panel { padding: 28px; }
    .tabs { display: flex; width: 100%; margin-bottom: 22px; }
    .tabs button { flex: 1; }
    .wide { width: 100%; padding: 14px; }
    .hint { font-weight: 400; font-size: 0.8rem; }
    .invite-note { margin: 0; padding: 10px 14px; border-radius: 12px; background: var(--good-bg); color: var(--good); font-weight: 600; font-size: 0.9rem; }
    .forgot { align-self: flex-end; margin-top: -6px; font-size: 0.85rem; color: var(--text-muted); }
    @media (max-width: 880px) { .split { grid-template-columns: 1fr; } .panel { order: -1; } }
  `,
})
export class Welcome implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly bank = inject(QuestionBankApi);
  readonly next = input<string>();

  protected readonly tabs: { id: Tab; label: string }[] = [
    { id: 'login', label: 'Log in' },
    { id: 'register', label: 'Sign up' },
    { id: 'guest', label: 'Guest' },
  ];
  protected readonly modes = [
    { icon: '⏱', title: 'Timed', text: 'Beat the clock. Easy questions are quick points, advanced ones pay triple.' },
    { icon: '📈', title: 'Adaptive', text: 'Do well and the questions get harder. Struggle and they ease off.' },
    { icon: '⚡', title: 'Multiplayer', text: 'Same question, same moment. First correct answer takes the points.' },
  ];
  protected readonly tab = signal<Tab>('login');
  /** "Forgot password?" only once the server can email reset links. */
  protected readonly canReset = signal(false);
  protected readonly invited = signal(!!pendingInvite());
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected userName = '';
  protected email = '';
  protected password = '';

  private readonly stats = toSignal(this.bank.stats().pipe(catchError(() => of([] as TopicStats[]))), { initialValue: [] as TopicStats[] });
  protected readonly questionCount = computed(() => this.stats().reduce((n, t) => n + t.questions, 0));
  protected readonly topicCount = computed(() => this.stats().length);

  /** Someone opening a friend's link plays as a guest in one tap: no sign-up between them and the fun. */
  ngOnInit(): void {
    this.auth.providers().subscribe({ next: (p) => this.canReset.set(!!p?.passwordReset), error: () => this.canReset.set(false) });
    if (pendingInvite() && !this.next()) {
      this.tab.set('register'); // a friend's invite only counts for a new account
    }
    if (/^\/(know-me|party|ai-quiz|challenge|room|cup)\//.test(this.next() ?? '')) {
      this.tab.set('guest');
    }
  }

  protected submit(): void {
    this.error.set(null);
    if (this.tab() === 'register' && this.password.length < 8) {
      this.error.set('The password needs at least 8 characters.');
      return;
    }
    this.busy.set(true);
    const call = this.tab() === 'guest'
      ? this.auth.playAsGuest(this.userName)
      : this.tab() === 'login'
        ? this.auth.login(this.email, this.password)
        : this.auth.register(this.userName, this.email, this.password);
    call.subscribe({
      next: () => this.done(),
      error: (e) => {
        this.busy.set(false);
        this.error.set(errorMessage(e));
      },
    });
  }

  protected google(credential: string): void {
    this.error.set(null);
    this.busy.set(true);
    this.auth.signInWithGoogle(credential).subscribe({
      next: () => this.done(),
      error: (e) => {
        this.busy.set(false);
        this.error.set(errorMessage(e, 'Google sign-in failed. Please try again.'));
      },
    });
  }

  private done(): void {
    const next = this.next();
    this.router.navigateByUrl(next && next.startsWith('/') && !next.startsWith('//') ? next : '/');
  }
}
