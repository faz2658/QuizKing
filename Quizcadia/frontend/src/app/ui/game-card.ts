import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { GameCard } from '../core/models';
import { Tilt } from '../motion/tilt';

const PALETTES = [
  ['#7c6cff', '#ff7ab6'], ['#ff7ab6', '#ffb547'], ['#28c2ff', '#7c6cff'],
  ['#ffb547', '#ff6b81'], ['#3ddc97', '#28c2ff'], ['#c86bff', '#ff7ab6'],
];

const REASONS: Record<string, string> = {
  FAVORITE_CATEGORY: 'From your favourite category',
  PLAYERS_LIKE_YOU: 'Players like you enjoyed this',
  POPULAR: 'Popular right now',
  PLAY_AGAIN: 'Play again',
};

/** A game's card with its play buttons. */
@Component({
  selector: 'mv-game-card',
  imports: [RouterLink, Tilt],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <article class="game card spotlight" mvTilt="7">
      <div class="art" [style.background]="art()" aria-hidden="true">
        <span class="initials">{{ initials() }}</span>
        <span class="sheen"></span>
      </div>
      <div class="body">
        <div class="meta">
          <span class="chip">{{ game().mode === 'MULTI' ? 'Multiplayer' : 'Solo' }}</span>
          @if (game().questionCount) { <span class="muted count">{{ game().questionCount }} questions</span> }
        </div>
        @if (reason()) { <span class="reason">{{ reason() }}</span> }
        <h3>{{ game().gameName }}</h3>
        @if (game().gameDescription) { <p class="muted">{{ game().gameDescription }}</p> }
        <div class="actions">
          @if (international()) {
            <a class="btn btn-primary" [routerLink]="['/play', game().gameId]" [queryParams]="{ intl: 1 }">Play</a>
            <a class="btn" routerLink="/vs/new" [queryParams]="{ game: game().gameId, intl: 1 }" title="Challenge a friend to this game">⚔️ Challenge</a>
          } @else if (game().mode === 'MULTI') {
            <a class="btn btn-primary" [routerLink]="['/multiplayer', game().gameId]">Find opponents</a>
          } @else {
            <a class="btn btn-primary" [routerLink]="['/play', game().gameId]">Play</a>
            <a class="btn" [routerLink]="['/adaptive', game().gameId]" title="Difficulty follows how well you do">Adaptive</a>
            <a class="btn" routerLink="/vs/new" [queryParams]="{ game: game().gameId }" title="Challenge a friend to this game" aria-label="Challenge a friend to this game">⚔️</a>
          }
        </div>
      </div>
    </article>
  `,
  styles: `
    :host { display: block; height: 100%; }
    .game { display: flex; flex-direction: column; height: 100%; padding: 0; overflow: hidden; }
    .art { position: relative; height: 120px; display: grid; place-items: center; overflow: hidden; }
    .initials { font-size: 2.8rem; font-weight: 900; letter-spacing: -0.04em; color: rgba(255,255,255,0.95); text-shadow: 0 4px 24px rgba(0,0,0,0.25); }
    .sheen { position: absolute; inset: 0; background: linear-gradient(115deg, transparent 35%, rgba(255,255,255,0.35) 50%, transparent 65%); transform: translateX(-120%); transition: transform 0.8s ease; }
    .game:hover .sheen { transform: translateX(120%); }
    .body { padding: 18px 20px 20px; display: flex; flex-direction: column; gap: 6px; flex: 1; }
    .meta { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
    .count { font-size: 0.8rem; font-variant-numeric: tabular-nums; }
    h3 { font-size: 1.2rem; margin: 4px 0 0; letter-spacing: -0.02em; }
    p { margin: 0; font-size: 0.9rem; }
    .reason { font-size: 0.72rem; font-weight: 750; color: var(--pink); text-transform: uppercase; letter-spacing: 0.08em; }
    .actions { display: flex; gap: 8px; margin-top: auto; padding-top: 14px; }
    .actions .btn { padding: 9px 18px; }
  `,
})
export class GameCardView {
  readonly game = input.required<GameCard>();
  /** On the International page: one Play button that asks questions from every country. */
  readonly international = input(false);

  protected readonly art = computed(() => {
    const [a, b] = PALETTES[Math.abs(this.game().gameId) % PALETTES.length];
    return `radial-gradient(circle at 20% 0%, rgba(255,255,255,0.25), transparent 50%), linear-gradient(135deg, ${a}, ${b})`;
  });
  protected readonly initials = computed(() =>
    this.game().gameName.split(/\s+/).filter((w) => /^[\p{L}\p{N}]/u.test(w)).slice(0, 2).map((w) => w[0].toUpperCase()).join(''),
  );
  protected readonly reason = computed(() => (this.game().reason ? REASONS[this.game().reason!] : null) ?? null);
}
