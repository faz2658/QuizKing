import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ThemeSwitch } from '../ui/theme-switch';
import { applyTheme, Theme, THEME_TRANSITION_CLASS } from './theme';

function media({ reduced = false, light = false } = {}) {
  vi.stubGlobal('matchMedia', (q: string) => ({
    matches: (reduced && q.includes('reduce')) || (light && q.includes('light')),
    media: q, addEventListener() {}, removeEventListener() {},
  }));
}

describe('Theme (Day/Night)', () => {
  const root = document.documentElement;

  beforeEach(() => {
    localStorage.clear();
    root.removeAttribute('data-theme');
  });
  afterEach(() => {
    vi.unstubAllGlobals();
    delete (document as { startViewTransition?: unknown }).startViewTransition;
  });

  it('follows the device until the player picks, then remembers the pick', () => {
    media({ light: true });
    const theme = TestBed.inject(Theme);
    expect(theme.current()).toBe('light');
    expect(root.hasAttribute('data-theme')).toBe(false);

    media({ light: true, reduced: true });
    theme.set('dark');
    expect(theme.current()).toBe('dark');
    expect(root.getAttribute('data-theme')).toBe('dark');
    expect(localStorage.getItem('quizcadia.theme')).toBe('dark');

    root.removeAttribute('data-theme');
    applyTheme(); // what main.ts does on the next visit
    expect(root.getAttribute('data-theme')).toBe('dark');
  });

  it('relights with a sweep and a band of haze, and tidies up afterwards', async () => {
    media();
    let done!: () => void;
    const finished = new Promise<void>((r) => (done = r));
    const start = vi.fn((cb: () => void) => {
      cb();
      return { finished };
    });
    Object.assign(document, { startViewTransition: start });
    const theme = TestBed.inject(Theme);

    theme.set('light');
    expect(start).toHaveBeenCalledOnce();
    expect(root.classList.contains(THEME_TRANSITION_CLASS)).toBe(true);
    expect(root.classList.contains('theme-to-light')).toBe(true);
    expect(root.getAttribute('data-theme')).toBe('light');
    expect(document.querySelector('.theme-fog')).not.toBeNull();

    done();
    await finished;
    await Promise.resolve();
    expect(root.classList.contains(THEME_TRANSITION_CLASS)).toBe(false);
    expect(document.querySelector('.theme-fog')).toBeNull();
  });

  it('switches at once with reduced motion', () => {
    media({ reduced: true });
    const start = vi.fn();
    Object.assign(document, { startViewTransition: start });
    TestBed.inject(Theme).set('light');
    expect(start).not.toHaveBeenCalled();
    expect(root.getAttribute('data-theme')).toBe('light');
  });

  it('the header switch shows the lit half and changes theme', async () => {
    media({ reduced: true });
    const f = TestBed.createComponent(ThemeSwitch);
    await f.whenStable();
    const el = f.nativeElement as HTMLElement;
    const radios = [...el.querySelectorAll<HTMLButtonElement>('[role=radio]')];
    expect(radios.map((b) => b.getAttribute('aria-label'))).toEqual(['Day', 'Night']);
    expect(radios[1].getAttribute('aria-checked')).toBe('true');

    radios[0].click();
    await f.whenStable();
    expect(radios[0].getAttribute('aria-checked')).toBe('true');
    expect(root.getAttribute('data-theme')).toBe('light');
  });
});
