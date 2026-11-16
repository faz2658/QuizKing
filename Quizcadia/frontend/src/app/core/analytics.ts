import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';
import { SignInOptions } from './sign-in-options';

type Gtag = (...args: unknown[]) => void;
type Consent = 'granted' | 'denied';

const KEY = 'quizcadia.analytics';

function gtagFn(): Gtag | undefined {
  return (window as unknown as { gtag?: Gtag }).gtag;
}

/**
 * Sends a Google Analytics event, if the player allowed analytics. Never pass names, emails or ids:
 * only what happened (e.g. track('share', { method: 'copied', content_type: 'daily' })).
 */
export function track(event: string, params: Record<string, string | number | boolean> = {}): void {
  gtagFn()?.('event', event, params);
}

function readConsent(): Consent | null {
  try {
    const v = localStorage.getItem(KEY);
    return v === 'granted' || v === 'denied' ? v : null;
  } catch {
    return null;
  }
}

/**
 * Google Analytics 4, opt-in. Nothing loads (no script, no cookies) until the server has a measurement id
 * and the player says yes. Ad features are off; page views are sent on each route change.
 */
@Injectable({ providedIn: 'root' })
export class Analytics {
  private readonly signInOptions = inject(SignInOptions);
  private readonly router = inject(Router);
  private readonly id = signal<string | null>(null);
  readonly consent = signal<Consent | null>(readConsent());
  /** Show the "allow analytics?" banner: analytics is set up here and the player hasn't answered. */
  readonly ask = computed(() => !!this.id() && this.consent() === null);
  private loaded = false;

  constructor() {
    const sub = this.router.events.pipe(filter((e) => e instanceof NavigationEnd)).subscribe(() => this.pageView());
    inject(DestroyRef).onDestroy(() => sub.unsubscribe());
  }

  /** Fetches the measurement id once at start-up; loads GA if the player already said yes. */
  init(): void {
    this.signInOptions.get().subscribe({
      next: (p) => {
        const id = p?.analyticsId && /^G-[A-Z0-9]{4,20}$/.test(p.analyticsId) ? p.analyticsId : null;
        this.id.set(id);
        if (id && this.consent() === 'granted') this.load(id);
      },
      error: () => {
        /* no analytics */
      },
    });
  }

  choose(c: Consent): void {
    this.consent.set(c);
    try {
      localStorage.setItem(KEY, c);
    } catch {
      /* private mode: ask again next visit */
    }
    const id = this.id();
    if (c === 'granted' && id) {
      this.load(id);
    } else if (c === 'denied' && this.loaded) {
      gtagFn()?.('consent', 'update', { analytics_storage: 'denied' });
      (window as unknown as Record<string, boolean>)[`ga-disable-${id}`] = true;
    }
  }

  private load(id: string): void {
    if (this.loaded) return;
    this.loaded = true;
    const w = window as unknown as { dataLayer: unknown[]; gtag: Gtag } & Record<string, unknown>;
    w[`ga-disable-${id}`] = false;
    w.dataLayer = w.dataLayer || [];
    // gtag.js reads the arguments object itself, so this must stay a plain function
    w.gtag = function gtag() {
      // eslint-disable-next-line prefer-rest-params
      w.dataLayer.push(arguments);
    };
    w.gtag('consent', 'default', {
      analytics_storage: 'granted', ad_storage: 'denied', ad_user_data: 'denied', ad_personalization: 'denied',
    });
    w.gtag('js', new Date());
    w.gtag('config', id, { send_page_view: false, allow_google_signals: false, allow_ad_personalization_signals: false });
    const s = document.createElement('script');
    s.async = true;
    s.src = `https://www.googletagmanager.com/gtag/js?id=${encodeURIComponent(id)}`;
    document.head.appendChild(s);
    this.pageView();
  }

  private pageView(): void {
    if (!this.loaded || this.consent() !== 'granted') return;
    // reset links carry a secret token: send only the path
    track('page_view', { page_location: location.origin + location.pathname, page_title: document.title });
  }
}
