import { ChangeDetectionStrategy, Component, DestroyRef, effect, inject, input, signal, untracked } from '@angular/core';
import { prefersReducedMotion } from './reduced-motion';

/** A number that rolls from its previous value to the new one (ease-out). */
@Component({
  selector: 'mv-count-up',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `{{ format(display()) }}{{ suffix() }}`,
  host: { class: 'count-up' },
  styles: `:host { font-variant-numeric: tabular-nums; }`,
})
export class CountUp {
  readonly value = input.required<number>();
  readonly duration = input(900);
  readonly suffix = input('');

  protected readonly display = signal(0);
  private readonly fmt = new Intl.NumberFormat();

  protected format(n: number): string {
    return this.fmt.format(n);
  }
  private frame = 0;

  constructor() {
    inject(DestroyRef).onDestroy(() => cancelAnimationFrame(this.frame));
    effect(() => {
      const to = this.value();
      untracked(() => this.animate(this.display(), to));
    });
  }

  private animate(from: number, to: number): void {
    cancelAnimationFrame(this.frame);
    if (prefersReducedMotion() || from === to) {
      this.display.set(to);
      return;
    }
    const start = performance.now();
    const d = this.duration();
    const tick = (now: number) => {
      const t = Math.min(1, (now - start) / d);
      const eased = 1 - Math.pow(1 - t, 3);
      this.display.set(Math.round(from + (to - from) * eased));
      if (t < 1) {
        this.frame = requestAnimationFrame(tick);
      }
    };
    this.frame = requestAnimationFrame(tick);
  }
}
