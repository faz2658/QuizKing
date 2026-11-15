import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { catchError, combineLatest, map, of, startWith, switchMap } from 'rxjs';
import { BoardsApi, CatalogApi } from '../core/api';
import { AuthService } from '../core/auth.service';
import { errorMessage } from '../core/http-errors';
import { BoardPeriod, Category, XpBoard } from '../core/models';
import { DailyBoard } from '../ui/daily-board';
import { flag } from '../core/countries';

/** XP rankings by period and topic, plus today's Daily Challenge board. */
@Component({
  selector: 'mv-leaderboard',
  imports: [FormsModule, DailyBoard],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="head">
      <div>
        <h1>Leaderboard</h1>
        <p class="muted">Ranked by XP: 10 per correct answer, plus bonuses for perfect games, wins and the Daily Challenge.</p>
      </div>
      <div class="controls">
        <div class="pill-tabs" role="group" aria-label="Period">
          @for (p of periods; track p.id) {
            <button type="button" [class.on]="period() === p.id" [attr.aria-pressed]="period() === p.id" (click)="period.set(p.id)">{{ p.label }}</button>
          }
        </div>
        <label>
          <span class="sr-only">Category</span>
          <select class="input" [ngModel]="categoryId()" (ngModelChange)="categoryId.set($event ? +$event : null)">
            <option [ngValue]="null">All categories</option>
            @for (c of categories(); track c.categoryId) { <option [ngValue]="c.categoryId">{{ c.categoryName }}</option> }
          </select>
        </label>
      </div>
    </section>

    @if (state(); as st) {
      @if (st.error) {
        <div class="alert" role="alert">{{ st.error }}</div>
      } @else if (st.board; as b) {
        @if (b.top.length) {
          <div class="podium">
            @for (e of podium(); track e.userId) {
              <div class="step fade-up" [class]="'step p' + e.rank" [class.me]="e.userId === me">
                <span class="medal" aria-hidden="true">{{ medals[e.rank - 1] }}</span>
                <span class="avatar" aria-hidden="true">{{ e.name.charAt(0).toUpperCase() }}</span>
                <strong class="name">{{ flag(e.country) }} {{ e.name }}</strong>
                <span class="xp">{{ e.xp }} XP</span>
                <span class="muted">{{ e.title }} · {{ e.games }} {{ e.games === 1 ? 'game' : 'games' }}</span>
              </div>
            }
          </div>
          <ol class="list" start="4">
            @for (e of rest(); track e.userId) {
              <li [class.me]="e.userId === me">
                <span class="rank">{{ e.rank }}</span><span class="name">{{ flag(e.country) }} {{ e.name }}{{ e.userId === me ? ' (you)' : '' }}</span>
                <span class="muted">{{ e.games }} {{ e.games === 1 ? 'game' : 'games' }}</span><strong>{{ e.xp }} XP</strong>
              </li>
            }
          </ol>
          @if (b.you && b.you.rank > b.top.length) {
            <p class="you">You're <strong>#{{ b.you.rank }}</strong> of {{ b.players }} with {{ b.you.xp }} XP.</p>
          } @else if (!b.you) {
            <p class="you muted">Play a game to get on this board.</p>
          }
        } @else {
          <div class="card empty"><h3>No games in this period yet</h3><p class="muted">Be the first: finish a game and come back.</p></div>
        }
      } @else {
        <div class="skeleton" style="height: 260px"></div>
      }
    }

    <mv-daily-board />
  `,
  styles: `
    .head { display: flex; justify-content: space-between; align-items: end; gap: 20px; flex-wrap: wrap; margin-bottom: 28px; }
    .head h1 { margin: 0 0 6px; }
    .head p { margin: 0; max-width: 520px; }
    .controls { display: flex; gap: 12px; flex-wrap: wrap; align-items: center; }
    select.input { min-width: 190px; }
    .podium { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; align-items: end; margin-bottom: 18px; }
    .step { display: flex; flex-direction: column; align-items: center; gap: 4px; padding: 20px 12px; text-align: center; border-radius: 22px;
      background: var(--bg-raised); border: 1px solid var(--border); }
    .step.p1 { order: 2; padding-top: 34px; border-color: color-mix(in srgb, gold 55%, var(--border)); box-shadow: 0 10px 40px color-mix(in srgb, gold 22%, transparent); }
    .step.p2 { order: 1; }
    .step.p3 { order: 3; }
    .step.me { outline: 2px solid var(--accent); }
    .medal { font-size: 2rem; }
    .avatar { width: 48px; height: 48px; border-radius: 50%; display: grid; place-items: center; font-weight: 900; color: #fff;
      background: linear-gradient(135deg, var(--accent-2), var(--pink)); }
    .name { max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .xp { font-size: 1.3rem; font-weight: 900; }
    .list { list-style: none; padding: 0; display: grid; gap: 6px; }
    .list li { display: grid; grid-template-columns: 40px 1fr auto 90px; gap: 12px; align-items: center; padding: 10px 14px; border-radius: 14px; background: var(--bg-sunken); }
    .list li strong { text-align: right; }
    .list li.me { outline: 2px solid var(--accent); }
    .rank { font-weight: 900; text-align: center; }
    .you { margin: 12px 0 0; }
    .empty { text-align: center; padding: 36px; }
    @media (max-width: 640px) { .podium { grid-template-columns: 1fr; } .step.p1, .step.p2, .step.p3 { order: 0; } }
  `,
})
export class Leaderboard {
  private readonly boards = inject(BoardsApi);
  private readonly catalog = inject(CatalogApi);
  protected readonly me = inject(AuthService).user()?.userId;
  protected readonly flag = flag;
  protected readonly medals = ['🥇', '🥈', '🥉'];
  protected readonly periods: { id: BoardPeriod; label: string }[] = [
    { id: 'DAY', label: 'Today' },
    { id: 'WEEK', label: 'This week' },
    { id: 'ALL', label: 'All time' },
  ];
  protected readonly period = signal<BoardPeriod>('WEEK');
  protected readonly categoryId = signal<number | null>(null);
  protected readonly categories = toSignal(this.catalog.categories().pipe(catchError(() => of([] as Category[]))), {
    initialValue: [] as Category[],
  });

  protected readonly state = toSignal(
    combineLatest([toObservable(this.period), toObservable(this.categoryId)]).pipe(
      switchMap(([p, c]) =>
        this.boards.leaderboard(p, c).pipe(
          map((board) => ({ board, error: null as string | null })),
          catchError((e) => of({ board: null as XpBoard | null, error: errorMessage(e) })),
          startWith({ board: null as XpBoard | null, error: null as string | null }),
        ),
      ),
    ),
  );

  protected readonly podium = computed(() => this.state()?.board?.top.slice(0, 3) ?? []);
  protected readonly rest = computed(() => this.state()?.board?.top.slice(3) ?? []);
}
