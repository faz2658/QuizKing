import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { track } from '../../core/analytics';
import { BRAIN_TEASERS } from '../../core/puzzles/content';
import { pick } from '../../core/puzzles/logic';
import { PuzzleProgress } from '../../core/puzzles/progress';

/** 🧠 Brain Teaser: one riddle or logic question a day, one try. */
@Component({
  selector: 'mv-brain-teaser',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p class="muted how">Think it through: you only get one try.</p>
    <h3 class="q">{{ entry().question }}</h3>
    <div class="options" role="group" aria-label="Answers">
      @for (o of entry().options; track $index; let i = $index) {
        <button type="button" class="option"
          [class.right]="chosen() !== null && i === entry().answer" [class.wrong]="chosen() === i && i !== entry().answer"
          [disabled]="chosen() !== null" (click)="choose(i)">{{ o }}</button>
      }
    </div>
    @if (result()?.done) {
      <div class="end" [class.won]="result()!.won" role="status">
        <strong>{{ result()!.won ? '🧠 Correct!' : 'Not quite.' }}</strong> {{ entry().why }}
      </div>
    }
  `,
  styles: `
    .how { margin-top: 0; }
    .q { font-size: 1.25rem; line-height: 1.35; margin: 0 0 16px; }
    .options { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
    .option { padding: 14px; border-radius: 12px; border: 1px solid var(--border-strong); background: var(--surface-strong);
      color: var(--text); font: inherit; font-weight: 700; cursor: pointer; text-align: left; }
    .option:disabled { cursor: default; opacity: 0.75; }
    .option.right { background: var(--good-bg); border-color: var(--good); color: var(--good); opacity: 1; }
    .option.wrong { background: var(--bad-bg); border-color: var(--bad); color: var(--bad); opacity: 1; }
    .end { padding: 14px 16px; border-radius: 12px; background: var(--bad-bg); margin-top: 14px; }
    .end.won { background: var(--good-bg); }
    @media (max-width: 480px) { .options { grid-template-columns: 1fr; } }
  `,
})
export class BrainTeaserPuzzle {
  readonly day = input.required<string>();
  readonly finished = output<void>();
  private readonly progress = inject(PuzzleProgress);

  protected readonly entry = computed(() => pick(BRAIN_TEASERS, this.day(), 11));
  protected readonly result = computed(() => this.progress.results().teaser);
  protected readonly chosen = computed(() => (this.result()?.state as number | undefined) ?? null);

  protected choose(i: number): void {
    if (this.result()?.done) return;
    const won = i === this.entry().answer;
    this.progress.save('teaser', { done: true, won, summary: `🧠 Brain Teaser: ${won ? '✅' : '❌'}`, state: i });
    track('puzzle_complete', { puzzle: 'teaser', won });
    this.finished.emit();
  }
}
