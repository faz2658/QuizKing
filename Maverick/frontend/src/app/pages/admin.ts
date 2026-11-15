import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Subscription, catchError, of, switchMap, takeWhile, timer } from 'rxjs';
import { CatalogApi, QuestionBankApi } from '../core/api';
import { AuthService } from '../core/auth.service';
import { errorMessage } from '../core/http-errors';
import { Category, ImportStatus, TopicStats } from '../core/models';

/** Creates a game from a topic's question bank in the game manager. */
@Component({
  selector: 'mv-admin',
  imports: [FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1>Question bank</h1>
    <section class="card stack bank">
      <div class="row">
        <div>
          <strong>{{ totalQuestions() }} questions</strong> in {{ stats().length }} topics
          <p class="muted small">Every topic with at least 12 questions gets a solo and a multiplayer game automatically.
            Games draw new questions on every play and skip ones the player has already seen.</p>
        </div>
        <span class="spacer"></span>
        <button class="btn btn-primary" type="button" (click)="startImport()" [disabled]="importStatus()?.state === 'RUNNING'">
          {{ importStatus()?.state === 'RUNNING' ? 'Importing…' : 'Import from Open Trivia DB' }}
        </button>
      </div>
      @if (importStatus(); as st) {
        @if (st.state === 'RUNNING') {
          <div class="progress" role="progressbar" aria-label="Import progress" [attr.aria-valuenow]="st.fetched" aria-valuemax="5300">
            <span [style.width.%]="Math.min(100, st.fetched / 53)"></span>
          </div>
          <p class="muted small" aria-live="polite">{{ st.fetched }} downloaded · {{ st.added }} new so far · about {{ minutesLeft(st) }} min left (Open Trivia DB allows one request every 5 seconds)</p>
        } @else if (st.state === 'DONE') {
          <p class="ok">{{ st.message }}</p>
        } @else if (st.state === 'FAILED') {
          <div class="alert" role="alert">Import stopped: {{ st.message }}. {{ st.added }} questions were saved; you can run it again.</div>
        }
      }
      @if (stats().length) {
        <details>
          <summary>Questions per topic</summary>
          <ul class="topics">
            @for (t of sortedStats(); track t.category + t.topic) {
              <li><span>{{ t.topic === t.category ? t.topic : t.category + ' · ' + t.topic }}</span><strong>{{ t.questions }}</strong></li>
            }
          </ul>
        </details>
      }
    </section>

    <h1>Create a game</h1>
    <form class="card stack form" (ngSubmit)="create()">
      <div class="two">
        <label class="field">Category
          <select class="input" name="category" [ngModel]="categoryId()" (ngModelChange)="categoryId.set(+$event); topic = ''" required>
            <option [ngValue]="0" disabled>Choose…</option>
            @for (c of categories(); track c.categoryId) { <option [ngValue]="c.categoryId">{{ c.categoryName }}</option> }
          </select>
        </label>
        <label class="field">Topic
          <select class="input" name="topic" [(ngModel)]="topic" required [disabled]="!topics().length">
            <option value="" disabled>Choose…</option>
            @for (t of topics(); track t.topicId) { <option [value]="t.topicName">{{ t.topicName }} ({{ t.questionCount }} questions)</option> }
          </select>
        </label>
      </div>
      <label class="field">Name <input class="input" name="name" [(ngModel)]="name" required maxlength="80" /></label>
      <label class="field">Description <textarea class="input" name="desc" rows="2" [(ngModel)]="description"></textarea></label>

      <fieldset class="field">
        <legend>Type</legend>
        <div class="row">
          <label><input type="radio" name="mode" value="SINGLE" [(ngModel)]="mode" /> Solo (timed &amp; adaptive)</label>
          <label><input type="radio" name="mode" value="MULTI" [(ngModel)]="mode" /> Multiplayer</label>
        </div>
      </fieldset>

      <div class="three">
        <label class="field">Easy questions <input class="input" type="number" min="0" name="easy" [(ngModel)]="easy" /></label>
        <label class="field">Medium questions <input class="input" type="number" min="0" name="medium" [(ngModel)]="medium" /></label>
        <label class="field">Advanced questions <input class="input" type="number" min="0" name="adv" [(ngModel)]="advanced" /></label>
      </div>
      @if (mode === 'SINGLE') {
        <p class="muted">Defaults: 10 / 15 / 20 seconds and 10 / 20 / 30 points per easy / medium / advanced question.</p>
      } @else {
        <label class="field">Seconds per question <input class="input" type="number" min="5" name="tpq" [(ngModel)]="secondsPerQuestion" /></label>
      }

      @if (error()) { <div class="alert" role="alert">{{ error() }}</div> }
      @if (created()) { <p class="ok">Created “{{ created() }}”.</p> }
      <div class="row"><button class="btn btn-primary" type="submit" [disabled]="busy()">Create game</button></div>
    </form>
  `,
  styles: `
    .bank { max-width: 760px; margin-bottom: 36px; }
    .small { font-size: 0.88rem; margin: 4px 0 0; }
    .progress { height: 8px; border-radius: 999px; background: var(--bg-sunken); overflow: hidden; }
    .progress span { display: block; height: 100%; background: linear-gradient(90deg, var(--accent-2), var(--accent)); transition: width 0.5s; }
    .topics { list-style: none; padding: 0; margin: 12px 0 0; display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 6px 20px; }
    .topics li { display: flex; justify-content: space-between; border-bottom: 1px solid var(--border); padding: 4px 0; }
    summary { cursor: pointer; font-weight: 650; }
    .form { max-width: 760px; }
    .two { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
    .three { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
    fieldset { border: 0; padding: 0; margin: 0; }
    legend { margin-bottom: 6px; }
    .ok { color: var(--good); font-weight: 700; }
    @media (max-width: 640px) { .two, .three { grid-template-columns: 1fr; } }
  `,
})
export class Admin implements OnInit {
  private readonly api = inject(CatalogApi);
  private readonly bank = inject(QuestionBankApi);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly Math = Math;
  protected readonly stats = signal<TopicStats[]>([]);
  protected readonly importStatus = signal<ImportStatus | null>(null);
  protected readonly totalQuestions = computed(() => this.stats().reduce((n, t) => n + t.questions, 0));
  protected readonly sortedStats = computed(() => [...this.stats()].sort((a, b) => b.questions - a.questions));
  private poll = new Subscription();

  ngOnInit(): void {
    this.destroyRef.onDestroy(() => this.poll.unsubscribe());
    this.loadStats();
    this.bank.importStatus().subscribe({ next: (s) => this.trackImport(s), error: () => {} });
  }

  protected startImport(): void {
    this.bank.startImport().subscribe({ next: (s) => this.trackImport(s), error: (e) => this.error.set(errorMessage(e)) });
  }

  protected minutesLeft(s: ImportStatus): number {
    return Math.max(1, Math.round(((5300 - s.fetched) / 50) * 5.5 / 60));
  }

  private trackImport(s: ImportStatus): void {
    this.importStatus.set(s);
    if (s.state !== 'RUNNING') {
      return;
    }
    this.poll.unsubscribe();
    this.poll = timer(3000, 3000)
      .pipe(switchMap(() => this.bank.importStatus()), takeWhile((st) => st.state === 'RUNNING', true))
      .subscribe((st) => {
        this.importStatus.set(st);
        this.loadStats();
      });
  }

  private loadStats(): void {
    this.bank.stats().pipe(catchError(() => of([] as TopicStats[]))).subscribe((s) => this.stats.set(s));
  }
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly categories = toSignal(this.api.categories().pipe(catchError(() => of([] as Category[]))), { initialValue: [] as Category[] });
  protected readonly categoryId = signal(0);
  protected readonly topics = computed(() => this.categories().find((c) => c.categoryId === this.categoryId())?.topic ?? []);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly created = signal<string | null>(null);

  protected topic = '';
  protected name = '';
  protected description = '';
  protected mode: 'SINGLE' | 'MULTI' = 'SINGLE';
  protected easy = 3;
  protected medium = 2;
  protected advanced = 1;
  protected secondsPerQuestion = 15;

  protected create(): void {
    this.error.set(null);
    this.created.set(null);
    if (!this.categoryId() || !this.topic || !this.name.trim()) {
      this.error.set('Choose a category and topic and give the game a name.');
      return;
    }
    this.busy.set(true);
    const levels = { easyLevel: this.easy, mediumLevel: this.medium, advancedLevel: this.advanced };
    const base = { gameName: this.name, gameDescription: this.description, createdBy: this.auth.user()?.userName, userId: this.auth.user()?.userId };
    const call = this.mode === 'SINGLE'
      ? this.api.createGame(this.categoryId(), this.topic, { ...base, questionLevels: levels })
      : this.api.createMultiplayerGame(this.categoryId(), this.topic, { ...base, questionLevel: levels, timePerQuestion: this.secondsPerQuestion });
    call.subscribe({
      next: (g) => {
        this.busy.set(false);
        this.created.set(g.gameName);
      },
      error: (e) => {
        this.busy.set(false);
        this.error.set(errorMessage(e));
      },
    });
  }
}
