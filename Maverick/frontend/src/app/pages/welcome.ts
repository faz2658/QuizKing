import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { errorMessage } from '../core/http-errors';

type Tab = 'login' | 'register' | 'guest';

@Component({
  selector: 'mv-welcome',
  imports: [FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="hero">
      <div class="pitch">
        <span class="chip">Quiz arena</span>
        <h1>Think fast. <span class="accent">Play smart.</span></h1>
        <p class="muted lead">Timed rounds where harder questions pay more, adaptive games that follow your level,
          and fastest-finger-first battles against other players.</p>
        <ul class="perks">
          <li><strong>Timed</strong> · beat the clock on every question</li>
          <li><strong>Adaptive</strong> · difficulty moves with your score</li>
          <li><strong>Multiplayer</strong> · first correct answer takes the points</li>
        </ul>
      </div>

      <div class="card panel">
        <div class="tabs" role="tablist">
          @for (t of tabs; track t.id) {
            <button type="button" role="tab" [attr.aria-selected]="tab() === t.id" [class.on]="tab() === t.id"
              (click)="tab.set(t.id); error.set(null)">{{ t.label }}</button>
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
          } @else {
            <p class="muted">Guests can play right away. Stats are kept for this browser only.</p>
          }
          @if (error()) { <div class="alert" role="alert">{{ error() }}</div> }
          <button class="btn btn-primary wide" type="submit" [disabled]="busy()">
            {{ busy() ? 'One moment…' : tab() === 'login' ? 'Log in' : tab() === 'register' ? 'Create account' : 'Start playing' }}
          </button>
        </form>
      </div>
    </section>
  `,
  styles: `
    .hero { display: grid; grid-template-columns: 1.1fr 0.9fr; gap: 40px; align-items: center; padding-top: 24px; }
    .accent { background: linear-gradient(90deg, var(--accent), var(--accent-2)); -webkit-background-clip: text; background-clip: text; color: transparent; }
    .lead { font-size: 1.1rem; max-width: 46ch; }
    .perks { list-style: none; padding: 0; margin: 24px 0 0; display: grid; gap: 10px; }
    .perks li { padding: 12px 16px; border-radius: var(--radius); background: var(--bg-raised); border: 1px solid var(--border); }
    .tabs { display: grid; grid-template-columns: repeat(3, 1fr); gap: 4px; padding: 4px; background: var(--bg-sunken); border-radius: 999px; margin-bottom: 20px; }
    .tabs button { font: inherit; font-weight: 700; border: 0; background: transparent; color: var(--text-muted); padding: 9px; border-radius: 999px; cursor: pointer; }
    .tabs button.on { background: var(--surface); color: var(--text); box-shadow: var(--shadow); }
    .wide { justify-content: center; width: 100%; padding: 13px; }
    .hint { font-weight: 400; font-size: 0.8rem; }
    @media (max-width: 860px) { .hero { grid-template-columns: 1fr; } }
  `,
})
export class Welcome {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  readonly next = input<string>();

  protected readonly tabs: { id: Tab; label: string }[] = [
    { id: 'login', label: 'Log in' },
    { id: 'register', label: 'Sign up' },
    { id: 'guest', label: 'Guest' },
  ];
  protected readonly tab = signal<Tab>('login');
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected userName = '';
  protected email = '';
  protected password = '';

  protected submit(): void {
    this.error.set(null);
    if (this.tab() === 'guest') {
      this.auth.playAsGuest(this.userName);
      this.done();
      return;
    }
    if (this.tab() === 'register' && this.password.length < 8) {
      this.error.set('The password needs at least 8 characters.');
      return;
    }
    this.busy.set(true);
    const call = this.tab() === 'login'
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

  private done(): void {
    const next = this.next();
    this.router.navigateByUrl(next && next.startsWith('/') && !next.startsWith('//') ? next : '/');
  }
}
