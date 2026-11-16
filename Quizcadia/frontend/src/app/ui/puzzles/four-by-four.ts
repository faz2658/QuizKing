import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { track } from '../../core/analytics';
import { FOUR_BY_FOURS } from '../../core/puzzles/content';
import { dayNumber, findGroup, oneAway, pick, seededShuffle } from '../../core/puzzles/logic';
import { PuzzleProgress } from '../../core/puzzles/progress';

const MISTAKES = 4;
/** Group colours, easiest group first, and their share emoji. */
const TONES = ['easy', 'medium', 'lavender', 'pink'];
const EMOJI = ['🟩', '🟨', '🟦', '🟥'];

interface State { found: number[]; mistakes: number; tries: string[][]; order?: string[]; }

/** 🧩 Four by Four: sort 16 trivia items into four hidden groups of four. */
@Component({
  selector: 'mv-four-by-four',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p class="muted how">Find four groups of four. Pick four tiles that belong together and submit. Some tiles look like they
      fit more than one group, but only one way works. {{ maxMistakes - state().mistakes }} {{ maxMistakes - state().mistakes === 1 ? 'mistake' : 'mistakes' }} left.</p>
    @for (gi of shownGroups(); track gi) {
      <div class="solved fade-up" [class]="'solved fade-up tone-' + tones[gi]">
        <strong>{{ groups()[gi].label }}</strong><span>{{ groups()[gi].items.join(' · ') }}</span>
      </div>
    }
    @if (!result()?.done) {
      <div class="tiles" role="group" aria-label="Tiles">
        @for (t of remaining(); track t) {
          <button type="button" class="tile" [class.on]="picked().includes(t)" [attr.aria-pressed]="picked().includes(t)" (click)="toggle(t)">{{ t }}</button>
        }
      </div>
      <div class="row actions">
        <button type="button" class="btn btn-ghost" (click)="shuffle()">Shuffle</button>
        <button type="button" class="btn btn-ghost" (click)="picked.set([])" [disabled]="!picked().length">Clear</button>
        <span class="spacer"></span>
        <button type="button" class="btn btn-primary" (click)="submit()" [disabled]="picked().length !== 4">Submit four</button>
      </div>
      @if (note()) { <p class="muted note" role="status">{{ note() }}</p> }
    } @else {
      <div class="end" [class.won]="result()!.won" role="status">
        @if (result()!.won) { 🎉 All four groups found with {{ state().mistakes }} {{ state().mistakes === 1 ? 'mistake' : 'mistakes' }}. }
        @else { Out of mistakes. Here are the groups. }
      </div>
    }
  `,
  styles: `
    .how { margin-top: 0; }
    .solved { display: flex; flex-direction: column; align-items: center; gap: 2px; padding: 12px; border-radius: 12px; margin-bottom: 8px; color: #fff; text-align: center; }
    .solved span { font-size: 0.9rem; opacity: 0.95; }
    .tone-easy { background: var(--easy); } .tone-medium { background: var(--medium); }
    .tone-lavender { background: var(--accent-2); } .tone-pink { background: var(--pink); }
    .tiles { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; }
    .tile { min-height: 64px; padding: 6px; border-radius: 12px; border: 1px solid var(--border-strong); background: var(--surface-strong);
      color: var(--text); font: inherit; font-weight: 800; font-size: clamp(0.62rem, 2.6vw, 0.9rem); cursor: pointer; word-break: break-word; }
    .tile.on { background: var(--cta-bg); color: var(--cta-ink); border-color: var(--cta-bg); }
    .actions { margin-top: 14px; }
    .note { margin-bottom: 0; }
    .end { padding: 14px 16px; border-radius: 12px; background: var(--bad-bg); margin-top: 8px; }
    .end.won { background: var(--good-bg); }
  `,
})
export class FourByFourPuzzle {
  readonly day = input.required<string>();
  readonly finished = output<void>();
  private readonly progress = inject(PuzzleProgress);
  protected readonly maxMistakes = MISTAKES;
  protected readonly tones = TONES;

  protected readonly groups = computed(() => pick(FOUR_BY_FOURS, this.day(), 3).groups);
  protected readonly result = computed(() => this.progress.results().four);
  protected readonly state = signal<State>((this.progress.result('four')?.state as State) ?? { found: [], mistakes: 0, tries: [] });
  protected readonly picked = signal<string[]>([]);
  protected readonly note = signal<string | null>(null);
  /** Tile order: today's shared shuffle until the player shuffles (the input day isn't known at construction). */
  private readonly shuffled = signal<string[] | null>(null);
  private readonly order = computed(() =>
    this.shuffled() ?? this.state().order ?? seededShuffle(this.groups().flatMap((g) => g.items), dayNumber(this.day())));

  /** Found groups first; once the game is over, every group. */
  protected readonly shownGroups = computed(() =>
    this.result()?.done ? [...this.state().found, ...[0, 1, 2, 3].filter((i) => !this.state().found.includes(i))] : this.state().found);
  protected readonly remaining = computed(() => {
    const used = new Set(this.state().found.flatMap((i) => this.groups()[i].items));
    return this.order().filter((t) => !used.has(t));
  });

  protected toggle(t: string): void {
    this.note.set(null);
    const p = this.picked();
    this.picked.set(p.includes(t) ? p.filter((x) => x !== t) : p.length < 4 ? [...p, t] : p);
  }

  protected shuffle(): void {
    this.shuffled.set(seededShuffle(this.order(), Date.now() % 100_000));
  }

  protected submit(): void {
    const picked = this.picked();
    const key = [...picked].sort().join('|');
    const s = this.state();
    if (s.tries.some((t) => [...t].sort().join('|') === key)) {
      this.note.set('You already tried those four.');
      return;
    }
    const group = findGroup(this.groups(), picked);
    const next: State = { ...s, tries: [...s.tries, picked], order: this.order() };
    if (group) {
      next.found = [...s.found, this.groups().indexOf(group)];
      this.note.set(null);
    } else {
      next.mistakes = s.mistakes + 1;
      this.note.set(oneAway(this.groups(), picked) ? 'One away! Three of those belong together.' : 'Not a group. Try again.');
    }
    this.picked.set([]);
    this.state.set(next);
    const won = next.found.length === 4;
    if (won || next.mistakes >= MISTAKES) {
      const grid = next.tries.map((t) => t.map((item) => EMOJI[this.groups().findIndex((g) => g.items.includes(item))]).join('')).join('\n');
      const head = won ? `🧩 Four by Four: solved, ${next.mistakes} ${next.mistakes === 1 ? 'mistake' : 'mistakes'}` : '🧩 Four by Four: not solved';
      this.progress.save('four', { done: true, won, summary: `${head}\n${grid}`, state: next });
      track('puzzle_complete', { puzzle: 'four', won });
      this.finished.emit();
    } else {
      this.progress.save('four', { done: false, won: false, summary: '', state: next });
    }
  }
}
