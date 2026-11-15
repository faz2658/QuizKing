import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/** Circular countdown. Turns amber, then red, as time runs out. */
@Component({
  selector: 'mv-timer-ring',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg viewBox="0 0 100 100" [attr.aria-label]="seconds() + ' seconds left'" role="timer">
      <circle class="track" cx="50" cy="50" r="44" />
      <circle class="bar" [class.warn]="fraction() <= 0.5" [class.danger]="fraction() <= 0.25"
        cx="50" cy="50" r="44" [attr.stroke-dasharray]="circumference"
        [attr.stroke-dashoffset]="offset()" />
    </svg>
    <span class="value" aria-hidden="true">{{ seconds() }}</span>
  `,
  styles: `
    :host { position: relative; display: inline-grid; place-items: center; width: var(--size, 84px); aspect-ratio: 1; }
    svg { position: absolute; inset: 0; transform: rotate(-90deg); }
    circle { fill: none; stroke-width: 8; }
    .track { stroke: var(--bg-sunken); }
    .bar { stroke: var(--good); stroke-linecap: round; transition: stroke-dashoffset 0.2s linear, stroke 0.3s; filter: drop-shadow(0 0 6px currentColor); color: var(--good); }
    .bar.warn { stroke: var(--accent); }
    .bar.danger { stroke: var(--bad); }
    .value { font-weight: 800; font-size: calc(var(--size, 84px) * 0.34); font-variant-numeric: tabular-nums; }
  `,
})
export class TimerRing {
  readonly seconds = input.required<number>();
  readonly fraction = input.required<number>();
  protected readonly circumference = 2 * Math.PI * 44;
  protected readonly offset = computed(() => this.circumference * (1 - this.fraction()));
}
