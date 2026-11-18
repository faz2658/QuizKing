import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, inject, input, output, signal, viewChild } from '@angular/core';
import { Theme } from '../core/theme';
import { AuthService } from '../core/auth.service';

/** The parts of Google Identity Services (accounts.google.com/gsi/client) this button uses. */
interface GoogleIdentityServices {
  accounts: {
    id: {
      initialize(config: { client_id: string; callback: (r: { credential: string }) => void; ux_mode?: 'popup'; context?: string }): void;
      renderButton(el: HTMLElement, options: Record<string, unknown>): void;
    };
  };
}

const SCRIPT = 'https://accounts.google.com/gsi/client';
let loading: Promise<GoogleIdentityServices> | null = null;

function loadGoogle(): Promise<GoogleIdentityServices> {
  const w = window as unknown as { google?: GoogleIdentityServices };
  if (w.google?.accounts?.id) {
    return Promise.resolve(w.google);
  }
  loading ??= new Promise((resolve, reject) => {
    const s = document.createElement('script');
    s.src = SCRIPT;
    s.async = true;
    s.onload = () => (w.google ? resolve(w.google) : reject(new Error('Google sign-in did not load')));
    s.onerror = () => {
      loading = null;
      reject(new Error('Google sign-in did not load'));
    };
    document.head.appendChild(s);
  });
  return loading;
}

/**
 * Google's own "Sign in with Google" button. Shows nothing until the server says Google sign-in is set up
 * (and nothing if Google's script is blocked). Emits the ID token Google returns; the server verifies it.
 */
@Component({
  selector: 'mv-google-button',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (available()) {
      <div class="or" aria-hidden="true"><span>or</span></div>
    }
    <div #slot class="slot" [class.hidden]="!available()"></div>
  `,
  styles: `
    :host { display: block; }
    .or { display: flex; align-items: center; gap: 12px; margin: 18px 0 14px; color: var(--text-muted); font-size: 0.85rem; }
    .or::before, .or::after { content: ''; flex: 1; height: 1px; background: var(--border); }
    .slot { display: flex; justify-content: center; min-height: 44px; }
    .hidden { display: none; }
  `,
})
export class GoogleButton {
  private readonly theme = inject(Theme);
  private readonly auth = inject(AuthService);
  private readonly slot = viewChild.required<ElementRef<HTMLElement>>('slot');
  /** "signin" or "signup": only changes the button's wording. */
  readonly context = input<'signin' | 'signup'>('signin');
  readonly credential = output<string>();
  protected readonly available = signal(false);

  constructor() {
    let alive = true;
    inject(DestroyRef).onDestroy(() => (alive = false));
    this.auth.providers().subscribe({
      next: ({ googleClientId }) => {
        if (!googleClientId) {
          return;
        }
        loadGoogle()
          .then((g) => {
            if (!alive) {
              return;
            }
            g.accounts.id.initialize({
              client_id: googleClientId,
              ux_mode: 'popup',
              context: this.context(),
              callback: (r) => this.credential.emit(r.credential),
            });
            const el = this.slot().nativeElement;
            g.accounts.id.renderButton(el, {
              type: 'standard',
              theme: this.theme.current() === 'light' ? 'outline' : 'filled_black', size: 'large', shape: 'pill',
              text: this.context() === 'signup' ? 'signup_with' : 'signin_with',
              width: Math.min(360, Math.max(200, el.parentElement?.clientWidth ?? 320)),
            });
            this.available.set(true);
          })
          .catch(() => {
            /* script blocked (ad blocker, offline): email sign-in still works */
          });
      },
      error: () => {
        /* older server without /providers: no Google button */
      },
    });
  }
}
