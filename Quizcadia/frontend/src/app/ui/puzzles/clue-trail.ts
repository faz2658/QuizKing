import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { track } from '../../core/analytics';
import { CLUE_TRAILS } from '../../core/puzzles/content';
import { clueTrailScore, matchesAnswer, pick } from '../../core/puzzles/logic';
import { PuzzleProgress } from '../../core/puzzles/progress';

interface State { shown: number; guesses: string[]; }

/** 🕵️ Clue Trail: name the mystery answer; each wrong guess (or "next clue") reveals an easier clue. */
@Component({
  selector: 'mv-clue-trail',
  imports: [FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p class="muted how">Today's mystery: <strong>{{ entry().category.toLowerCase() }}</strong>. Name it from the clues: they get
      easier as you go, and the fewer you need, the more points you score. A wrong guess reveals the next clue.</p>
    <ol class="clues">
      @for (c of entry().clues; track $index; let i = $index) {
        @if (i < state().shown) { <li class="clue fade-up"><span class="n">{{ i + 1 }}</span>{{ c }}</li> }
        @else { <li class="clue hidden"><span class="n">{{ i + 1 }}</span>Hidden clue</li> }
      }
    </ol>
    @if (state().guesses.length) {
      <p class="muted tried">Tried: @for (g of state().guesses; track $index) { <span class="chip">{{ g }}</span> }</p>
    }
    @if (result()?.done) {
      <div class="end" [class.won]="result()!.won" role="status">
        @if (result()!.won) { 🎉 It's <strong>{{ entry().answer }}</strong>. Solved with {{ state().shown }} {{ state().shown === 1 ? 'clue' : 'clues' }}: {{ score() }} / {{ entry().clues.length }} points. }
        @else { The answer was <strong>{{ entry().answer }}</strong>. Better luck tomorrow! }
      </div>
    } @else {
      <form class="row guess" (ngSubmit)="guess()">
        <label class="sr-only" for="clue-guess">Your answer</label>
        <input id="clue-guess" class="input" [(ngModel)]="typed" name="guess" autocomplete="off" placeholder="Type your answer" />
        <button class="btn btn-primary" type="submit" [disabled]="!typed.trim()">Guess</button>
        @if (state().shown < entry().clues.length) {
          <button class="btn btn-ghost" type="button" (click)="reveal()">Next clue (−1 point)</button>
        }
      </form>
    }
  `,
  styles: `
    .how { margin-top: 0; }
    .clues { list-style: none; padding: 0; margin: 0 0 14px; display: grid; gap: 8px; }
    .clue { display: flex; gap: 12px; align-items: baseline; padding: 10px 14px; border-radius: 12px; background: var(--surface); border: 1px solid var(--border); }
    .clue.hidden { color: var(--text-muted); opacity: 0.6; font-style: italic; }
    .n { font-weight: 800; color: var(--accent); min-width: 1.2em; }
    .tried .chip { margin: 0 4px 4px 0; }
    .guess .input { flex: 1; min-width: 180px; }
    .end { padding: 14px 16px; border-radius: 12px; background: var(--bad-bg); }
    .end.won { background: var(--good-bg); }
  `,
})
export class ClueTrailPuzzle {
  readonly day = input.required<string>();
  readonly finished = output<void>();
  private readonly progress = inject(PuzzleProgress);

  protected readonly entry = computed(() => pick(CLUE_TRAILS, this.day(), 0));
  protected readonly result = computed(() => this.progress.results().clue);
  protected readonly state = signal<State>((this.progress.result('clue')?.state as State) ?? { shown: 1, guesses: [] });
  protected readonly score = computed(() => clueTrailScore(this.state().shown, this.entry().clues.length));
  protected typed = '';

  protected guess(): void {
    const g = this.typed.trim();
    if (!g || this.result()?.done) return;
    this.typed = '';
    const e = this.entry();
    if (matchesAnswer(g, e.answer, e.aliases)) {
      this.finish(true, this.state());
      return;
    }
    const s: State = { shown: this.state().shown, guesses: [...this.state().guesses, g] };
    if (s.shown >= e.clues.length) {
      this.finish(false, s);
    } else {
      this.update({ ...s, shown: s.shown + 1 });
    }
  }

  protected reveal(): void {
    this.update({ ...this.state(), shown: Math.min(this.state().shown + 1, this.entry().clues.length) });
  }

  private update(s: State): void {
    this.state.set(s);
    this.progress.save('clue', { done: false, won: false, summary: '', state: s });
  }

  private finish(won: boolean, s: State): void {
    this.state.set(s);
    const total = this.entry().clues.length;
    const summary = won
      ? `🕵️ Clue Trail: solved with ${s.shown} ${s.shown === 1 ? 'clue' : 'clues'} (${clueTrailScore(s.shown, total)}/${total})`
      : '🕵️ Clue Trail: not solved';
    this.progress.save('clue', { done: true, won, summary, state: s });
    track('puzzle_complete', { puzzle: 'clue', won });
    this.finished.emit();
  }
}
