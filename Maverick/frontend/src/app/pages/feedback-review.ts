import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { QuestionBankApi } from '../core/api';
import { errorMessage } from '../core/http-errors';
import { BankQuestion, FeedbackItem, FeedbackReview, FlagReason } from '../core/models';
import { FLAG_REASONS } from '../ui/question-feedback';

/** Options of a bank question, without the empty slots. */
export function optionsOf(q: BankQuestion): string[] {
  return [q.option1, q.option2, q.option3, q.option4].filter((o): o is string => !!o && o.trim().length > 0);
}

/** Admin queue of questions players flagged or rated down, plus the ones that were retired automatically. */
@Component({
  selector: 'mv-feedback-review',
  imports: [FormsModule, DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card review">
      <div class="row head">
        <h2>Player feedback</h2>
        <span class="spacer"></span>
        <button class="btn btn-ghost" type="button" (click)="load()">Refresh</button>
      </div>
      <p class="muted small">Questions players flagged or rated down. Three flags or mostly thumbs down from signed-in players retire a question automatically (guests can't).</p>
      @if (error()) { <div class="alert" role="alert">{{ error() }}</div> }
      @if (review(); as r) {
        @if (!r.questions.length && !r.retired.length) { <p class="muted">Nothing to review. 🎉</p> }
        @for (item of r.questions; track item.questionId) {
          <article class="item">
            <div class="row meta">
              <span class="chip">{{ item.topicName }}</span>
              <span>👍 {{ item.ups }}</span><span>👎 {{ item.downs }}</span><span>🚩 {{ item.flags }}</span>
              @for (r of reasonList(item); track r.id) { <span class="chip warn">{{ r.label }} × {{ r.count }}</span> }
            </div>
            <label class="sr-only" [for]="'stem-' + item.questionId">Question</label>
            <textarea class="input" rows="2" [id]="'stem-' + item.questionId" [(ngModel)]="item.question.questionStem" [name]="'stem-' + item.questionId"></textarea>
            <div class="opts">
              @for (o of options(item); track $index) {
                <button type="button" class="opt" [class.answer]="o === item.question.correctAnswer" (click)="item.question.correctAnswer = o"
                  [attr.aria-pressed]="o === item.question.correctAnswer" [title]="'Make this the correct answer'">{{ o }}</button>
              }
            </div>
            <input class="input" maxlength="300" placeholder="Explanation (optional)" [(ngModel)]="item.question.explanation" [name]="'why-' + item.questionId" />
            @if (item.notes.length) {
              <ul class="notes">@for (n of item.notes; track $index) { <li>“{{ n }}”</li> }</ul>
            }
            <div class="row">
              <button class="btn btn-primary" type="button" (click)="save(item)">Save fix</button>
              <button class="btn" type="button" (click)="dismiss(item)">It's fine</button>
              <button class="btn btn-ghost danger" type="button" (click)="remove(item)">Delete question</button>
            </div>
          </article>
        }
        @if (r.retired.length) {
          <h3>Retired automatically</h3>
          @for (q of r.retired; track q.questionId) {
            <article class="item retired">
              <div class="row meta"><span class="chip">{{ q.topicName }}</span><span class="muted">{{ q.why }} · {{ q.retiredAt | date: 'medium' }}</span></div>
              <p><strong>{{ q.question.questionStem }}</strong> <span class="muted">· {{ q.question.correctAnswer }}</span></p>
              <button class="btn" type="button" (click)="restore(q.questionId)">Restore</button>
            </article>
          }
        }
      }
    </section>
  `,
  styles: `
    .review { margin-top: 28px; }
    .head h2 { margin: 0; }
    .small { font-size: 0.88rem; }
    .item { border-top: 1px solid var(--border); padding: 16px 0; display: grid; gap: 8px; }
    .meta { gap: 10px; flex-wrap: wrap; font-weight: 700; }
    .chip.warn { border-color: var(--bad); color: var(--bad); }
    .opts { display: flex; flex-wrap: wrap; gap: 6px; }
    .opt { border: 1px solid var(--border); border-radius: 999px; padding: 4px 12px; background: var(--bg-raised); color: var(--text); cursor: pointer; }
    .opt.answer { border-color: var(--good); background: color-mix(in srgb, var(--good) 15%, var(--bg-raised)); font-weight: 800; }
    .notes { margin: 0; padding-left: 18px; color: var(--text-muted); }
    .danger { color: var(--bad); }
    .retired p { margin: 0; }
  `,
})
export class FeedbackReviewPanel implements OnInit {
  private readonly api = inject(QuestionBankApi);
  protected readonly review = signal<FeedbackReview | null>(null);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.api.feedbackReview().subscribe({ next: (r) => this.review.set(r), error: (e) => this.error.set(errorMessage(e)) });
  }

  protected options(item: FeedbackItem): string[] {
    return optionsOf(item.question);
  }

  protected reasonList(item: FeedbackItem): { id: FlagReason; label: string; count: number }[] {
    return FLAG_REASONS.filter((r) => item.reasons[r.id]).map((r) => ({ ...r, count: item.reasons[r.id]! }));
  }

  protected save(item: FeedbackItem): void {
    this.api.updateQuestion(item.categoryId, item.topicName, item.question).subscribe({
      next: () => this.dismiss(item),
      error: (e) => this.error.set(errorMessage(e)),
    });
  }

  protected dismiss(item: FeedbackItem): void {
    this.api.dismissFeedback(item.questionId).subscribe({ next: () => this.load(), error: (e) => this.error.set(errorMessage(e)) });
  }

  protected remove(item: FeedbackItem): void {
    this.api.deleteQuestion(item.categoryId, item.topicName, item.questionId).subscribe({
      next: () => this.dismiss(item),
      error: (e) => this.error.set(errorMessage(e)),
    });
  }

  protected restore(questionId: number): void {
    this.api.restoreQuestion(questionId).subscribe({ next: () => this.load(), error: (e) => this.error.set(errorMessage(e)) });
  }
}
