import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { catchError, of } from 'rxjs';
import { QuestionBankApi } from '../core/api';
import { errorMessage } from '../core/http-errors';
import { Category, DraftQuestion } from '../core/models';

const LEVELS = ['', 'Easy', 'Medium', 'Hard'];

/** Generate questions with the fine-tuned model and review them before they reach any game. */
@Component({
  selector: 'mv-ai-drafts',
  imports: [FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card stack box">
      <div class="row">
        <h2>AI question drafts</h2>
        <span class="spacer"></span>
        <span class="chip" [class.on]="available()">{{ available() ? 'Model connected' : 'No model connected' }}</span>
      </div>
      @if (!available()) {
        <p class="muted small">Train the question model with Unsloth (see <code>ai/question-model/README.md</code>), then start it with
          <code>docker compose --profile ai up</code>. Until then you can still review any pending drafts below.</p>
      } @else {
        <p class="muted small">The model writes new questions; nothing reaches players until you approve it here.</p>
        <form class="gen" (ngSubmit)="generate()">
          <label class="field">Topic
            <select class="input" name="topic" [(ngModel)]="topicKey" required>
              <option value="" disabled>Choose…</option>
              @for (t of topics(); track t.key) { <option [value]="t.key">{{ t.label }}</option> }
            </select>
          </label>
          <label class="field">Difficulty
            <select class="input" name="level" [(ngModel)]="level">
              <option [ngValue]="1">Easy</option><option [ngValue]="2">Medium</option><option [ngValue]="3">Hard</option>
            </select>
          </label>
          <label class="field">Format
            <select class="input" name="tf" [(ngModel)]="trueFalse">
              <option [ngValue]="false">Multiple choice</option><option [ngValue]="true">True / false</option>
            </select>
          </label>
          <label class="field">How many
            <input class="input" type="number" min="1" max="20" name="count" [(ngModel)]="count" />
          </label>
          <button class="btn btn-primary" type="submit" [disabled]="busy() || !topicKey">{{ busy() ? 'Writing…' : 'Generate' }}</button>
        </form>
        @if (summary()) { <p class="muted small" aria-live="polite">{{ summary() }}</p> }
      }
      @if (error()) { <div class="alert" role="alert">{{ error() }}</div> }

      @if (drafts().length) {
        <h3>{{ drafts().length }} waiting for review</h3>
        <ul class="drafts">
          @for (d of drafts(); track d.id) {
            <li class="draft">
              <div class="row meta">
                <span class="chip">{{ d.topicName }}</span>
                <span class="chip">{{ levelName(d.question.questionLevel) }}</span>
              </div>
              <label class="sr-only" [for]="'stem-' + d.id">Question</label>
              <textarea class="input stem" rows="2" [id]="'stem-' + d.id" [(ngModel)]="d.question.questionStem" [name]="'stem-' + d.id"></textarea>
              <div class="opts">
                @for (o of options(d); track $index) {
                  <span class="opt" [class.answer]="o === d.question.correctAnswer">{{ o }}</span>
                }
              </div>
              <label class="sr-only" [for]="'why-' + d.id">Explanation (optional)</label>
              <input class="input why" maxlength="300" [id]="'why-' + d.id" [name]="'why-' + d.id"
                placeholder="Why is this the answer? (optional, shown to players after they answer)" [(ngModel)]="d.question.explanation" />
              <div class="row">
                <button class="btn btn-primary" type="button" (click)="approve(d)">Approve</button>
                <button class="btn" type="button" (click)="reject(d)">Reject</button>
              </div>
            </li>
          }
        </ul>
      } @else if (available()) {
        <p class="muted small">No drafts waiting.</p>
      }
    </section>
  `,
  styles: `
    .box { max-width: 860px; margin-bottom: 36px; }
    .box h2 { margin: 0; }
    .small { font-size: 0.88rem; margin: 0; }
    .chip.on { color: var(--good); border-color: color-mix(in srgb, var(--good) 40%, transparent); }
    .gen { display: grid; grid-template-columns: 2fr 1fr 1.3fr 0.8fr auto; gap: 12px; align-items: end; }
    .drafts { list-style: none; padding: 0; margin: 0; display: grid; gap: 14px; }
    .draft { padding: 16px; border-radius: var(--radius); border: 1px solid var(--border); background: var(--surface); display: grid; gap: 10px; }
    .meta { gap: 6px; }
    .why { margin: 8px 0 10px; font-size: 0.9rem; }
    .stem { font-weight: 650; resize: vertical; }
    .opts { display: flex; flex-wrap: wrap; gap: 8px; }
    .opt { padding: 6px 12px; border-radius: 999px; border: 1px solid var(--border); font-size: 0.9rem; }
    .opt.answer { border-color: var(--good); color: var(--good); background: var(--good-bg); font-weight: 700; }
    code { font-size: 0.85em; }
    @media (max-width: 760px) { .gen { grid-template-columns: 1fr 1fr; } }
  `,
})
export class AiDrafts implements OnInit {
  readonly categories = input.required<Category[]>();
  private readonly bank = inject(QuestionBankApi);

  protected readonly available = signal(false);
  protected readonly drafts = signal<DraftQuestion[]>([]);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly summary = signal<string | null>(null);
  protected topicKey = '';
  protected level = 2;
  protected trueFalse = false;
  protected count = 5;

  protected readonly topics = computed(() =>
    this.categories().flatMap((c) => (c.topic ?? []).map((t) => ({
      key: `${c.categoryId}|${t.topicName}`,
      label: c.categoryName === t.topicName ? t.topicName : `${c.categoryName} · ${t.topicName}`,
    }))),
  );

  ngOnInit(): void {
    this.bank.aiAvailable().pipe(catchError(() => of(false))).subscribe((a) => this.available.set(a));
    this.load();
  }

  protected levelName(l: number): string {
    return LEVELS[l] ?? `Level ${l}`;
  }

  protected options(d: DraftQuestion): string[] {
    const q = d.question;
    return [q.option1, q.option2, q.option3, q.option4].filter((o): o is string => !!o);
  }

  protected generate(): void {
    const [id, ...rest] = this.topicKey.split('|');
    this.busy.set(true);
    this.error.set(null);
    this.summary.set(null);
    this.bank.generateDrafts(Number(id), rest.join('|'), this.level, this.trueFalse, this.count).subscribe({
      next: (r) => {
        this.busy.set(false);
        this.summary.set(`${r.created} of ${r.requested} written` +
          (r.duplicates ? `, ${r.duplicates} duplicates skipped` : '') + (r.invalidReplies ? `, ${r.invalidReplies} unusable replies skipped` : '') + '.');
        this.load();
      },
      error: (e) => {
        this.busy.set(false);
        this.error.set(errorMessage(e));
      },
    });
  }

  protected approve(d: DraftQuestion): void {
    this.bank.approveDraft(d.id, d.question).subscribe({ next: () => this.remove(d), error: (e) => this.error.set(errorMessage(e)) });
  }

  protected reject(d: DraftQuestion): void {
    this.bank.rejectDraft(d.id).subscribe({ next: () => this.remove(d), error: (e) => this.error.set(errorMessage(e)) });
  }

  private remove(d: DraftQuestion): void {
    this.drafts.update((list) => list.filter((x) => x.id !== d.id));
  }

  private load(): void {
    this.bank.pendingDrafts().pipe(catchError(() => of([] as DraftQuestion[]))).subscribe((d) => this.drafts.set(d));
  }
}
