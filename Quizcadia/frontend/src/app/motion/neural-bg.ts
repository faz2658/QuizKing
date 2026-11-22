import { afterNextRender, ChangeDetectionStrategy, Component, DestroyRef, effect, ElementRef, inject, InjectionToken, signal, viewChild } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { Theme } from '../core/theme';
import type { NeuralScene, RGB, SceneColors, SceneFactory, ScenePose } from './neural-scene';
import { prefersReducedMotion } from './reduced-motion';

/** How the scene code is fetched: a separate chunk, loaded the first time the welcome page shows. Tests swap it. */
export const NEURAL_SCENE_LOADER = new InjectionToken<() => Promise<SceneFactory>>('NEURAL_SCENE_LOADER', {
  factory: () => () => import('./neural-scene').then((m) => m.createNeuralScene),
});

/**
 * 'loading' until the scene is up; 'live' animates; 'still' is one frame per page for reduced motion;
 * 'fallback' hides the canvas so the CSS {@code Aurora} backdrop shows on its own (no WebGL).
 */
export type NeuralMode = 'loading' | 'live' | 'still' | 'fallback';

/**
 * hero: the welcome page, where the brain lives. Every other page has its own motion (camera moves between
 * pages, the Day/Night relight), so there the brain drifts off in the direction of the page and fades away;
 * once it has gone the scene stops drawing.
 */
export type PoseName = 'hero' | 'browse' | 'game' | 'quiet';

const GONE = { alpha: 0, spin: 0, lean: 0 };
/** At most about 60 frames a second (a little under 16.7 ms, so a 60 Hz screen never skips one). */
export const MIN_FRAME_MS = 15;

/** Where the camera looks for each pose: the brain on the sign-in page, and moved aside and faded out elsewhere. */
export const POSES: Record<PoseName, ScenePose> = {
  hero: { x: 0, y: 0, zoom: 1, alpha: 0.9, spin: 1, lean: 1 },
  browse: { x: 1.4, y: 0.1, zoom: 1.6, ...GONE },
  game: { x: 0, y: 0, zoom: 2.2, ...GONE },
  quiet: { x: -1.4, y: -0.2, zoom: 1.6, ...GONE },
};

const GAME = /^\/(daily|play\/|adaptive\/|match\/|room\/|multiplayer\/|custom\/play|challenge\/[^/]+\/play|vs\/[^/]+\/play|party\/|ai-quiz\/|know-me\/(?!new)|puzzles)/;
const QUIET = /^\/(admin|privacy|terms|forgot-password|reset-password)/;

