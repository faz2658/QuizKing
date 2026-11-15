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

  @HostListener('pointermove', ['$event'])
  protected move(e: PointerEvent): void {
    if (e.pointerType === 'touch') {
      return;
    }
    const r = this.el.getBoundingClientRect();
    const x = (e.clientX - r.left) / r.width;
    const y = (e.clientY - r.top) / r.height;
    this.el.style.setProperty('--mx', `${(x * 100).toFixed(1)}%`);
    this.el.style.setProperty('--my', `${(y * 100).toFixed(1)}%`);
    if (!prefersReducedMotion()) {
      const max = this.mvTilt();
      this.el.style.transform = `perspective(900px) rotateX(${((0.5 - y) * max).toFixed(2)}deg) rotateY(${((x - 0.5) * max).toFixed(2)}deg)`;
    }
  }

  @HostListener('pointerleave')
  protected leave(): void {
    this.el.style.transform = '';
  }
}
