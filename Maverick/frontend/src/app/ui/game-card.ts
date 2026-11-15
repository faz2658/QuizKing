import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { GameCard } from '../core/models';

const PALETTES = [
  ['#7c6cff', '#3ddc97'], ['#ff6b81', '#ffb547'], ['#28c2ff', '#7c6cff'],
  ['#ffb547', '#ff6b81'], ['#3ddc97', '#28c2ff'], ['#c86bff', '#ff6b81'],
];

const REASONS: Record<string, string> = {
  FAVORITE_CATEGORY: 'From your favourite category',
  PLAYERS_LIKE_YOU: 'Players like you enjoyed this',
  POPULAR: 'Popular right now',
  PLAY_AGAIN: 'Play again',
};

@Component({
  selector: 'mv-game-card',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <article class="game">
      <div class="art" [style.background]="art()" aria-hidden="true"><span>{{ initials() }}</span></div>
      <div class="body">
        @if (reason()) { <span class="reason">{{ reason() }}</span> }
        <h3>{{ game().gameName }}</h3>
        @if (game().gameDescription) { <p class="muted">{{ game().gameDescription }}</p> }
        <div class="facts muted">
          @if (game().topic) { <span>{{ game().topic }}</span> }
          @if (game().questionCount) { <span>{{ game().questionCount }} questions</span> }
        </div>
        <div class="actions">
          @if (game().mode === 'MULTI') {
            <a class="btn btn-primary" [routerLink]="['/multiplayer', game().gameId]">Find opponents</a>
          } @else {
            <a class="btn btn-primary" [routerLink]="['/play', game().gameId]">Play</a>
            <a class="btn" [routerLink]="['/adaptive', game().gameId]" title="Difficulty follows how well you do">Adaptive</a>
          }
        </div>
      </div>
    </article>
  `,
  styles: `
    .game { display: flex; flex-direction: column; height: 100%; background: var(--bg-raised); border: 1px solid var(--border); border-radius: var(--radius-lg); overflow: hidden; box-shadow: var(--shadow); transition: transform 0.15s, border-color 0.15s; }
    .game:hover { transform: translateY(-3px); border-color: var(--accent-2); }
    .art { height: 110px; display: grid; place-items: center; }
    .art span { font-size: 2.4rem; font-weight: 900; color: rgba(255,255,255,0.92); letter-spacing: 0.04em; text-shadow: 0 2px 12px rgba(0,0,0,0.25); }
    .body { padding: 18px; display: flex; flex-direction: column; gap: 6px; flex: 1; }
    h3 { font-size: 1.15rem; margin: 0; }
    p { margin: 0; font-size: 0.92rem; }
    .facts { display: flex; gap: 12px; font-size: 0.82rem; }
    .reason { font-size: 0.75rem; font-weight: 750; color: var(--accent); text-transform: uppercase; letter-spacing: 0.05em; }
    .actions { display: flex; gap: 8px; margin-top: auto; padding-top: 12px; }
  `,
})
export class GameCardView {
  readonly game = input.required<GameCard>();

  protected readonly art = computed(() => {
    const [a, b] = PALETTES[Math.abs(this.game().gameId) % PALETTES.length];
    return `linear-gradient(135deg, ${a}, ${b})`;
  });
  protected readonly initials = computed(() =>
    this.game().gameName.split(/\s+/).filter(Boolean).slice(0, 2).map((w) => w[0]?.toUpperCase()).join(''),
  );
  protected readonly reason = computed(() => (this.game().reason ? REASONS[this.game().reason!] : null) ?? null);
}
