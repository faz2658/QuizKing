import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, input, OnInit, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { catchError, map, of, Subscription, timer } from 'rxjs';
import { TOPIC_IDEAS, TopicQuizApi, TopicQuizView } from '../core/ai-quiz';
import { errorMessage } from '../core/http-errors';
import { shareLink } from '../core/invite';

/** Seconds per question when playing an AI quiz. */
const SECONDS = 20;

function verdict(score: number, total: number): string {
  const p = total ? score / total : 0;
  if (p === 1) return 'Flawless! 👑';
  if (p >= 0.75) return 'Expert level 🧠';
  if (p >= 0.5) return 'Not bad at all 👍';
  if (p >= 0.25) return 'Room to grow 🌱';
  return 'Better luck next time 😅';
}

const shared = `
  :host { display: block; max-width: 720px; margin: 0 auto; padding-bottom: 56px; }
  h1 { margin: 6px 0 8px; letter-spacing: -0.02em; }
  h2 { margin: 26px 0 12px; font-size: 1.15rem; }
  .card { padding: 22px; }
  .row { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
  .board { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 6px; }
  .board li { display: flex; gap: 12px; padding: 10px 14px; border-radius: 12px; background: var(--surface); border: 1px solid var(--border); }
  .board li.you { border-color: var(--accent); }
  .board .rank { width: 2ch; font-weight: 800; color: var(--text-muted); }
  .board .name { flex: 1; font-weight: 650; }
`;

