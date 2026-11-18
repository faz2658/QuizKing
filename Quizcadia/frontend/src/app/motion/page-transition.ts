import type { ActivatedRouteSnapshot, ViewTransitionInfo } from '@angular/router';
import { prefersReducedMotion } from './reduced-motion';

/** Class on <html> while a page change animates; styles.scss names <main> and the nav pill only then. */
export const PAGE_TRANSITION_CLASS = 'page-vt';

/**
 * How the camera moves for a page change, as if the site were one room: 'right' / 'left' pan to the next or
 * previous section's wall, 'in' walks up to a game or detail page, 'out' steps back from it.
 */
export type PageMove = 'right' | 'left' | 'in' | 'out';

/** The walls of the room, in the order the camera passes them (the nav's order, then the public pages). */
const SECTIONS = ['/welcome', '/', '/world', '/leaderboard', '/me', '/admin', '/puzzles', '/events', '/venues', '/privacy', '/terms'];

function clean(url: string): string {
  const path = url.split(/[?#]/)[0].replace(/\/+$/, '');
  return path === '' ? '/' : path;
}

export function pageMove(from: string, to: string): PageMove {
  const a = clean(from);
  const b = clean(to);
  const ia = SECTIONS.indexOf(a);
  const ib = SECTIONS.indexOf(b);
  if (ia >= 0 && ib >= 0) return ib >= ia ? 'right' : 'left';
  if (ib >= 0) return 'out';
  if (ia >= 0) return 'in';
  if (b.startsWith(a + '/')) return 'in';
  if (a.startsWith(b + '/')) return 'out';
  return 'right'; // onwards through a flow, e.g. a game to its results
}

/** The URL path a router state stands for. */
export function pathOf(root: ActivatedRouteSnapshot): string {
  const parts: string[] = [];
  for (let s: ActivatedRouteSnapshot | null = root; s; s = s.firstChild) parts.push(...s.url.map((u) => u.path));
  return '/' + parts.join('/');
}

/**
 * Router hook for every page change: tags <html> with the camera move (data-move) and where the old page's
 * centre of view was, so it turns about what the player was looking at. <main> is named only for the
 * transition, so its glass cards keep blurring the backdrop the rest of the time. Reduced motion skips it.
 */
export function onPageTransition({ transition, from, to }: ViewTransitionInfo): void {
  if (prefersReducedMotion()) {
    transition.skipTransition();
    return;
  }
  const root = document.documentElement;
  const main = document.querySelector('main');
  const top = main ? main.getBoundingClientRect().top : 0;
  root.style.setProperty('--vt-old-y', `${Math.round(window.innerHeight / 2 - top)}px`);
  root.dataset['move'] = pageMove(pathOf(from), pathOf(to));
  root.classList.add(PAGE_TRANSITION_CLASS);
  transition.finished.finally(() => {
    root.classList.remove(PAGE_TRANSITION_CLASS);
    delete root.dataset['move'];
    root.style.removeProperty('--vt-old-y');
  });
}
