import { ChangeDetectionStrategy, Component, ElementRef, HostListener, computed, effect, inject, input, output, untracked } from '@angular/core';
import { Feedback, RunnerQuestion } from '../core/quiz-runner';
import { TimerRing } from './timer-ring';
import { DecryptText } from '../motion/decrypt-text';
import { Sparks } from '../motion/sparks';
import { GameFx } from '../core/game-fx';

/** The question card: stem, timer and answer options, with right/wrong feedback. Keys 1-4 pick an option. */
@Component({
  selector: 'mv-quiz-stage',
  imports: [TimerRing, DecryptText],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="card stage">
      <header class="top">
        <div class="meta">
          <span class="count">Question {{ question().number }}<span class="muted"> / {{ total() }}</span></span>
          <ng-content select="[badges]" />
        </div>
        <mv-timer-ring [seconds]="secondsLeft()" [fraction]="fraction()" />
      </header>

      <h2 class="stem"><mv-decrypt-text [text]="question().stem" /></h2>

      <ol class="options" [class.locked]="locked()">
        @for (option of question().options; track option; let i = $index) {
          <li>
            <button type="button" class="option" [class]="stateOf(option)" [disabled]="locked()"
              [attr.aria-pressed]="selected() === option" (click)="choose.emit(option)">
              <span class="key">{{ i + 1 }}</span>
              <span class="label">{{ option }}</span>
            </button>
          </li>
        }
      </ol>

      <p class="feedback" aria-live="polite">
        @if (feedback(); as f) {
          @if (f.timedOut) {
            <span class="bad">{{ f.note ?? "Time's up!" }}</span>
          } @else if (f.correct) {
            <span class="good">Correct! +{{ f.points }}</span>
          } @else {
            <span class="bad">Not quite{{ f.correctAnswer ? ' — it was ' + f.correctAnswer : '' }}</span>
          }
          @if (f.note && !f.timedOut) { <span class="muted"> · {{ f.note }}</span> }
          @if (f.explanation) { <span class="why">💡 {{ f.explanation }}</span> }
        } @else {
          <span class="muted">Worth {{ question().points }} points · press 1–{{ question().options.length }}</span>
        }
      </p>
    </div>
  `,
  styles: `
    .stage { padding: clamp(20px, 4vw, 36px); }
    .top { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 18px; }
    .meta { display: flex; flex-direction: column; gap: 8px; }
    .count { font-weight: 750; font-size: 1.05rem; }
    .stem { font-size: clamp(1.4rem, 3.4vw, 2.1rem); margin: 6px 0 26px; letter-spacing: -0.025em; min-height: 2.2em; }
    .options { list-style: none; padding: 0; margin: 0; display: grid; gap: 12px; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); }
    .option {
      width: 100%; display: flex; align-items: center; gap: 14px; text-align: left;
      font: inherit; font-size: 1.05rem; font-weight: 600; color: var(--text);
      background: var(--surface); border: 1px solid var(--border-strong); border-radius: var(--radius);
      padding: 17px 18px; cursor: pointer; transition: border-color 0.15s, background 0.15s, transform 0.12s, box-shadow 0.2s;
    }
    .option:hover:not(:disabled) { border-color: var(--lavender); transform: translateY(-2px); box-shadow: 0 10px 30px color-mix(in srgb, var(--lavender) 18%, transparent); }
    .option:focus-visible { outline: 3px solid var(--accent-2); outline-offset: 2px; }
    .option:disabled { cursor: default; }
    .key {
      flex: none; width: 30px; height: 30px; display: grid; place-items: center; border-radius: 8px;
      background: var(--bg-sunken); color: var(--text-muted); font-size: 0.85rem; font-weight: 800;
    }
    .option.chosen { border-color: var(--accent-2); }
    .option.right { border-color: var(--good); background: var(--good-bg); box-shadow: 0 0 0 1px var(--good), 0 0 30px color-mix(in srgb, var(--good) 35%, transparent); }
    .option.wrong { border-color: var(--bad); background: var(--bad-bg); animation: shake 0.4s; }
    @keyframes shake { 20%, 60% { transform: translateX(-6px); } 40%, 80% { transform: translateX(6px); } }
    .locked .option:not(.right):not(.wrong):not(.chosen) { opacity: 0.55; }
    .feedback { min-height: 1.6em; margin: 18px 0 0; font-weight: 700; }
    .why { display: block; margin-top: 8px; font-weight: 500; color: var(--text-muted); animation: fade-in 0.3s ease; }
    @keyframes fade-in { from { opacity: 0; transform: translateY(4px); } }
    .good { color: var(--good); }
    .bad { color: var(--bad); }
  `,
})
export class QuizStage {
  readonly question = input.required<RunnerQuestion>();
  readonly total = input.required<number | string>();
  readonly secondsLeft = input.required<number>();
  readonly fraction = input.required<number>();
  readonly selected = input<string | null>(null);
  readonly feedback = input<Feedback | null>(null);
  readonly locked = input(false);
  readonly choose = output<string>();

  private readonly correctAnswer = computed(() => this.feedback()?.correctAnswer ?? null);
  private readonly host = inject(ElementRef<HTMLElement>).nativeElement as HTMLElement;
  private readonly sparks = inject(Sparks);
  private readonly fx = inject(GameFx);

  constructor() {
    // sound and vibration: right, wrong, a combo, and a tick for each of the last three seconds
    effect(() => {
      const f = this.feedback();
      if (f) {
        untracked(() => this.fx.play(f.correct ? (f.note?.startsWith('Combo') ? 'combo' : 'correct') : 'wrong'));
      }
    });
    effect(() => {
      const left = this.secondsLeft();
      if (left > 0 && left <= 3 && !this.locked() && !untracked(this.feedback)) {
        untracked(() => this.fx.play('tick'));
      }
    });
    // celebrate a correct answer on the button that was picked
    effect(() => {
      const f = this.feedback();
      if (f?.correct) {
        queueMicrotask(() => {
          const btn = this.host.querySelector('.option.right');
          if (btn) {
            this.sparks.burst(btn, 'var(--good)', 14);
          }
        });
      }
    });
  }

  protected stateOf(option: string): string {
    const correct = this.correctAnswer();
    if (correct !== null && option.toLowerCase() === correct.toLowerCase()) {
      return 'option right';
    }
    if (this.selected() === option) {
      return this.feedback() ? 'option wrong' : 'option chosen';
    }
    return 'option';
  }

  @HostListener('document:keydown', ['$event'])
  protected onKey(e: KeyboardEvent): void {
    const n = Number(e.key);
    const options = this.question().options;
    if (!this.locked() && Number.isInteger(n) && n >= 1 && n <= options.length && !(e.target instanceof HTMLInputElement)) {
      this.choose.emit(options[n - 1]);
    }
  }
}
