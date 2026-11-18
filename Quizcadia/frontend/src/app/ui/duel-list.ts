import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';
import { QuizApi } from '../core/api';
import { DuelView } from '../core/models';

/** What a challenge needs from you, in a few words. */
export function duelStatus(d: DuelView): { text: string; action: boolean } {
  const friend = d.role === 'CHALLENGER' ? d.friend?.name ?? 'a friend' : d.challengerName;
  switch (d.state) {
    case 'WAITING':
      return d.role === 'CHALLENGER' ? { text: 'Waiting for a friend to accept', action: false } : { text: `${friend} challenges you`, action: true };
    case 'DECLINED':
      return { text: d.role === 'CHALLENGER' ? `${friend} declined` : 'Declined', action: false };
    case 'EXPIRED':
      return { text: 'Expired', action: false };
    case 'PLAYING':
      return d.you?.status === 'COMPLETED' ? { text: `Waiting for ${friend} to play`, action: false } : { text: `Your turn vs ${friend}`, action: true };
    case 'FINISHED':
      return {
        text: `${d.outcome === 'WON' ? '🏆 You won' : d.outcome === 'TIE' ? '🤝 Tie' : `${friend} won`} · ${d.you?.score ?? '–'} vs ${d.friend?.score ?? '–'}`,
        action: false,
      };
  }
}

/** Home-page list of the player's challenges, sent and received. Hidden until there is one. */
@Component({
  selector: 'mv-duel-list',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (duels().length) {
      <div class="head"><h2>Challenges</h2><a class="btn btn-ghost" routerLink="/vs/new">⚔️ New challenge</a></div>
      <ul class="list">
        @for (d of duels(); track d.id) {
          @let st = status(d);
          <li>
            <a class="card item" [routerLink]="['/vs', d.id]" [class.todo]="st.action">
              <span class="game"><strong>{{ d.gameName }}</strong>@if (d.international) { <span class="muted"> · International</span> }</span>
              <span class="muted">{{ st.text }}</span>
              @if (st.action) { <span class="go">{{ d.state === 'WAITING' ? 'Answer' : 'Play' }} →</span> }
            </a>
          </li>
        }
      </ul>
    }
  `,
  styles: `
    :host { display: block; }
    .head { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
    h2 { margin: 0; }
    .list { list-style: none; margin: 0 0 32px; padding: 0; display: grid; gap: 10px; }
    .item { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; padding: 12px 16px; color: var(--text); text-decoration: none; }
    .item:hover { border-color: var(--accent); }
    .item.todo { border-color: color-mix(in srgb, var(--accent) 55%, var(--border)); }
    .game { flex: 1 1 200px; }
    .go { margin-left: auto; font-weight: 800; color: var(--accent); white-space: nowrap; }
  `,
})
export class DuelList {
  private readonly api = inject(QuizApi);
  protected readonly duels = toSignal(this.api.duels().pipe(catchError(() => of([] as DuelView[]))), { initialValue: [] as DuelView[] });
  protected readonly status = duelStatus;
}
