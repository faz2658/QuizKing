import { ChangeDetectionStrategy, Component } from '@angular/core';

/** Fixed full-page backdrop: slow-drifting colour blobs under a fading dot grid. Pure CSS, no canvas. */
@Component({
  selector: 'mv-aurora',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="blob a"></div>
    <div class="blob b"></div>
    <div class="blob c"></div>
    <div class="grid"></div>
  `,
  host: { 'aria-hidden': 'true' },
  styles: `
    :host { position: fixed; inset: 0; z-index: -1; overflow: hidden; pointer-events: none; background: var(--bg); }
    .blob { position: absolute; width: 55vmax; height: 55vmax; border-radius: 50%; filter: blur(90px); opacity: 0.38; will-change: transform; }
    .a { background: radial-gradient(circle, var(--accent-2), transparent 65%); top: -22vmax; left: -12vmax; animation: drift-a 26s ease-in-out infinite alternate; }
    .b { background: radial-gradient(circle, var(--pink), transparent 65%); top: -10vmax; right: -20vmax; opacity: 0.26; animation: drift-b 32s ease-in-out infinite alternate; }
    .c { background: radial-gradient(circle, var(--accent), transparent 65%); bottom: -30vmax; left: 25vw; opacity: 0.16; animation: drift-c 38s ease-in-out infinite alternate; }
    .grid {
      position: absolute; inset: 0;
      background-image: radial-gradient(color-mix(in srgb, var(--text) 16%, transparent) 1px, transparent 1.4px);
      background-size: 26px 26px;
      mask-image: radial-gradient(ellipse 80% 60% at 50% 0%, #000 30%, transparent 75%);
    }
    @keyframes drift-a { to { transform: translate(14vw, 10vh) scale(1.15); } }
    @keyframes drift-b { to { transform: translate(-16vw, 14vh) scale(0.9); } }
    @keyframes drift-c { to { transform: translate(-10vw, -12vh) scale(1.2); } }
    @media (prefers-reduced-motion: reduce) { .blob { animation: none; } }
  `,
})
export class Aurora {}
