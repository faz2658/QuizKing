/** Class on <html> on every page except the login page: all animations and transitions are off there. */
export const STILL_CLASS = 'still';

/** The login page (/welcome) keeps its animations; every other page is still. */
export function isLoginPage(url: string): boolean {
  return /^\/welcome(?:[/?#]|$)/.test(url);
}

/** Turns motion off or back on for the page at {@code url} (called as each navigation starts). */
export function syncStill(url: string, root: HTMLElement = document.documentElement): void {
  root.classList.toggle(STILL_CLASS, !isLoginPage(url));
}

/**
 * True when nothing should move: the player asked their system for less motion, or this isn't the login page.
 * Every effect checks this (tilt, count-up, sparks, the Day/Night relight, page changes, the background brain).
 */
export function prefersReducedMotion(): boolean {
  if (typeof window === 'undefined') return false;
  return !!window.matchMedia?.('(prefers-reduced-motion: reduce)').matches || document.documentElement.classList.contains(STILL_CLASS);
}
