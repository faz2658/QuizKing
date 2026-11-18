import { computed, Injectable, signal } from '@angular/core';
import { prefersReducedMotion } from '../motion/reduced-motion';

export type ThemeName = 'light' | 'dark';
/** 'system' follows the device's light/dark setting until the player picks Day or Night. */
export type ThemeChoice = ThemeName | 'system';

const STORAGE_KEY = 'quizcadia.theme';
/** Class on <html> while the site relights; styles.scss runs the sweep only then. */
export const THEME_TRANSITION_CLASS = 'theme-vt';

function storedChoice(): ThemeChoice {
  try {
    const v = globalThis.localStorage?.getItem(STORAGE_KEY);
    return v === 'light' || v === 'dark' ? v : 'system';
  } catch {
    return 'system';
  }
}

/** Sets the data-theme attribute styles.scss reads; 'system' leaves it off so the media query decides. */
export function applyTheme(choice: ThemeChoice = storedChoice()): void {
  const root = globalThis.document?.documentElement;
  if (!root) return;
  if (choice === 'system') root.removeAttribute('data-theme');
  else root.setAttribute('data-theme', choice);
}

/**
 * Day or Night for the whole site. Switching relights the page with a sweep: sunrise rising from the bottom
 * for Day, dusk falling from the top for Night, with a band of haze along the edge (keyframes in styles.scss).
 */
@Injectable({ providedIn: 'root' })
export class Theme {
  readonly choice = signal<ThemeChoice>(storedChoice());
  private readonly systemLight = signal(this.mediaLight());
  /** The theme on screen now. */
  readonly current = computed<ThemeName>(() => {
    const c = this.choice();
    return c === 'system' ? (this.systemLight() ? 'light' : 'dark') : c;
  });

  constructor() {
    applyTheme(this.choice());
    const mq = globalThis.matchMedia?.('(prefers-color-scheme: light)');
    mq?.addEventListener?.('change', (e) => this.systemLight.set(e.matches));
  }

  set(next: ThemeName): void {
    if (next === this.current() && this.choice() !== 'system') return;
    const update = () => {
      this.choice.set(next);
      applyTheme(next);
      try {
        localStorage.setItem(STORAGE_KEY, next);
      } catch {
        // storage blocked: the choice lasts until the tab closes
      }
    };
    const doc = document as Document & { startViewTransition?: (cb: () => void) => { finished: Promise<void> } };
    if (next === this.current() || prefersReducedMotion() || !doc.startViewTransition) {
      update();
      return;
    }
    const root = document.documentElement;
    root.classList.add(THEME_TRANSITION_CLASS, `theme-to-${next}`);
    const fog = document.createElement('div');
    fog.className = 'theme-fog';
    fog.setAttribute('aria-hidden', 'true');
    const t = doc.startViewTransition(() => {
      update();
      document.body.appendChild(fog);
    });
    t.finished.finally(() => {
      fog.remove();
      root.classList.remove(THEME_TRANSITION_CLASS, 'theme-to-light', 'theme-to-dark');
    });
  }

  private mediaLight(): boolean {
    return !!globalThis.matchMedia?.('(prefers-color-scheme: light)').matches;
  }
}
