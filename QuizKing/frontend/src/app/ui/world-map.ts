import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { CENTROIDS, countryName, flag } from '../core/countries';

export interface MapPoint {
  country: string;
  players: number;
}

/** [x, y] on a 360 x 180 equirectangular map. */
export function project(lat: number, lon: number): [number, number] {
  return [lon + 180, 90 - lat];
}

/** Glowing dots where players are, on a latitude/longitude grid. */
@Component({
  selector: 'mv-world-map',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <figure class="map">
      <svg viewBox="0 0 360 180" role="img" [attr.aria-label]="label()">
        <defs>
          <radialGradient id="glow"><stop offset="0" stop-color="var(--pink)" stop-opacity="0.9" /><stop offset="1" stop-color="var(--pink)" stop-opacity="0" /></radialGradient>
        </defs>
        @for (x of meridians; track x) { <line [attr.x1]="x" y1="0" [attr.x2]="x" y2="180" class="grid" /> }
        @for (y of parallels; track y) { <line x1="0" [attr.y1]="y" x2="360" [attr.y2]="y" class="grid" [class.equator]="y === 90" /> }
        @for (p of placed(); track p.country) {
          <g [attr.transform]="'translate(' + p.x + ' ' + p.y + ')'">
            <circle [attr.r]="p.r * 2.2" fill="url(#glow)" class="pulse" />
            <circle [attr.r]="p.r" class="dot"><title>{{ p.name }}: {{ p.players }}</title></circle>
          </g>
        }
      </svg>
      <figcaption>
        @for (p of points().slice(0, 8); track p.country) { <span class="chip">{{ flag(p.country) }} {{ p.players }}</span> }
        @if (!points().length) { <span class="muted">Nobody else here yet. Invite a friend!</span> }
      </figcaption>
    </figure>
  `,
  styles: `
    .map { margin: 0; }
    svg { width: 100%; height: auto; border-radius: 18px; background: radial-gradient(ellipse at center, color-mix(in srgb, var(--accent) 12%, var(--bg-sunken)), var(--bg-sunken)); }
    .grid { stroke: color-mix(in srgb, var(--text-muted) 22%, transparent); stroke-width: 0.3; }
    .equator { stroke-dasharray: 2 2; stroke-width: 0.5; }
    .dot { fill: var(--accent); stroke: #fff; stroke-width: 0.5; }
    .pulse { animation: pulse 2.4s ease-in-out infinite; transform-box: fill-box; transform-origin: center; }
    @keyframes pulse { 50% { opacity: 0.35; transform: scale(1.4); } }
    @media (prefers-reduced-motion: reduce) { .pulse { animation: none; } }
    figcaption { display: flex; gap: 6px; flex-wrap: wrap; margin-top: 10px; }
  `,
})
export class WorldMap {
  readonly points = input<MapPoint[]>([]);
  protected readonly flag = flag;
  protected readonly meridians = Array.from({ length: 11 }, (_, i) => 30 + i * 30);
  protected readonly parallels = [30, 60, 90, 120, 150];
  protected readonly placed = computed(() =>
    this.points()
      .filter((p) => CENTROIDS[p.country])
      .map((p) => {
        const [lat, lon] = CENTROIDS[p.country];
        const [x, y] = project(lat, lon);
        return { ...p, x, y, r: Math.min(6, 2 + Math.sqrt(p.players)), name: countryName(p.country) };
      }),
  );
  protected readonly label = computed(() =>
    this.points().length ? 'Players around the world: ' + this.points().map((p) => `${countryName(p.country)} ${p.players}`).join(', ') : 'World map',
  );
}