/** Which framing a page gets, from its URL. */
export function poseFor(url: string): PoseName {
  const path = url.split(/[?#]/)[0];
  if (path.startsWith('/welcome')) return 'hero';
  if (GAME.test(path)) return 'game';
  if (QUIET.test(path)) return 'quiet';
  return 'browse';
}

function hex(value: string, fallback: RGB): RGB {
  const m = /^#?([0-9a-f]{6})$/i.exec(value.trim());
  if (!m) return fallback;
  const n = parseInt(m[1], 16);
  return [(n >> 16) / 255, ((n >> 8) & 255) / 255, (n & 255) / 255];
}

/** The scene's colours, taken from the theme's CSS variables so it follows Day and Night. */
function themeColors(light: boolean): SceneColors {
  const css = getComputedStyle(document.documentElement);
  const v = (name: string, fallback: RGB) => hex(css.getPropertyValue(name), fallback);
  return {
    from: v('--accent-2', [0.49, 0.42, 1]),
    via: v('--lavender', [0.77, 0.71, 1]),
    to: v('--pink', [1, 0.48, 0.71]),
    signal: v('--accent', [1, 0.71, 0.28]),
    link: v('--lavender', [0.77, 0.71, 1]),
    light,
  };
}

/**
 * The site-wide 3D backdrop: a glowing neural "brain" over the {@code Aurora} colours. Each kind of page frames
 * it differently and the camera glides between framings, with a swing of the brain, on every page change. It
 * holds still during games, pauses while the tab is hidden and frees its GL context when the app goes.
 */
@Component({
  selector: 'mv-neural-bg',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<canvas #canvas></canvas>`,
  host: { 'aria-hidden': 'true', '[attr.data-mode]': 'mode()', '[attr.data-pose]': 'pose()' },
  styles: `
    :host { position: fixed; inset: 0; z-index: -1; pointer-events: none; }
    canvas {
      position: absolute; inset: 0; width: 100%; height: 100%; display: block;
      opacity: 0; transition: opacity 1.4s ease;
      mask-image: radial-gradient(ellipse 95% 85% at 50% 45%, #000 45%, transparent 92%);
    }
    :host([data-mode='live']) canvas, :host([data-mode='still']) canvas { opacity: 1; }
    :host([data-mode='fallback']) canvas { display: none; }
    @media (prefers-reduced-motion: reduce) { canvas { transition: none; } }
  `,
})
export class NeuralBg {
  private readonly load = inject(NEURAL_SCENE_LOADER);
  private readonly router = inject(Router);
  private readonly theme = inject(Theme);
  private readonly canvas = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');
  readonly mode = signal<NeuralMode>('loading');
  readonly pose = signal<PoseName>(poseFor(this.router.url));

  private scene: NeuralScene | null = null;
  private small = false;
  private frame = 0;
  private time = 0;
  private last = 0;
  private pointer = [0, 0];
  private destroyed = false;
  private started = false;
  private afterFirstRender = false;
  private readonly cleanups: (() => void)[] = [];

  constructor() {
    inject(DestroyRef).onDestroy(() => this.destroy());
    const sub = this.router.events.subscribe((e) => e instanceof NavigationEnd && this.go(poseFor(e.urlAfterRedirects)));
    this.cleanups.push(() => sub.unsubscribe());
    // nothing is downloaded or drawn until the welcome page shows
    afterNextRender(() => {
      this.afterFirstRender = true;
      if (this.pose() === 'hero') void this.start();
    });
    effect(() => {
      const light = this.theme.current() === 'light';
      if (!this.scene) return;
      this.scene.setColors(themeColors(light));
      this.wake();
    });
  }

  private async start(): Promise<void> {
    if (this.destroyed || this.started) return;
    this.started = true;
    if (typeof WebGLRenderingContext === 'undefined') {
      this.mode.set('fallback');
      return;
    }
    let factory: SceneFactory;
    try {
      factory = await this.load();
    } catch {
      this.mode.set('fallback');
      return;
    }
    if (this.destroyed) return;
    const canvas = this.canvas().nativeElement;
    this.small = Math.min(window.innerWidth, window.innerHeight) < 600 || !!window.matchMedia?.('(pointer: coarse)').matches;
    this.scene = factory(canvas, { small: this.small, colors: themeColors(this.theme.current() === 'light') });
    if (!this.scene) {
      this.mode.set('fallback');
      return;
    }
    this.scene.setPose(this.target(this.pose()), 0, true);
    this.listen(canvas, 'webglcontextlost', (e) => {
      e.preventDefault();
      this.stop();
      this.scene = null;
      this.mode.set('fallback');
    });
    this.listen(window, 'resize', () => {
      this.scene?.resize();
      this.wake();
    });
    this.listen(window, 'pointermove', (e) => {
      const p = e as PointerEvent;
      this.pointer = [(p.clientX / window.innerWidth) * 2 - 1, (p.clientY / window.innerHeight) * 2 - 1];
      if (POSES[this.pose()].lean) this.wake();
    });
    this.listen(document, 'visibilitychange', () => this.wake());
    const motion = window.matchMedia?.('(prefers-reduced-motion: reduce)');
    const onMotion = () => this.wake();
    motion?.addEventListener?.('change', onMotion);
    this.cleanups.push(() => motion?.removeEventListener?.('change', onMotion));
    this.wake();
  }

  /** On phones the brain sits behind the content rather than beside it, so it stays dimmer there. */
  private target(name: PoseName): ScenePose {
    const p = POSES[name];
    return this.small && name !== 'hero' ? { ...p, alpha: p.alpha * 0.7 } : p;
  }

  /** A page change: glide to the new framing with a swing of the brain. */
  private go(name: PoseName): void {
    this.pose.set(name);
    if (name === 'hero' && !this.started && this.afterFirstRender) void this.start();
    if (!this.scene) return;
    const still = prefersReducedMotion();
    this.scene.setPose(this.target(name), still ? 0 : 1.4, still);
    this.wake();
  }

  /** Runs, pauses or freezes the animation for the current motion setting, tab and pose. */
  private wake(): void {
    if (!this.scene) return;
    if (prefersReducedMotion()) {
      this.stop();
      this.mode.set('still');
      this.scene.setPose(this.target(this.pose()), 0, true);
      this.scene.render(8, 0, 0);
      return;
    }
    this.mode.set('live');
    if (document.hidden) this.stop();
    else if (!this.frame) {
      this.last = performance.now();
      this.frame = requestAnimationFrame(this.tick);
    }
  }

  private readonly tick = (now: number) => {
    // 120 Hz screens ask for a frame every 8 ms; the slow spin looks the same at 60 fps for half the work
    if (now - this.last < MIN_FRAME_MS) {
      this.frame = requestAnimationFrame(this.tick);
      return;
    }
    // a long pause (hidden tab) doesn't make the brain jump ahead
    this.time += Math.min(now - this.last, 50) / 1000;
    this.last = now;
    const settled = this.scene?.render(this.time, this.pointer[0], this.pointer[1]) ?? true;
    // a pose that holds still needs no more frames once the camera has arrived
    this.frame = settled && POSES[this.pose()].spin === 0 ? 0 : requestAnimationFrame(this.tick);
  };

  private stop(): void {
    if (this.frame) cancelAnimationFrame(this.frame);
    this.frame = 0;
  }

  private listen(target: EventTarget, type: string, fn: (e: Event) => void): void {
    target.addEventListener(type, fn, { passive: type !== 'webglcontextlost' });
    this.cleanups.push(() => target.removeEventListener(type, fn));
  }

  private destroy(): void {
    this.destroyed = true;
    this.stop();
    this.cleanups.splice(0).forEach((c) => c());
    this.scene?.dispose();
    this.scene = null;
  }
}
