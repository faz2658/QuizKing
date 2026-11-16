import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { track } from '../../core/analytics';
import { LETTER_VAULTS } from '../../core/puzzles/content';
import { LetterMark, markGuess, pick } from '../../core/puzzles/logic';
import { PuzzleProgress } from '../../core/puzzles/progress';

const TRIES = 6;
const EMOJI: Record<LetterMark, string> = { hit: '🟪', near: '🟧', miss: '⬛' };

/** 🔐 Letter Vault: crack a hidden trivia word in six tries, with a category hint and letter marks. */
@Component({
  selector: 'mv-letter-vault',
  imports: [FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p class="muted how">Crack the {{ length() }}-letter word. Category: <strong>{{ entry().category }}</strong>.
      <span class="key hit">A</span> right letter, right spot · <span class="key near">A</span> in the word, elsewhere ·
      <span class="key miss">A</span> not in the word.</p>
    <div class="board" [style.--n]="length()" role="grid" aria-label="Your guesses">
      @for (row of rows(); track $index) {
        <div class="line" role="row">
          @for (cell of row; track $index) {
            <span class="cell" role="gridcell" [class]="'cell ' + (cell.mark ?? '')"
              [attr.aria-label]="cell.letter ? cell.letter + ' ' + (cell.mark === 'hit' ? 'right spot' : cell.mark === 'near' ? 'wrong spot' : 'not in word') : 'empty'">{{ cell.letter }}</span>
          }
        </div>
      }
    </div>
    @if (result()?.done) {
      <div class="end" [class.won]="result()!.won" role="status">
        @if (result()!.won) { 🔓 Cracked it in {{ guesses().length }}/{{ tries }}: <strong>{{ entry().word }}</strong>. }
        @else { 🔒 The word was <strong>{{ entry().word }}</strong>. }
      </div>
    } @else {
      <form class="row guess" (ngSubmit)="guess()">
        <label class="sr-only" for="vault-guess">Your {{ length() }}-letter guess</label>
        <input id="vault-guess" class="input" [(ngModel)]="typed" name="guess" autocomplete="off" autocapitalize="characters"
          spellcheck="false" [maxlength]="length()" [placeholder]="length() + ' letters'" (ngModelChange)="error.set(null)" />
        <button class="btn btn-primary" type="submit">Try it</button>
      </form>
      @if (error()) { <p class="muted" role="alert">{{ error() }}</p> }
    }
  `,
  styles: `
    .how { margin-top: 0; line-height: 2; }
    .key { display: inline-grid; place-items: center; width: 22px; height: 22px; border-radius: 6px; font-weight: 800; font-size: 0.8rem; color: #fff; }
    .board { display: grid; gap: 6px; margin: 4px 0 16px; justify-content: center; }
    .line { display: grid; grid-template-columns: repeat(var(--n), minmax(36px, 52px)); gap: 6px; }
    .cell { aspect-ratio: 1; display: grid; place-items: center; border-radius: 10px; border: 2px solid var(--border-strong);
      font-weight: 900; font-size: 1.35rem; text-transform: uppercase; }
    .hit, .cell.hit { background: var(--accent-2); border-color: var(--accent-2); color: #fff; }
    .near, .cell.near { background: var(--accent); border-color: var(--accent); color: #fff; }
    .miss, .cell.miss { background: var(--text-muted); border-color: var(--text-muted); color: #fff; }
    .guess { justify-content: center; }
    .guess .input { width: 12em; text-transform: uppercase; letter-spacing: 0.2em; text-align: center; }
    .end { padding: 14px 16px; border-radius: 12px; background: var(--bad-bg); text-align: center; }
    .end.won { background: var(--good-bg); }
  `,
})
export class LetterVaultPuzzle {
  readonly day = input.required<string>();
  readonly finished = output<void>();
  private readonly progress = inject(PuzzleProgress);
  protected readonly tries = TRIES;

  protected readonly entry = computed(() => pick(LETTER_VAULTS, this.day(), 7));
  protected readonly length = computed(() => this.entry().word.length);
  protected readonly result = computed(() => this.progress.results().vault);
  protected readonly guesses = signal<string[]>((this.progress.result('vault')?.state as string[]) ?? []);
  protected readonly error = signal<string | null>(null);
  protected typed = '';

  protected readonly rows = computed(() => {
    const word = this.entry().word;
    return Array.from({ length: TRIES }, (_, r) => {
      const g = this.guesses()[r];
      const marks = g ? markGuess(g, word) : [];
      return Array.from({ length: word.length }, (_, i) => ({ letter: g?.[i] ?? '', mark: g ? marks[i] : null }));
    });
  });

  protected guess(): void {
    const g = this.typed.trim().toUpperCase();
    if (this.result()?.done) return;
    if (!/^[A-Z]+$/.test(g) || g.length !== this.length()) {
      this.error.set(`Type a ${this.length()}-letter word (letters only).`);
      return;
    }
    if (this.guesses().includes(g)) {
      this.error.set('You already tried that one.');
      return;
    }
    this.typed = '';
    const all = [...this.guesses(), g];
    this.guesses.set(all);
    const won = g === this.entry().word;
    if (won || all.length >= TRIES) {
      const grid = all.map((x) => markGuess(x, this.entry().word).map((m) => EMOJI[m]).join('')).join('\n');
      const head = won ? `🔐 Letter Vault: ${all.length}/${TRIES}` : `🔐 Letter Vault: X/${TRIES}`;
      this.progress.save('vault', { done: true, won, summary: `${head}\n${grid}`, state: all });
      track('puzzle_complete', { puzzle: 'vault', won });
      this.finished.emit();
    } else {
      this.progress.save('vault', { done: false, won: false, summary: '', state: all });
    }
  }
}
