import type { ViewTransitionInfo } from '@angular/router';
import { prefersReducedMotion } from './reduced-motion';

/** Class on <html> while a page change animates; styles.scss gives <main> its view-transition-name only then. */
export const PAGE_TRANSITION_CLASS = 'page-vt';

/**
 * Router hook for every page change: the old page sinks back in 3D and the new one comes forward (keyframes in
 * styles.scss). <main> is named only for the transition, so its glass cards keep blurring the backdrop the rest
 * of the time. Reduced motion skips the animation and the page swaps at once.
 */
export function onPageTransition({ transition }: ViewTransitionInfo): void {
  if (prefersReducedMotion()) {
    transition.skipTransition();
    return;
  }
  const root = document.documentElement;
  root.classList.add(PAGE_TRANSITION_CLASS);
  transition.finished.finally(() => root.classList.remove(PAGE_TRANSITION_CLASS));
}
