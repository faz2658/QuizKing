import { Injectable } from '@angular/core';
import { prefersReducedMotion } from './reduced-motion';

/** Short particle effects drawn on throwaway DOM nodes (sparks on a correct answer, confetti on a win). */
@Injectable({ providedIn: 'root' })
export class Sparks {
  /** A ring of sparks bursting out of the centre of {@code target}. */
  burst(target: Element, color = 'var(--good)', count = 12): void {
    if (prefersReducedMotion()) {
      return;
    }
    const r = target.getBoundingClientRect();
    const layer = this.layer(r.left + r.width / 2, r.top + r.height / 2);
    for (let i = 0; i < count; i++) {
      const s = document.createElement('i');
      const angle = (i / count) * 360;
      s.style.cssText = `position:absolute;left:0;top:0;width:3px;height:14px;border-radius:3px;background:${color};
        transform:rotate(${angle}deg) translateY(-8px);transform-origin:50% 0;opacity:1;
        transition:transform .5s cubic-bezier(.2,.7,.2,1),opacity .5s ease-out;`;
      layer.appendChild(s);
      requestAnimationFrame(() => {
        s.style.transform = `rotate(${angle}deg) translateY(-${46 + Math.random() * 18}px) scaleY(.4)`;
        s.style.opacity = '0';
      });
    }
    setTimeout(() => layer.remove(), 650);
  }

  /** Confetti falling from the top of the viewport. */
  confetti(pieces = 90): void {
    if (prefersReducedMotion()) {
      return;
    }
    const colors = ['#ffb547', '#ff7ab6', '#c4b5ff', '#7c6cff', '#3ddc97'];
    const layer = this.layer(0, 0);
    layer.style.cssText += 'inset:0;width:100vw;height:100vh;overflow:hidden;';
    for (let i = 0; i < pieces; i++) {
      const p = document.createElement('i');
      const x = Math.random() * 100;
      const size = 6 + Math.random() * 6;
      p.style.cssText = `position:absolute;left:${x}vw;top:-20px;width:${size}px;height:${size * 0.45}px;
        background:${colors[i % colors.length]};border-radius:2px;opacity:.95;
        transition:transform ${1.6 + Math.random() * 1.2}s cubic-bezier(.25,.6,.4,1) ${Math.random() * 0.3}s, opacity .6s ease-in 2s;`;
      layer.appendChild(p);
      requestAnimationFrame(() => {
        p.style.transform = `translate(${(Math.random() - 0.5) * 30}vw, 110vh) rotate(${Math.random() * 900}deg)`;
        p.style.opacity = '0';
      });
    }
    setTimeout(() => layer.remove(), 3400);
  }

  private layer(x: number, y: number): HTMLElement {
    const layer = document.createElement('div');
    layer.setAttribute('aria-hidden', 'true');
    layer.style.cssText = `position:fixed;left:${x}px;top:${y}px;pointer-events:none;z-index:100;`;
    document.body.appendChild(layer);
    return layer;
  }
}
