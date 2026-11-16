import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { catchError, of, switchMap } from 'rxjs';
import { BoardsApi } from '../core/api';
import { AuthService } from '../core/auth.service';
import { DailyBoard as Board } from '../core/models';
import { flag } from '../core/countries';

/** One day's challenge ranking, with the player's own row highlighted (and shown even outside the top). */
@Component({
  selector: 'mv-daily-board',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (board(); as b) {
      <section class="card board" aria-label="Daily Challenge leaderboard">
        <div class="row head">
          <h2>Today's leaderboard</h2>
          <span class="spacer"></span>
          <span class="muted">{{ b.players }} {{ b.players === 1 ? 'player' : 'players' }}</span>
        </div>
        @if (b.top.length) {
          <ol>
            @for (e of b.top; track e.userId) {
              <li [class.me]="e.userId === me">
                <span class="rank" [class]="'rank r' + e.rank">{{ e.rank <= 3 ? medals[e.rank - 1] : e.rank }}</span>
                <span class="name">{{ flag(e.country) }} {{ e.name }}{{ e.userId === me ? ' (you)' : '' }}</span>
                <span class="muted">{{ e.correctAnswers }}/{{ e.totalQuestions }}</span>
                <strong>{{ e.score }}</strong>
              </li>
            }
          </ol>
          @if (guest && !b.you) {
            <p class="muted you">Guest runs aren't ranked. Create a free account to get on the board.</p>
          }
          @if (b.you && b.you.rank > b.top.length) {
            <ol [start]="b.you.rank" class="you">
              <li class="me">
                <span class="rank">{{ b.you.rank }}</span><span class="name">You</span>
                <span class="muted">{{ b.you.correctAnswers }}/{{ b.you.totalQuestions }}</span><strong>{{ b.you.score }}</strong>
              </li>
            </ol>
          }
        } @else {
          <p class="muted">Nobody has finished today's challenge yet.{{ guest ? ' Guest runs aren\'t ranked: create a free account to get on the board.' : '' }}</p>
        }
      </section>
    }
  `,
  styles: `
    .board { margin-top: 28px; }
    .head h2 { margin: 0; }
    ol { list-style: none; padding: 0; margin: 14px 0 0; display: grid; gap: 6px; }
    li { display: grid; grid-template-columns: 44px 1fr auto 70px; gap: 12px; align-items: center; padding: 10px 14px; border-radius: 14px; background: var(--bg-sunken); }
    li strong { text-align: right; font-variant-numeric: tabular-nums; }
    li.me { outline: 2px solid var(--accent); background: color-mix(in srgb, var(--accent) 12%, var(--bg-sunken)); }
    .rank { font-weight: 900; text-align: center; font-size: 1.1rem; }
    .name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-weight: 700; }
    .you { margin-top: 10px; border-top: 1px dashed var(--border); padding-top: 10px; }
  `,
})
export class DailyBoard {
  readonly date = input<string | null | undefined>(null);
  private readonly api = inject(BoardsApi);
  protected readonly me = inject(AuthService).user()?.userId;
  protected readonly guest = inject(AuthService).user()?.role === 'GUEST';
  protected readonly medals = ['🥇', '🥈', '🥉'];
  protected readonly flag = flag;
  protected readonly board = toSignal(
    toObservable(this.date).pipe(switchMap((d) => this.api.daily(d ?? undefined).pipe(catchError(() => of(null as Board | null))))),
    { initialValue: null },
  );
}
