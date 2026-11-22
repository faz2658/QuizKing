import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { QuestionBankApi } from '../core/api';
import { errorMessage } from '../core/http-errors';
import { FlagReason } from '../core/models';

/** The reasons a player can give when flagging a question. */
export const FLAG_REASONS: { id: FlagReason; label: string }[] = [
  { id: 'WRONG_ANSWER', label: 'The answer is wrong' },
  { id: 'UNCLEAR', label: 'The question is unclear' },
  { id: 'TYPO', label: 'Typo or formatting' },
  { id: 'OFFENSIVE', label: 'Offensive' },
  { id: 'OTHER', label: 'Something else' },
];

/** Thumbs up/down and "report a problem" for one question. */
@Component({
  selector: 'mv-question-feedback',
  imports: [FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="fb">
      <span class="muted small">Rate this question</span>
      <button type="button" class="vote" [class.on]="rating() === 1" [attr.aria-pressed]="rating() === 1" aria-label="Good question" (click)="rate(1)">👍</button>
      <button type="button" class="vote" [class.on]="rating() === -1" [attr.aria-pressed]="rating() === -1" aria-label="Bad question" (click)="rate(-1)">👎</button>
      <button type="button" class="vote" [class.on]="reported()" aria-label="Report a problem" [attr.aria-expanded]="open()" (click)="open.set(!open())">🚩</button>
      @if (message()) { <span class="small msg" aria-live="polite">{{ message() }}</span> }
    </div>
    @if (open() && !reported()) {
      <form class="report" (submit)="report($event)">
        <select class="input" name="reason" [(ngModel)]="reason" aria-label="What's wrong?">
          @for (r of reasons; track r.id) { <option [ngValue]="r.id">{{ r.label }}</option> }
        </select>
        <input class="input" name="note" maxlength="200" placeholder="Details (optional), e.g. the right answer" [(ngModel)]="note" />
        <button class="btn" type="submit">Send report</button>
      </form>
    }
  `,
  styles: `
    .fb { display: flex; align-items: center; gap: 6px; margin-top: 10px; flex-wrap: wrap; }
    .vote { border: 1px solid var(--border); background: var(--bg-raised); border-radius: 999px; padding: 4px 10px; cursor: pointer; font-size: 1rem; }
    .vote.on { border-color: var(--accent); background: color-mix(in srgb, var(--accent) 14%, var(--bg-raised)); }
    .small { font-size: 0.85rem; }
    .msg { color: var(--accent); font-weight: 700; }
    .report { display: flex; gap: 8px; margin-top: 8px; flex-wrap: wrap; }
    .report .input { flex: 1 1 200px; }
  `,
})
export class QuestionFeedback {
  readonly questionId = input.required<number>();
  private readonly api = inject(QuestionBankApi);
  protected readonly reasons = FLAG_REASONS;
  protected readonly rating = signal(0);
  protected readonly open = signal(false);
  protected readonly reported = signal(false);
  protected readonly message = signal<string | null>(null);
  protected reason: FlagReason = 'WRONG_ANSWER';
  protected note = '';

  protected rate(value: number): void {
    const next = this.rating() === value ? 0 : value;
    this.rating.set(next);
    this.send(null, next ? 'Thanks!' : null);
  }

  protected report(e: Event): void {
    e.preventDefault();
    this.reported.set(true);
    this.open.set(false);
    this.send(this.reason, 'Thanks, an admin will take a look.');
  }

  private send(flag: FlagReason | null, thanks: string | null): void {
    this.api.feedback(this.questionId(), this.rating(), flag, flag ? this.note : null).subscribe({
      next: (r) => this.message.set(r.retired ? 'Thanks! Enough players agreed, so it has been taken out of the games.' : thanks),
      error: (err) => this.message.set(errorMessage(err)),
    });
  }
}
