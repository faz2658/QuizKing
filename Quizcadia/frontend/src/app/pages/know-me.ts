import { ChangeDetectionStrategy, Component, computed, inject, input, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { errorMessage } from '../core/http-errors';
import { shareLink } from '../core/invite';
import { friendshipTitle, KNOW_ME_PROMPTS, KnowMeApi, KnowMeDraft, KnowMeMine, KnowMeView } from '../core/knowme';

function linkTo(id: string): string {
  return `${location.origin}/know-me/${id}`;
}

const shared = `
  :host { display: block; max-width: 760px; margin: 0 auto; padding-bottom: 56px; }
  h1 { margin: 6px 0 8px; letter-spacing: -0.02em; }
  .lead { margin: 0 0 24px; }
  .card { padding: 22px; }
  .stack { display: flex; flex-direction: column; gap: 14px; }
  .row { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
  .spacer { flex: 1; }
  .done { border-radius: var(--radius); padding: 12px 16px; background: var(--good-bg); color: var(--good); font-weight: 600; }
  .board { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 6px; }
  .board li { display: flex; gap: 12px; padding: 10px 14px; border-radius: 12px; background: var(--surface); border: 1px solid var(--border); }
  .board li.you { border-color: var(--accent); }
  .board .rank { width: 2ch; font-weight: 800; color: var(--text-muted); }
  .board .name { flex: 1; font-weight: 650; }
  .board .score { font-weight: 800; }
`;

/** Write a "How well do you know me?" quiz from ready-made prompts, then share the link. */
@Component({
  selector: 'mv-know-me-create',
  imports: [FormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="badge"><span class="dot"></span>Takes 2 minutes</span>
    <h1>How well do your friends know you?</h1>
    <p class="lead muted">Answer a few questions about yourself, send the link, and see who really knows you best.</p>

    @if (mine().length) {
      <details class="card mine">
        <summary>Your quizzes ({{ mine().length }})</summary>
        <ul>
          @for (q of mine(); track q.id) {
            <li><a [routerLink]="['/know-me', q.id]">{{ q.questions }} questions · {{ q.players }} {{ q.players === 1 ? 'friend' : 'friends' }} played</a></li>
          }
        </ul>
      </details>
    }

    <form class="stack" (ngSubmit)="save()">
      @for (q of drafts(); track $index; let i = $index) {
        <div class="card stack q">
          <div class="row">
            <strong class="num">{{ i + 1 }}</strong>
            <input class="input grow" [name]="'text' + i" [(ngModel)]="q.text" maxlength="140" placeholder="Your question" aria-label="Question" />
            <button type="button" class="btn btn-ghost icon" (click)="remove(i)" [disabled]="drafts().length <= 3" aria-label="Remove question">✕</button>
          </div>
          <p class="muted hint">Fill in the options and tap the one that's true for you.</p>
          <div class="options">
            @for (o of q.options; track $index; let j = $index) {
              <div class="option" [class.right]="q.answer === j">
                <button type="button" class="pick" (click)="q.answer = j; touch()" [attr.aria-pressed]="q.answer === j"
                  [attr.aria-label]="'Mark option ' + (j + 1) + ' as the right answer'">{{ q.answer === j ? '✓' : '' }}</button>
                <input class="input" [name]="'o' + i + '-' + j" [(ngModel)]="q.options[j]" maxlength="80" [placeholder]="'Option ' + (j + 1)" />
              </div>
            }
          </div>
          @if (q.options.length < 4) {
            <button type="button" class="btn btn-ghost add-option" (click)="q.options.push(''); touch()">+ Add an option</button>
          }
        </div>
      }

      <div class="row">
        @if (drafts().length < 15) {
          <button type="button" class="btn btn-ghost" (click)="add()">+ Another question</button>
        }
        <div class="spacer"></div>
        <span class="muted">{{ drafts().length }} / 15</span>
      </div>

      @if (error()) { <div class="alert" role="alert">{{ error() }}</div> }
      <button class="btn btn-primary wide" type="submit" [disabled]="busy()">{{ busy() ? 'Saving…' : 'Create my quiz and get the link' }} →</button>
    </form>
  `,
  styles: shared + `
    .mine { margin-bottom: 22px; }
    .mine summary { cursor: pointer; font-weight: 700; }
    .mine ul { margin: 12px 0 0; padding-left: 18px; display: flex; flex-direction: column; gap: 6px; }
    .q { gap: 10px; }
    .num { width: 1.6em; text-align: center; color: var(--text-muted); }
    .grow { flex: 1; min-width: 0; }
    .icon { padding: 8px 10px; }
    .hint { margin: 0; font-size: 0.82rem; }
    .options { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
    .option { display: flex; gap: 8px; align-items: center; }
    .option .input { flex: 1; min-width: 0; }
    .pick { flex: none; width: 34px; height: 34px; border-radius: 50%; border: 2px solid var(--border); background: transparent;
      color: var(--good); font-weight: 900; cursor: pointer; }
    .option.right .pick { border-color: var(--good); background: var(--good-bg); }
    .option.right .input { border-color: var(--good); }
    .add-option { align-self: flex-start; padding-left: 0; }
    .wide { width: 100%; padding: 14px; }
    @media (max-width: 560px) { .options { grid-template-columns: 1fr; } }
  `,
})
export class KnowMeCreate implements OnInit {
  private readonly api = inject(KnowMeApi);
  private readonly router = inject(Router);
  protected readonly drafts = signal<KnowMeDraft[]>(
    KNOW_ME_PROMPTS.slice(0, 6).map((text) => ({ text, options: ['', ''], answer: -1 })),
  );
  protected readonly mine = signal<KnowMeMine[]>([]);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.api.mine().subscribe({ next: (m) => this.mine.set(m), error: () => this.mine.set([]) });
  }

  /** The drafts are edited in place; this tells the signal they changed. */
  protected touch(): void {
    this.drafts.update((d) => [...d]);
  }

  protected add(): void {
    const used = new Set(this.drafts().map((d) => d.text));
    const next = KNOW_ME_PROMPTS.find((p) => !used.has(p)) ?? '';
    this.drafts.update((d) => [...d, { text: next, options: ['', ''], answer: -1 }]);
  }

  protected remove(i: number): void {
    this.drafts.update((d) => d.filter((_, j) => j !== i));
  }

  protected save(): void {
    const questions = this.drafts()
      .map((d) => ({ ...d, text: d.text.trim(), options: d.options.map((o) => o.trim()) }))
      .filter((d) => d.text || d.options.some((o) => o));
    const problem = questions.findIndex((d) => !d.text || d.options.filter((o) => o).length < 2 || d.answer < 0 || !d.options[d.answer]);
    if (questions.length < 3) {
      this.error.set('Write at least 3 questions.');
      return;
    }
    if (problem >= 0) {
      this.error.set(`Question ${problem + 1}: write the question, at least 2 options, and tap the right one.`);
      return;
    }
    // drop empty extra options, keeping the right answer's position
    const cleaned = questions.map((d) => {
      const right = d.options[d.answer];
      const options = d.options.filter((o) => o);
      return { text: d.text, options, answer: options.indexOf(right) };
    });
    this.error.set(null);
    this.busy.set(true);
    this.api.create(cleaned).subscribe({
      next: (quiz) => this.router.navigate(['/know-me', quiz.id], { queryParams: { created: 1 } }),
      error: (e) => {
        this.busy.set(false);
        this.error.set(errorMessage(e));
      },
    });
  }
}

/** A friend answers the quiz; afterwards everyone sees the right answers, a verdict and the leaderboard. */
@Component({
  selector: 'mv-know-me-play',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (error()) {
      <div class="alert" role="alert">{{ error() }}</div>
      <a class="btn btn-ghost" routerLink="/know-me/new">Make your own quiz</a>
    } @else if (quiz(); as q) {
      @if (q.own) {
        <span class="badge"><span class="dot"></span>Your quiz</span>
        <h1>How well do your friends know you?</h1>
        @if (created()) { <div class="done" role="status">Your quiz is ready. Send the link to your friends!</div> }
        <div class="card share">
          <code>{{ link() }}</code>
          <button class="btn btn-primary" (click)="share()">{{ shareLabel() }}</button>
        </div>
        <h2>Leaderboard</h2>
        @if (q.leaderboard.length) {
          <ol class="board">
            @for (e of q.leaderboard; track $index) {
              <li><span class="rank">{{ e.rank }}</span><span class="name">{{ e.name }}</span><span class="score">{{ e.score }}/{{ q.questions.length }}</span></li>
            }
          </ol>
        } @else {
          <p class="muted">Nobody has played yet. Results show up here as your friends answer.</p>
        }
        <details class="card answers">
          <summary>Your questions and answers</summary>
          <ol>@for (x of q.questions; track $index) { <li>{{ x.text }} <strong>{{ x.options[x.answer ?? 0] }}</strong></li> }</ol>
        </details>
        <button class="btn btn-ghost danger" (click)="remove()">Delete this quiz</button>
      } @else if (q.yourScore === null) {
        <span class="badge"><span class="dot"></span>Question {{ index() + 1 }} of {{ q.questions.length }}</span>
        <h1>How well do you know {{ q.ownerName }}?</h1>
        <div class="progress" aria-hidden="true"><span [style.width.%]="(index() / q.questions.length) * 100"></span></div>
        @if (current(); as c) {
          <div class="card ask fade-up">
            <h2>{{ c.text }}</h2>
            <div class="choices">
              @for (o of c.options; track $index; let j = $index) {
                <button class="btn choice" [disabled]="busy()" (click)="pick(j)">{{ o }}</button>
              }
            </div>
          </div>
        }
        @if (index() > 0 && !busy()) { <button class="btn btn-ghost" (click)="back()">← Previous question</button> }
      } @else {
        <span class="badge"><span class="dot"></span>{{ q.ownerName }}'s quiz</span>
        <div class="card verdict fade-up">
          <div class="big">{{ q.yourScore }}/{{ q.questions.length }}</div>
          <h1>{{ title() }}</h1>
          <p class="muted">{{ placeLine() }}</p>
          <div class="row center">
            <button class="btn btn-primary" (click)="shareResult()">{{ shareLabel() }}</button>
            <a class="btn btn-ghost" routerLink="/know-me/new">Make your own quiz →</a>
          </div>
        </div>
        <h2>Leaderboard</h2>
        <ol class="board">
          @for (e of q.leaderboard; track $index) {
            <li [class.you]="e.you"><span class="rank">{{ e.rank }}</span><span class="name">{{ e.name }}{{ e.you ? ' (you)' : '' }}</span><span class="score">{{ e.score }}/{{ q.questions.length }}</span></li>
          }
        </ol>
        <h2>The answers</h2>
        <ol class="review">
          @for (x of q.questions; track $index; let i = $index) {
            <li [class.ok]="q.yourAnswers?.[i] === x.answer">
              <span>{{ x.text }}</span>
              <strong>{{ x.options[x.answer ?? 0] }}</strong>
              @if (q.yourAnswers?.[i] !== x.answer) { <span class="muted">you said {{ x.options[q.yourAnswers?.[i] ?? 0] }}</span> }
            </li>
          }
        </ol>
      }
    } @else {
      <div class="skeleton" style="height: 320px"></div>
    }
  `,
  styles: shared + `
    h2 { margin: 28px 0 12px; font-size: 1.15rem; }
    .share { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; margin-top: 16px; }
    .share code { flex: 1; min-width: 0; overflow-wrap: anywhere; font-size: 0.9rem; }
    .answers { margin-top: 22px; }
    .answers summary { cursor: pointer; font-weight: 700; }
    .answers ol { margin: 12px 0 0; display: flex; flex-direction: column; gap: 6px; }
    .danger { margin-top: 18px; color: var(--bad); padding-left: 0; }
    .progress { height: 6px; border-radius: 99px; background: var(--surface-strong); overflow: hidden; margin-bottom: 18px; }
    .progress span { display: block; height: 100%; background: var(--accent); transition: width 0.3s; }
    .ask h2 { margin: 0 0 18px; font-size: 1.35rem; }
    .choices { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
    .choice { padding: 16px; justify-content: center; text-align: center; white-space: normal; }
    .verdict { text-align: center; padding: 30px 22px; margin-top: 12px; }
    .verdict .big { font-size: clamp(3rem, 10vw, 4.6rem); font-weight: 900; letter-spacing: -0.04em; }
    .verdict h1 { margin: 4px 0 6px; }
    .center { justify-content: center; margin-top: 16px; }
    .review { display: flex; flex-direction: column; gap: 8px; padding-left: 20px; }
    .review li { display: flex; flex-direction: column; gap: 2px; padding: 8px 0; border-bottom: 1px solid var(--border); }
    .review li strong { color: var(--bad); }
    .review li.ok strong { color: var(--good); }
    @media (max-width: 560px) { .choices { grid-template-columns: 1fr; } }
  `,
})
export class KnowMePlay implements OnInit {
  private readonly api = inject(KnowMeApi);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  /** Route parameter. */
  readonly id = input.required<string>();
  /** ?created=1 right after the owner saved it. */
  readonly created = input<string>();
  protected readonly quiz = signal<KnowMeView | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly index = signal(0);
  protected readonly busy = signal(false);
  protected readonly shareLabel = signal('Share');
  private answers: number[] = [];

  protected readonly current = computed(() => this.quiz()?.questions[this.index()] ?? null);
  protected readonly link = computed(() => linkTo(this.id()));
  protected readonly title = computed(() => {
    const q = this.quiz();
    return q && q.yourScore !== null ? friendshipTitle(q.yourScore, q.questions.length) : '';
  });
  protected readonly placeLine = computed(() => {
    const q = this.quiz();
    const me = q?.leaderboard.find((e) => e.you);
    if (!q || !me) return '';
    return q.players === 1 ? `You're the first of ${q.ownerName}'s friends to play.` : `You're #${me.rank} of ${q.players} friends.`;
  });

  ngOnInit(): void {
    this.api.get(this.id()).subscribe({ next: (q) => this.quiz.set(q), error: (e) => this.error.set(errorMessage(e)) });
  }

  protected pick(option: number): void {
    const q = this.quiz();
    if (!q) return;
    this.answers[this.index()] = option;
    if (this.index() < q.questions.length - 1) {
      this.index.update((i) => i + 1);
      return;
    }
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

  protected back(): void {
    this.index.update((i) => Math.max(0, i - 1));
  }

  protected async share(): Promise<void> {
    const name = this.auth.user()?.userName ?? 'me';
    this.report(await shareLink('How well do you know me?', `How well do you know ${name}? Take my quiz on QuizCadia:`, this.link()));
  }

  protected async shareResult(): Promise<void> {
    const q = this.quiz();
    if (!q || q.yourScore === null) return;
    this.report(await shareLink('How well do you know me?',
      `I got ${q.yourScore}/${q.questions.length} on ${q.ownerName}'s quiz (${this.title()}). How well do you know ${q.ownerName}?`, this.link()));
  }

  protected remove(): void {
    const q = this.quiz();
    if (!q || !confirm('Delete this quiz and everyone\'s answers?')) return;
    this.api.delete(q.id).subscribe({ next: () => this.router.navigateByUrl('/know-me/new'), error: (e) => this.error.set(errorMessage(e)) });
  }

  private report(r: 'shared' | 'copied' | 'failed'): void {
    this.shareLabel.set(r === 'copied' ? 'Link copied!' : r === 'failed' ? 'Copy failed' : 'Share');
    if (r !== 'shared') setTimeout(() => this.shareLabel.set('Share'), 2500);
  }
}