/** Type any topic; the AI writes a quiz you can share. */
@Component({
  selector: 'mv-ai-quiz-start',
  imports: [FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="badge"><span class="dot"></span>Written by the Quiz King AI</span>
    <h1>A quiz about anything</h1>
    <p class="muted">Type a topic you love. The AI writes 8 questions in about a minute, then challenge your friends to beat you.</p>
    @if (offline()) {
      <div class="alert" role="alert">AI quizzes are switched off on this server right now.</div>
    } @else {
      <form class="card topic" (ngSubmit)="create()">
        <input class="input big" name="topic" [(ngModel)]="topic" maxlength="60" placeholder="e.g. Taylor Swift, Formula 1, Dinosaurs" aria-label="Topic" />
        <button class="btn btn-primary" type="submit" [disabled]="busy()">{{ busy() ? 'Starting…' : 'Write my quiz ✨' }}</button>
      </form>
      <div class="ideas">
        @for (t of ideas; track t) { <button type="button" class="chip-btn" (click)="topic = t">{{ t }}</button> }
      </div>
      @if (error()) { <div class="alert" role="alert">{{ error() }}</div> }
    }
  `,
  styles: shared + `
    .topic { display: flex; gap: 10px; margin: 20px 0 14px; flex-wrap: wrap; }
    .topic .big { flex: 1; min-width: 200px; font-size: 1.05rem; padding: 14px 16px; }
    .ideas { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 16px; }
    .chip-btn { padding: 7px 13px; border-radius: 999px; background: var(--surface); border: 1px solid var(--border); color: inherit; cursor: pointer; font: inherit; font-size: 0.88rem; }
    .chip-btn:hover { border-color: var(--accent); }
  `,
})
export class AiQuizStart {
  private readonly api = inject(TopicQuizApi);
  private readonly router = inject(Router);
  protected readonly ideas = TOPIC_IDEAS;
  protected readonly offline = toSignal(this.api.status().pipe(map((s) => !s.available), catchError(() => of(false))), { initialValue: false });
  protected topic = '';
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected create(): void {
    if (this.topic.trim().length < 2) {
      this.error.set('Type a topic first.');
      return;
    }
    this.error.set(null);
    this.busy.set(true);
    this.api.create(this.topic).subscribe({
      next: (q) => this.router.navigate(['/ai-quiz', q.id]),
      error: (e) => {
        this.busy.set(false);
        this.error.set(errorMessage(e));
      },
    });
  }
}

/** Watches the AI write, then plays the quiz against a per-question timer, then shows results and the leaderboard. */
@Component({
  selector: 'mv-ai-quiz-play',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (error()) {
      <div class="alert" role="alert">{{ error() }}</div>
      <a class="btn btn-ghost" routerLink="/ai-quiz">Try another topic</a>
    } @else if (quiz(); as q) {
      @switch (q.status) {
        @case ('GENERATING') {
          <div class="card writing fade-up">
            <div class="spark" aria-hidden="true">✨</div>
            <h1>Writing your {{ q.topic }} quiz…</h1>
            <p class="muted">Question {{ Math.min(q.written + 1, q.target) }} of {{ q.target }}. You can share the link already; it works as soon as the quiz is ready.</p>
            <div class="progress" aria-hidden="true"><span [style.width.%]="(q.written / q.target) * 100"></span></div>
            <button class="btn btn-ghost" (click)="share()">{{ shareLabel() }}</button>
          </div>
        }
        @case ('FAILED') {
          <div class="alert" role="alert">{{ q.problem }}</div>
          <a class="btn btn-primary" routerLink="/ai-quiz">Try another topic</a>
        }
        @default {
          @if (q.yourScore === null) {
            @if (!started()) {
              <div class="card intro fade-up">
                <span class="badge"><span class="dot"></span>AI quiz by {{ q.creatorName }}</span>
                <h1>{{ q.topic }}</h1>
                <p class="muted">{{ q.questions.length }} questions · {{ seconds }} seconds each · one go only{{ q.players ? ' · ' + q.players + ' played' : '' }}</p>
                <div class="row">
                  <button class="btn btn-primary big" (click)="begin()">Start</button>
                  <button class="btn btn-ghost" (click)="share()">{{ shareLabel() }}</button>
                </div>
              </div>
            } @else if (current(); as c) {
              <div class="meta"><span>{{ index() + 1 }} / {{ q.questions.length }}</span><span class="timer" [class.low]="left() <= 5">{{ left() }}s</span></div>
              <div class="progress" aria-hidden="true"><span [style.width.%]="(left() / seconds) * 100"></span></div>
              <div class="card ask fade-up">
                <h2>{{ c.text }}</h2>
                <div class="choices">
                  @for (o of c.options; track $index; let j = $index) {
                    <button class="btn choice" [disabled]="busy()" (click)="pick(j)">{{ o }}</button>
                  }
                </div>
              </div>
            }
          } @else {
            <div class="card verdict fade-up">
              <span class="badge"><span class="dot"></span>{{ q.topic }}</span>
              <div class="big-score">{{ q.yourScore }}/{{ q.questions.length }}</div>
              <h1>{{ verdictLine() }}</h1>
              <p class="muted">{{ placeLine() }}</p>
              <div class="row center">
                <button class="btn btn-primary" (click)="shareResult()">{{ shareLabel() }}</button>
                <a class="btn btn-ghost" routerLink="/ai-quiz">Make your own AI quiz →</a>
              </div>
            </div>
            <h2>Leaderboard</h2>
            <ol class="board">
              @for (e of q.leaderboard; track $index) {
                <li [class.you]="e.you"><span class="rank">{{ e.rank }}</span><span class="name">{{ e.name }}{{ e.you ? ' (you)' : '' }}</span><strong>{{ e.score }}/{{ q.questions.length }}</strong></li>
              }
            </ol>
            <h2>The answers</h2>
            <ol class="review">
              @for (x of q.questions; track $index; let i = $index) {
                <li [class.ok]="q.yourAnswers?.[i] === x.answer">
                  <span>{{ x.text }}</span>
                  <strong>{{ x.options[x.answer ?? 0] }}</strong>
                  @if (q.yourAnswers?.[i] !== x.answer) {
                    <span class="muted">{{ (q.yourAnswers?.[i] ?? -1) < 0 ? 'time ran out' : 'you said ' + x.options[q.yourAnswers![i]] }}</span>
                  }
                </li>
              }
            </ol>
            <p class="muted small">Questions are written by AI and can occasionally be wrong.</p>
          }
        }
      }
    } @else {
      <div class="skeleton" style="height: 300px"></div>
    }
  `,
  styles: shared + `
    .writing, .intro, .verdict { text-align: center; padding: 34px 22px; }
    .spark { font-size: 3rem; animation: spin 2.4s linear infinite; display: inline-block; }
    @keyframes spin { to { transform: rotate(360deg); } }
    .progress { height: 6px; border-radius: 99px; background: var(--surface-strong); overflow: hidden; margin: 14px 0 18px; }
    .progress span { display: block; height: 100%; background: var(--accent); transition: width 0.4s; }
    .intro .row, .center { justify-content: center; }
    .big { padding: 14px 30px; font-size: 1.05rem; }
    .meta { display: flex; justify-content: space-between; font-weight: 700; color: var(--text-muted); }
    .timer.low { color: var(--bad); }
    .ask h2 { margin: 0 0 18px; font-size: 1.3rem; }
    .choices { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
    .choice { padding: 16px; justify-content: center; text-align: center; white-space: normal; }
    .big-score { font-size: clamp(3rem, 10vw, 4.6rem); font-weight: 900; letter-spacing: -0.04em; margin-top: 8px; }
    .review { display: flex; flex-direction: column; gap: 8px; padding-left: 20px; }
    .review li { display: flex; flex-direction: column; gap: 2px; padding: 8px 0; border-bottom: 1px solid var(--border); }
    .review li strong { color: var(--bad); }
    .review li.ok strong { color: var(--good); }
    .small { font-size: 0.82rem; margin-top: 16px; }
    @media (max-width: 560px) { .choices { grid-template-columns: 1fr; } }
  `,
})
export class AiQuizPlay implements OnInit {
  private readonly api = inject(TopicQuizApi);
  private readonly destroyRef = inject(DestroyRef);
  readonly id = input.required<string>();
  protected readonly Math = Math;
  protected readonly seconds = SECONDS;
  protected readonly quiz = signal<TopicQuizView | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly started = signal(false);
  protected readonly index = signal(0);
  protected readonly left = signal(SECONDS);
  protected readonly busy = signal(false);
  protected readonly shareLabel = signal('Share');
  private answers: number[] = [];
  private poll?: Subscription;
  private clock?: ReturnType<typeof setInterval>;

  protected readonly current = computed(() => this.quiz()?.questions[this.index()] ?? null);
  protected readonly verdictLine = computed(() => {
    const q = this.quiz();
    return q && q.yourScore !== null ? verdict(q.yourScore, q.questions.length) : '';
  });
  protected readonly placeLine = computed(() => {
    const q = this.quiz();
    const me = q?.leaderboard.find((e) => e.you);
    return !q || !me ? '' : q.players === 1 ? 'You\'re the first to play. Share it and see who beats you!' : `You're #${me.rank} of ${q.players} players.`;
  });

  constructor() {
    this.destroyRef.onDestroy(() => {
      this.poll?.unsubscribe();
      clearInterval(this.clock);
    });
  }

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.api.get(this.id()).subscribe({
      next: (q) => {
        this.quiz.set(q);
        if (q.status === 'GENERATING') this.poll = timer(2000).subscribe(() => this.load());
      },
      error: (e) => this.error.set(errorMessage(e)),
    });
  }

  protected begin(): void {
    this.answers = [];
    this.index.set(0);
    this.started.set(true);
    this.restartClock();
  }

  protected pick(option: number): void {
    const q = this.quiz();
    if (!q || this.busy()) return;
    this.answers[this.index()] = option;
    if (this.index() < q.questions.length - 1) {
      this.index.update((i) => i + 1);
      this.restartClock();
      return;
    }
    clearInterval(this.clock);
    this.busy.set(true);
    this.api.answer(q.id, this.answers).subscribe({
      next: (done) => {
        this.busy.set(false);
        this.quiz.set(done);
      },
      error: (e) => {
        this.busy.set(false);
        this.error.set(errorMessage(e));
      },
    });
  }

  private restartClock(): void {
    clearInterval(this.clock);
    this.left.set(SECONDS);
    this.clock = setInterval(() => {
      this.left.update((s) => s - 1);
      if (this.left() <= 0) this.pick(-1); // time's up: counts as wrong
    }, 1000);
  }

  protected async share(): Promise<void> {
    const q = this.quiz();
    if (!q) return;
    this.report(await shareLink(`${q.topic} quiz`, `Think you know ${q.topic}? Try this quiz on Quiz King:`, location.href));
  }

  protected async shareResult(): Promise<void> {
    const q = this.quiz();
    if (!q || q.yourScore === null) return;
    this.report(await shareLink(`${q.topic} quiz`, `I scored ${q.yourScore}/${q.questions.length} on the ${q.topic} quiz. Can you beat me?`, location.href));
  }

  private report(r: 'shared' | 'copied' | 'failed'): void {
    this.shareLabel.set(r === 'copied' ? 'Link copied!' : r === 'failed' ? 'Copy failed' : 'Share');
    if (r !== 'shared') setTimeout(() => this.shareLabel.set('Share'), 2500);
  }
}
