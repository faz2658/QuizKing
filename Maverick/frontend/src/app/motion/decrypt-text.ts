import { ChangeDetectionStrategy, Component, DestroyRef, effect, inject, input, signal } from '@angular/core';
import { prefersReducedMotion } from './reduced-motion';

const GLYPHS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789#%&*?';

/**
 * Text that "decrypts": characters flicker through random glyphs and lock in from left to right.
 * Spaces and punctuation stay put so the line doesn't jump. The real text is always in the
 * accessibility tree.
 */
@Component({
  selector: 'mv-decrypt-text',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="sr-only">{{ text() }}</span><span aria-hidden="true">{{ shown() }}</span>`,
})
export class DecryptText {
  readonly text = input.required<string>();
  /** Total reveal time in milliseconds. */
  readonly duration = input(650);

  protected readonly shown = signal('');
  private frame = 0;

  constructor() {
    inject(DestroyRef).onDestroy(() => cancelAnimationFrame(this.frame));
    effect(() => this.run(this.text(), this.duration()));
  }

  private run(target: string, duration: number): void {
    cancelAnimationFrame(this.frame);
    if (prefersReducedMotion() || duration <= 0) {
      this.shown.set(target);
      return;
    }
    const start = performance.now();
    const tick = (now: number) => {
      const progress = Math.min(1, (now - start) / duration);
      const locked = Math.floor(progress * target.length);
      let out = '';
      for (let i = 0; i < target.length; i++) {
        const ch = target[i];
        out += i < locked || !/[A-Za-z0-9]/.test(ch) ? ch : GLYPHS[(Math.random() * GLYPHS.length) | 0];
      }
      this.shown.set(out);
      if (progress < 1) {
        this.frame = requestAnimationFrame(tick);
      }
    };
    this.frame = requestAnimationFrame(tick);
  }
}
