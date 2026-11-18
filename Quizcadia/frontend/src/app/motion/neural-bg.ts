import { afterNextRender, ChangeDetectionStrategy, Component, DestroyRef, ElementRef, inject, InjectionToken, signal, viewChild } from '@angular/core';
import type { NeuralScene, RGB, SceneColors, SceneFactory } from './neural-scene';
import { prefersReducedMotion } from './reduced-motion';

/** How the scene code is fetched: a separate chunk, so only the welcome page downloads it. Tests swap it. */
export const NEURAL_SCENE_LOADER = new InjectionToken<() => Promise<SceneFactory>>('NEURAL_SCENE_LOADER', {
  factory: () => () => import('./neural-scene').then((m) => m.createNeuralScene),
});

/**
 * 'loading' until the scene is up; 'live' animates; 'still' is one frame for reduced motion;
 * 'fallback' hides the canvas so the CSS {@code Aurora} backdrop shows on its own (no WebGL).
 */
export type NeuralMode = 'loading' | 'live' | 'still' | 'fallback';

function hex(value: string, fallback: RGB): RGB {
  const m = /^#?([0-9a-f]{6})$/i.exec(value.trim());
  if (!m) return fallback;
  const n = parseInt(m[1], 16);
  return [(n >> 16) / 255, ((n >> 8) & 255) / 255, (n & 255) / 255];
}

/** The scene's colours, taken from the theme's CSS variables so it follows light and dark. */
function themeColors(): SceneColors {
  const css = getComputedStyle(document.documentElement);
  const v = (name: string, fallback: RGB) => hex(css.getPropertyValue(name), fallback);
  return {
    from: v('--accent-2', [0.49, 0.42, 1]),
    via: v('--lavender', [0.77, 0.71, 1]),
    to: v('--pink', [1, 0.48, 0.71]),
    signal: v('--accent', [1, 0.71, 0.28]),
    link: v('--lavender', [0.77, 0.71, 1]),
    light: !!window.matchMedia?.('(prefers-color-scheme: light)').matches,
  };
}

/**
 * A slowly turning, glowing neural "brain" behind the welcome hero. It pauses while the tab is hidden or the
 * hero is scrolled away, leans gently towards the pointer and cleans up its GL context when the page goes.
 */
@Component({
  selector: 'mv-neural-bg',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<canvas #canvas></canvas>`,
  host: { 'aria-hidden': 'true', '[attr.data-mode]': 'mode()', '[class.away]': 'away()' },
  styles: `
    :host { position: absolute; inset: 0 0 auto 0; height: 100vh; height: 100svh; z-index: -1; pointer-events: none; }
    canvas {
      position: fixed; inset: 0; width: 100%; height: 100%; display: block;
      opacity: 0; transition: opacity 1.4s ease;
      /* a soft vignette, quieter in the middle where the headline sits */
      mask-image: radial-gradient(ellipse 70% 62% at 50% 46%, rgba(0, 0, 0, 0.72) 0%, #000 45%, transparent 78%);
    }
    :host([data-mode='live']) canvas, :host([data-mode='still']) canvas { opacity: 1; }
    :host(.away) canvas { opacity: 0; transition-duration: 0.5s; }
    :host([data-mode='fallback']) canvas { display: none; }
    @media (prefers-reduced-motion: reduce) { canvas { transition: none; } }
  `,
})
export class NeuralBg {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly load = inject(NEURAL_SCENE_LOADER);
  private readonly canvas = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');
  readonly mode = signal<NeuralMode>('loading');
  /** The hero has scrolled out of view: the canvas fades out and stops drawing. */
  protected readonly away = signal(false);

  private scene: NeuralScene | null = null;
  private frame = 0;
  private time = 0;
  private last = 0;
  private pointer = [0, 0];
  private destroyed = false;
  private readonly cleanups: (() => void)[] = [];

  constructor() {
    inject(DestroyRef).onDestroy(() => this.destroy());
    afterNextRender(() => void this.start());
  }

  private async start(): Promise<void> {
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
    const small = Math.min(window.innerWidth, window.innerHeight) < 600 || !!window.matchMedia?.('(pointer: coarse)').matches;
    this.scene = factory(canvas, { small, colors: themeColors() });
    if (!this.scene) {
      this.mode.set('fallback');
      return;
    }
    this.listen(canvas, 'webglcontextlost', (e) => {
      e.preventDefault();
      this.stop();
      this.scene = null;
      this.mode.set('fallback');
    });
    this.listen(window, 'resize', () => {
      this.scene?.resize();
      if (this.mode() === 'still') this.drawStill();
    });
    this.listen(window, 'pointermove', (e) => {
      const p = e as PointerEvent;
      this.pointer = [(p.clientX / window.innerWidth) * 2 - 1, (p.clientY / window.innerHeight) * 2 - 1];
    });
    this.listen(document, 'visibilitychange', () => this.sync());
    for (const [query, on] of [
      ['(prefers-reduced-motion: reduce)', () => this.sync()],
      ['(prefers-color-scheme: light)', () => {
        this.scene?.setColors(themeColors());
        if (this.mode() === 'still') this.drawStill();
      }],
    ] as const) {
      const mq = window.matchMedia?.(query);
      mq?.addEventListener?.('change', on);
      this.cleanups.push(() => mq?.removeEventListener?.('change', on));
    }
    if (typeof IntersectionObserver !== 'undefined') {
      const io = new IntersectionObserver(([entry]) => {
        this.away.set(entry.intersectionRatio < 0.3);
        this.sync();
      }, { threshold: [0, 0.3, 0.6] });
      io.observe(this.host.nativeElement);
      this.cleanups.push(() => io.disconnect());
    }
    this.sync();
  }

  /** Runs, pauses or freezes the animation for the current motion setting, tab and scroll state. */
  private sync(): void {
    if (!this.scene) return;
    if (prefersReducedMotion()) {
      this.stop();
      this.mode.set('still');
      this.drawStill();
      return;
    }
    this.mode.set('live');
    if (document.hidden || this.away()) this.stop();
    else if (!this.frame) {
      this.last = performance.now();
      this.frame = requestAnimationFrame(this.tick);
    }
  }

  private readonly tick = (now: number) => {
    // a long pause (hidden tab) doesn't make the brain jump ahead
    this.time += Math.min(now - this.last, 50) / 1000;
    this.last = now;
    this.scene?.render(this.time, this.pointer[0], this.pointer[1]);
    this.frame = requestAnimationFrame(this.tick);
  };

  /** Reduced motion: one fixed, slightly turned view; no drift, no pointer lean. */
  private drawStill(): void {
    this.scene?.render(8, 0, 0);
  }

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
