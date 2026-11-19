import { Directive, ElementRef, HostListener, inject, input } from '@angular/core';
import { prefersReducedMotion } from './reduced-motion';

/**
 * Tilts the element toward the pointer and exposes the pointer position as --mx / --my (percent),
 * which card styles use for a spotlight glow. Touch and reduced-motion users get a flat card.
 */
@Directive({ selector: '[mvTilt]', host: { class: 'tilt' } })
export class Tilt {
  /** Maximum tilt in degrees. */
  readonly mvTilt = input(8, { transform: (v: number | string) => (v === '' ? 8 : Number(v)) });
  private readonly el = inject(ElementRef<HTMLElement>).nativeElement as HTMLElement;
  /** The newest pointer position, applied once per frame: pointermove can fire faster than the screen draws. */
  private pending: { x: number; y: number } | null = null;
  private frame = 0;

  @HostListener('pointermove', ['$event'])
  protected move(e: PointerEvent): void {
    if (e.pointerType === 'touch') {
      return;
    }
    this.pending = { x: e.clientX, y: e.clientY };
    if (!this.frame) this.frame = requestAnimationFrame(() => this.apply());
  }

  private apply(): void {
    this.frame = 0;
    const p = this.pending;
    this.pending = null;
    if (!p) return;
    const r = this.el.getBoundingClientRect();
    const x = (p.x - r.left) / r.width;
    const y = (p.y - r.top) / r.height;
    this.el.style.setProperty('--mx', `${(x * 100).toFixed(1)}%`);
    this.el.style.setProperty('--my', `${(y * 100).toFixed(1)}%`);
    if (!prefersReducedMotion()) {
      const max = this.mvTilt();
      this.el.style.transform = `perspective(900px) rotateX(${((0.5 - y) * max).toFixed(2)}deg) rotateY(${((x - 0.5) * max).toFixed(2)}deg)`;
    }
  }

  @HostListener('pointerleave')
  protected leave(): void {
    if (this.frame) cancelAnimationFrame(this.frame);
    this.frame = 0;
    this.pending = null;
    this.el.style.transform = '';
  }
}
