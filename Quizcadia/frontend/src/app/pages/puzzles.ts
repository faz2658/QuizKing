import { ChangeDetectionStrategy, Component, ElementRef, computed, inject, signal, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { shareLink } from '../core/invite';
import { secondsUntilNextDay } from '../core/puzzles/logic';
import { PUZZLE_KINDS, PuzzleKind, PuzzleProgress } from '../core/puzzles/progress';
import { untilNext } from '../ui/daily-banner';
import { BrainTeaserPuzzle } from '../ui/puzzles/brain-teaser';
import { ClueTrailPuzzle } from '../ui/puzzles/clue-trail';
import { FourByFourPuzzle } from '../ui/puzzles/four-by-four';
import { LetterVaultPuzzle } from '../ui/puzzles/letter-vault';

interface Info { kind: PuzzleKind; icon: string; name: string; blurb: string; }

const INFO: Info[] = [
  { kind: 'clue', icon: '🕵️', name: 'Clue Trail', blurb: 'Name the mystery answer from as few clues as you can.' },
  { kind: 'vault', icon: '🔐', name: 'Letter Vault', blurb: 'Crack a hidden trivia word in six tries.' },
  { kind: 'four', icon: '🧩', name: 'Four by Four', blurb: 'Sort 16 items into four hidden groups.' },
  { kind: 'teaser', icon: '🧠', name: 'Brain Teaser', blurb: 'One riddle, one try.' },
];

/** Puzzle of the Day: four daily puzzles, the same for everyone. Play any of them, or all four in a row. */
@Component({
  selector: 'mv-puzzles',
  imports: [RouterLink, ClueTrailPuzzle, LetterVaultPuzzle, FourByFourPuzzle, BrainTeaserPuzzle],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="head">
      <span class="badge"><span class="dot"></span>Puzzle of the Day · {{ progress.day() }}</span>
      <h1>Four puzzles. One day. Same for everyone.</h1>
      <p class="muted">Play any of them, or all four. New puzzles in {{ next() }}.
        @if (progress.streak() > 0) { <strong class="streak">🔥 {{ progress.streak() }}-day puzzle streak</strong> }</p>
      <div class="row">
        @if (progress.doneCount() < 4) {
          <button type="button" class="btn btn-primary" (click)="playAll()">{{ progress.doneCount() ? 'Continue all four' : 'Play all four' }}</button>
        }
        @if (progress.doneCount() > 0) {
          <button type="button" class="btn" (click)="share()">📤 Share today's results ({{ progress.doneCount() }}/4)</button>
        }
      </div>
      @if (shareNote()) { <p class="muted" role="status">{{ shareNote() }}</p> }
      @if (!auth.isLoggedIn()) {
        <p class="muted small">Your puzzle progress is saved in this browser. <a routerLink="/welcome">Sign up</a> to play the quiz games too.</p>
      }
    </section>

    <div class="cards">
      @for (p of info; track p.kind) {
        <button type="button" class="card pcard" [class.active]="open() === p.kind" (click)="show(p.kind)" [attr.aria-pressed]="open() === p.kind">
          <span class="icon" aria-hidden="true">{{ p.icon }}</span>
          <span class="name">{{ p.name }}</span>
          <span class="muted blurb">{{ p.blurb }}</span>
          <span class="status" [class.won]="progress.results()[p.kind]?.won" [class.lost]="progress.results()[p.kind]?.done && !progress.results()[p.kind]?.won">
            {{ status(p.kind) }}
          </span>
        </button>
      }
    </div>

    @if (open(); as kind) {
      <section #stage class="card stage fade-up" [attr.aria-label]="infoFor(kind).name">
        <h2>{{ infoFor(kind).icon }} {{ infoFor(kind).name }}</h2>
        @switch (kind) {
          @case ('clue') { <mv-clue-trail [day]="progress.day()" (finished)="finished()" /> }
          @case ('vault') { <mv-letter-vault [day]="progress.day()" (finished)="finished()" /> }
          @case ('four') { <mv-four-by-four [day]="progress.day()" (finished)="finished()" /> }
          @case ('teaser') { <mv-brain-teaser [day]="progress.day()" (finished)="finished()" /> }
        }
        @if (progress.results()[kind]?.done) {
          <div class="row after">
            @if (nextUnplayed(); as n) {
              <button type="button" class="btn btn-primary" (click)="show(n)">Next: {{ infoFor(n).icon }} {{ infoFor(n).name }}</button>
            } @else {
              <span class="muted">🏁 You've played all four today. Come back tomorrow!</span>
            }
          </div>
        }
      </section>
    }
  `,
  styles: `
    .head { display: flex; flex-direction: column; gap: 10px; margin-bottom: 22px; }
    .head .badge { align-self: flex-start; }
    .head h1 { margin: 6px 0 0; }
    .head p { margin: 0; }
    .streak { color: var(--accent); margin-left: 8px; }
    .small { font-size: 0.85rem; }
    .cards { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin-bottom: 22px; }
    .pcard { display: flex; flex-direction: column; align-items: flex-start; gap: 6px; padding: 18px; text-align: left; cursor: pointer;
      font: inherit; color: var(--text); border: 1px solid var(--border); }
    .pcard.active { border-color: var(--accent); box-shadow: 0 0 0 2px color-mix(in srgb, var(--accent) 40%, transparent); }
    .icon { font-size: 1.8rem; }
    .name { font-weight: 850; font-size: 1.05rem; }
    .blurb { font-size: 0.88rem; flex: 1; }
    .status { font-size: 0.8rem; font-weight: 800; color: var(--accent); }
    .status.won { color: var(--good); } .status.lost { color: var(--bad); }
    .stage { padding: 24px; }
    .stage h2 { margin-top: 0; }
    .after { margin-top: 16px; }
    @media (max-width: 860px) { .cards { grid-template-columns: repeat(2, 1fr); } }
  `,
})
export class Puzzles {
  protected readonly progress = inject(PuzzleProgress);
  protected readonly auth = inject(AuthService);
  protected readonly info = INFO;
  protected readonly open = signal<PuzzleKind | null>(null);
  protected readonly shareNote = signal<string | null>(null);
  protected readonly next = signal(untilNext(secondsUntilNextDay()));
  private readonly stage = viewChild<ElementRef<HTMLElement>>('stage');

  protected readonly nextUnplayed = computed(() => PUZZLE_KINDS.find((k) => !this.progress.results()[k]?.done) ?? null);

  protected infoFor(kind: PuzzleKind): Info {
    return INFO.find((i) => i.kind === kind)!;
  }

  protected status(kind: PuzzleKind): string {
    const r = this.progress.results()[kind];
    if (!r) return 'Not played yet';
    if (!r.done) return 'In progress';
    return r.won ? '✓ Solved' : '✗ Not solved';
  }

  protected show(kind: PuzzleKind): void {
    this.open.set(kind);
    setTimeout(() => this.stage()?.nativeElement.scrollIntoView?.({ behavior: 'smooth', block: 'start' }));
  }

  protected playAll(): void {
    this.show(this.nextUnplayed() ?? 'clue');
  }

  protected finished(): void {
    this.next.set(untilNext(secondsUntilNextDay()));
  }

  protected async share(): Promise<void> {
    const lines = PUZZLE_KINDS.map((k) => this.progress.results()[k]).filter((r) => r?.done).map((r) => r!.summary);
    const text = [`✨ QuizCadia Puzzles ${this.progress.day()}`, ...lines].join('\n');
    const how = await shareLink('QuizCadia Puzzles', text, `${location.origin}/puzzles`);
    this.shareNote.set(how === 'copied' ? 'Copied! Paste it in your group chat.' : how === 'failed' ? text : null);
  }
}
