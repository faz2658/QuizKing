import { ChangeDetectionStrategy, Component, computed, inject, input, OnInit, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';
import { QuestionBankApi } from '../core/api';
import { AuthService, pendingInvite } from '../core/auth.service';
import { errorMessage } from '../core/http-errors';
import { TopicStats } from '../core/models';
import { emailValidator, fieldError, nameValidator, NAME_MAX, PASSWORD_MAX, passwordValidator } from '../core/validation';
import { BlurText } from '../motion/blur-text';
import { CountUp } from '../motion/count-up';
import { Tilt } from '../motion/tilt';
import { takeGoogleNext } from '../core/google-redirect';
import { GoogleButton } from '../ui/google-button';

type Tab = 'login' | 'register' | 'guest';

/** The public start page: what QuizCadia is, sign-in and sign-up. */
@Component({
  selector: 'mv-welcome',
  imports: [ReactiveFormsModule, RouterLink, BlurText, CountUp, Tilt, GoogleButton],
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
      <a class="btn puzzle-link fade-up" style="animation-delay: 700ms" routerLink="/puzzles">🧩 Try today's Puzzle of the Day, no sign-up needed</a>
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
            <button type="button" role="tab" [attr.aria-selected]="tab() === t.id" (click)="select(t.id)">{{ t.label }}</button>
          }
        </div>

        <form class="stack" [formGroup]="form" (ngSubmit)="submit()" novalidate>
          @if (tab() !== 'login') {
            <label class="field">Name
              <input class="input" name="userName" formControlName="userName" [attr.maxlength]="nameMax" autocomplete="nickname"
                [attr.aria-invalid]="!!err('userName')" aria-describedby="userName-error" />
              @if (err('userName'); as m) { <span class="field-error" id="userName-error" role="alert">{{ m }}</span> }
            </label>
          }
          @if (tab() !== 'guest') {
            <label class="field">Email
              <input class="input" name="email" type="email" formControlName="email" autocomplete="email"
                [attr.aria-invalid]="!!err('email')" aria-describedby="email-error" />
              @if (err('email'); as m) { <span class="field-error" id="email-error" role="alert">{{ m }}</span> }
            </label>
            <label class="field">Password
              <input class="input" name="password" type="password" formControlName="password" [attr.maxlength]="passwordMax"
                [attr.autocomplete]="tab() === 'register' ? 'new-password' : 'current-password'"
                [attr.aria-invalid]="!!err('password')" aria-describedby="password-error" />
              @if (err('password'); as m) {
                <span class="field-error" id="password-error" role="alert">{{ m }}</span>
              } @else if (tab() === 'register') { <span class="muted hint">8 to 72 characters</span> }
            </label>
            @if (tab() === 'login' && canReset()) { <a class="forgot" routerLink="/forgot-password">Forgot password?</a> }
          } @else {
            <p class="muted">Jump straight in. Guest stats are kept in this browser only.</p>
          }
          @if (invited() && tab() === 'register') { <p class="invite-note">🛡️ A friend invited you: sign up and you both get a streak shield.</p> }
          @if (error()) { <div class="alert" role="alert">{{ error() }}</div> }
          <button class="btn btn-primary wide" type="submit" [disabled]="busy() || form.invalid">
            {{ busy() ? 'One moment…' : tab() === 'login' ? 'Log in' : tab() === 'register' ? 'Create account' : 'Start playing' }}
            <span aria-hidden="true">→</span>
          </button>
        </form>
        @if (tab() !== 'guest') {
          <mv-google-button [context]="tab() === 'register' ? 'signup' : 'signin'" [next]="next()" (credential)="google($event)" />
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
  /** Set by the login service when Google's redirect sign-in comes back: "ok" or "failed". */
  readonly googleResult = input<string | undefined>(undefined, { alias: 'google' });

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
  protected readonly nameMax = NAME_MAX;
  protected readonly passwordMax = PASSWORD_MAX;
  /** One form for the three tabs: the fields a tab doesn't show are disabled, so they don't count. */
  protected readonly form = new FormGroup({
    userName: new FormControl('', { nonNullable: true }),
    email: new FormControl('', { nonNullable: true }),
    password: new FormControl('', { nonNullable: true }),
  });
  private readonly submitted = signal(false);

  private readonly stats = toSignal(this.bank.stats().pipe(catchError(() => of([] as TopicStats[]))), { initialValue: [] as TopicStats[] });
  protected readonly questionCount = computed(() => this.stats().reduce((n, t) => n + t.questions, 0));
  protected readonly topicCount = computed(() => this.stats().length);

  /** Someone opening a friend's link plays as a guest in one tap: no sign-up between them and the fun. */
  ngOnInit(): void {
    // fetch the Games page's code while the player signs in, so it's ready the moment they're in
    const idle = (window as Window & { requestIdleCallback?: (cb: () => void) => void }).requestIdleCallback ?? ((cb: () => void) => setTimeout(cb, 1500));
    idle(() => void import('./home').catch(() => undefined));
    this.auth.providers().subscribe({ next: (p) => this.canReset.set(!!p?.passwordReset), error: () => this.canReset.set(false) });
    let tab: Tab = this.tab();
    if (pendingInvite() && !this.next()) {
      tab = 'register'; // a friend's invite only counts for a new account
    }
    if (/^\/(know-me|party|ai-quiz|challenge|room|cup)\//.test(this.next() ?? '')) {
      tab = 'guest';
    }
    this.select(tab);
    this.finishGoogleRedirect(); // after select(), which clears any message
  }

  protected select(tab: Tab): void {
    this.tab.set(tab);
    this.error.set(null);
    this.submitted.set(false);
    const { userName, email, password } = this.form.controls;
    userName.setValidators(nameValidator);
    email.setValidators(emailValidator(tab === 'register'));
    password.setValidators(passwordValidator(tab === 'register' ? 'new' : 'current'));
    for (const [c, shown] of [[userName, tab !== 'login'], [email, tab !== 'guest'], [password, tab !== 'guest']] as const) {
      c.markAsUntouched();
      if (shown) c.enable();
      else c.disable();
      c.updateValueAndValidity();
    }
  }

  /** The inline message for a field, once it was left or the form was submitted. */
  protected err(name: 'userName' | 'email' | 'password'): string | null {
    return fieldError(this.form.controls[name], this.submitted());
  }

  protected submit(): void {
    this.error.set(null);
    this.submitted.set(true);
    if (this.form.invalid || this.busy()) {
      this.form.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    const { userName, email, password } = this.form.getRawValue();
    const call = this.tab() === 'guest'
      ? this.auth.playAsGuest(userName.trim())
      : this.tab() === 'login'
        ? this.auth.login(email.trim(), password)
        : this.auth.register(userName.trim(), email.trim(), password);
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

  /** Back from Google's redirect sign-in (Safari, iPhone, iPad): pick up the session the server just started. */
  private finishGoogleRedirect(): void {
    const outcome = this.googleResult();
    if (!outcome) return;
    if (outcome !== 'ok') {
      this.error.set('Google sign-in failed. Please try again.');
      return;
    }
    this.busy.set(true);
    this.auth.finishGoogleRedirect().subscribe({
      next: () => this.router.navigateByUrl(takeGoogleNext()),
      error: () => {
        this.busy.set(false);
        this.error.set('Google sign-in failed. Please try again.');
      },
    });
  }

  private done(): void {
    const next = this.next();
    this.router.navigateByUrl(next && next.startsWith('/') && !next.startsWith('//') ? next : '/');
  }
}
