import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable, Subscription, switchMap } from 'rxjs';
import { MultiplayerApi } from '../core/api';
import { AuthService } from '../core/auth.service';
import { errorMessage } from '../core/http-errors';
import { livePoll } from '../core/live-poll';
import { MatchView, RoomStatus } from '../core/models';
import { followMatch, normalizeRoomCode, ordinal, points } from '../core/room-quiz';
import { NAME_MAX, nameValidator } from '../core/validation';
import { FormControl } from '@angular/forms';

const LETTERS = ['A', 'B', 'C', 'D', 'E', 'F'];
const NAME_KEY = 'quizcadia.liveName';

function rememberedName(): string {
  try {
    return localStorage.getItem(NAME_KEY) ?? '';
  } catch {
    return '';
  }
}

function rememberName(name: string): void {
  try {
    localStorage.setItem(NAME_KEY, name);
  } catch {
    // private mode: nothing to remember
  }
}

/**
 * A phone in a live room quiz, Slido style: scan the QR code on the big screen (it opens /live/<code>), type a name
 * and play. No account needed: without one the phone joins as a guest. Then it shows big answer buttons for each
 * question, locks the answer in, and between questions says whether it was right and where you stand.
 */
@Component({
  selector: 'mv-live',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="phone">
      @if (fatal()) {
        <div class="panel">
          <h1>Can't join</h1>
          <div class="alert" role="alert">{{ fatal() }}</div>
          <a class="btn btn-primary" routerLink="/live">Enter another code</a>
        </div>
      } @else if (!code()) {
        <form class="panel" (submit)="$event.preventDefault(); goTo()">
          <h1>Join a quiz</h1>
          <p class="muted">Type the code on the big screen.</p>
          <label for="code">Room code</label>
          <input id="code" class="input big-input" [value]="typedCode()" (input)="typedCode.set($any($event.target).value)"
            placeholder="KING-1234" autocomplete="off" autocapitalize="characters" enterkeyhint="go" inputmode="text" />
          <button class="btn btn-primary tap" type="submit" [disabled]="!typedCode().trim()">Next</button>
        </form>
      } @else if (match(); as m) {
        @switch (m.phase) {
          @case ('GET_READY') {
            <div class="panel center">
              <span class="badge"><span class="dot"></span>{{ m.totalQuestions }} questions</span>
              <h1>Get ready, {{ myName() }}!</h1>
              <p class="muted">The first question is coming up. Look at the big screen.</p>
            </div>
          }
          @case ('QUESTION') {
            @if (m.currentQuestion; as q) {
              <div class="play">
                <div class="meta">
                  <span>{{ q.number }} / {{ m.totalQuestions }}</span>
                  <span class="timer" [class.low]="secondsLeft() <= 5" role="timer">{{ secondsLeft() }}</span>
                </div>
                <p class="stem">{{ q.stem }}</p>
                @if (q.yourAnswer || picked(); as mine) {
                  <div class="locked" role="status">
                    <span class="lock" aria-hidden="true">🔒</span>
                    <strong>Answer locked in</strong>
                    <span class="choice">{{ mine }}</span>
                    <span class="muted">Waiting for the others…</span>
                  </div>
                } @else {
                  <div class="answers" role="group" aria-label="Answers">
                    @for (o of q.options; track $index; let i = $index) {
                      <button type="button" class="answer" [class]="'c' + i" [disabled]="sending()" (click)="answer(m, q.questionId, o)">
                        <b>{{ letters[i] }}</b><span>{{ o }}</span>
                      </button>
                    }
                  </div>
                }
                @if (note()) { <p class="muted" role="alert">{{ note() }}</p> }
              </div>
            }
          }
          @case ('REVEAL') {
            <div class="panel center result" [class.good]="m.you?.lastCorrect" [class.bad]="m.you?.lastAnswer && !m.you.lastCorrect">
              @if (m.you?.lastCorrect) {
                <span class="mark" aria-hidden="true">✓</span>
                <h1>Correct!</h1>
                <p class="gain">+{{ fmt(m.you.lastPoints) }}</p>
              } @else if (m.you?.lastAnswer) {
                <span class="mark" aria-hidden="true">✗</span>
                <h1>Not quite</h1>
                <p>The answer was <strong>{{ m.lastQuestion?.correctAnswer }}</strong></p>
              } @else {
                <span class="mark" aria-hidden="true">⏱</span>
                <h1>Time's up</h1>
                <p>The answer was <strong>{{ m.lastQuestion?.correctAnswer }}</strong></p>
              }
              <p class="rank">{{ rankLine(m) }}</p>
              <p class="muted">{{ fmt(m.you?.score ?? 0) }} points · next question soon</p>
            </div>
          }
          @case ('FINISHED') {
            <div class="panel center">
              <span class="mark" aria-hidden="true">{{ (m.you?.rank ?? 99) <= 3 ? ['🥇', '🥈', '🥉'][(m.you?.rank ?? 1) - 1] : '🎉' }}</span>
              <h1>{{ m.you?.rank ? 'You finished ' + ordinal(m.you!.rank!) : 'Quiz over' }}</h1>
              <p class="rank">{{ fmt(m.you?.score ?? 0) }} points · {{ myRight(m) }} of {{ m.lastQuestion?.number ?? m.totalQuestions }} right</p>
              <p class="muted">of {{ m.playerCount }} {{ m.playerCount === 1 ? 'player' : 'players' }}</p>
              <a class="btn btn-primary tap" routerLink="/">Play more on QuizCadia</a>
            </div>
          }
        }
      } @else if (room(); as r) {
        <div class="panel center">
          @if (r.state === 'CLOSED') {
            <h1>The host closed this room</h1>
            <a class="btn btn-primary tap" routerLink="/live">Join another</a>
          } @else if (r.state === 'STARTED') {
            <h1>Starting…</h1>
          } @else {
            <span class="badge"><span class="dot"></span>{{ r.code }}</span>
            <h1>You're in, {{ myName() }}!</h1>
            <p class="muted">Look at the big screen. The quiz starts when the host is ready.</p>
            <p class="who">{{ r.players.length }} {{ r.players.length === 1 ? 'player' : 'players' }} joined</p>
            <button class="btn btn-ghost" type="button" (click)="leave()">Leave</button>
          }
        </div>
      } @else {
        <form class="panel" (submit)="$event.preventDefault(); join()">
          <span class="badge"><span class="dot"></span>{{ code() }}</span>
          <h1>Join the quiz</h1>
          @if (account(); as a) {
            <p>Playing as <strong>{{ a }}</strong>.</p>
          } @else {
            <label for="name">Your name</label>
            <input id="name" class="input big-input" [value]="name.value" (input)="name.setValue($any($event.target).value)"
              [attr.maxlength]="nameMax" placeholder="Name shown on the screen" autocomplete="nickname" enterkeyhint="go"
              [attr.aria-invalid]="!!nameError()" aria-describedby="name-error" />
            @if (nameError()) { <p class="error" id="name-error">{{ nameError() }}</p> }
          }
          @if (note()) { <div class="alert" role="alert">{{ note() }}</div> }
          <button class="btn btn-primary tap" type="submit" [disabled]="busy()">{{ busy() ? 'Joining…' : 'Join' }}</button>
          <p class="muted small">No account needed.</p>
        </form>
      }
    </section>
  `,
  styles: `
    :host { display: block; }
    .phone { max-width: 520px; margin: 0 auto; padding: 8px 0 32px; }
    .panel { display: flex; flex-direction: column; gap: 12px; padding: 24px 20px; border-radius: 22px; background: var(--surface);
      border: 1px solid var(--border); }
    .center { align-items: center; text-align: center; }
    .panel:not(.center) > .badge { align-self: flex-start; }
    h1 { margin: 4px 0; font-size: clamp(1.6rem, 7vw, 2.2rem); line-height: 1.15; }
    label { font-weight: 700; }
    .big-input { font-size: 1.25rem; padding: 14px 16px; min-height: 52px; width: 100%; box-sizing: border-box; }
    .tap { min-height: 56px; font-size: 1.15rem; width: 100%; justify-content: center; }
    .error { color: var(--bad); margin: 0; font-weight: 600; }
    .big-input { font-size: 1.2rem; }
    .small { font-size: 0.85rem; margin: 0; text-align: center; }
    .who { font-weight: 700; }
    .meta { display: flex; justify-content: space-between; align-items: center; font-weight: 800; color: var(--text-muted); }
    .timer { font-size: 2rem; color: var(--text); }
    .timer.low { color: var(--bad); }
    .stem { font-size: clamp(1.1rem, 5vw, 1.4rem); font-weight: 700; margin: 6px 0 14px; line-height: 1.3; }
    .answers { display: grid; grid-template-columns: 1fr; gap: 12px; }
    .answer { display: flex; align-items: center; gap: 14px; min-height: 72px; padding: 14px 18px; border: 0; border-radius: 18px;
      color: #fff; font: inherit; font-size: 1.15rem; font-weight: 750; text-align: left; cursor: pointer; touch-action: manipulation;
      box-shadow: 0 4px 0 rgba(0, 0, 0, 0.18); }
    .answer:active { transform: translateY(2px); box-shadow: 0 2px 0 rgba(0, 0, 0, 0.18); }
    .answer:disabled { opacity: 0.7; }
    .answer b { font-size: 1.4rem; opacity: 0.85; min-width: 1.2ch; }
    .c0 { background: #e2445c; } .c1 { background: #2f7ae5; } .c2 { background: #c98a12; } .c3 { background: #1f9e62; }
    .c4 { background: #8a5cf6; } .c5 { background: #14889a; }
    .locked { display: flex; flex-direction: column; align-items: center; gap: 8px; text-align: center; padding: 28px 16px;
      border-radius: 20px; background: var(--surface-strong); border: 2px dashed var(--accent); }
    .lock { font-size: 2.4rem; }
    .choice { font-size: 1.3rem; font-weight: 800; }
    .result.good { border-color: var(--good); background: var(--good-bg); }
    .result.bad { border-color: var(--bad); background: var(--bad-bg); }
    .mark { font-size: 3.4rem; line-height: 1; }
    .result.good .mark { color: var(--good); }
    .result.bad .mark { color: var(--bad); }
    .gain { font-size: 2rem; font-weight: 900; color: var(--good); margin: 0; }
    .rank { font-size: 1.25rem; font-weight: 800; margin: 4px 0 0; }
    @media (min-width: 560px) { .answers { grid-template-columns: 1fr 1fr; } }
  `,
})
export class Live implements OnInit {
  /** From /live/<code>; empty on /live, where the code is typed. */
  readonly code = input<string>('');
  private readonly api = inject(MultiplayerApi);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly letters = LETTERS;
  protected readonly nameMax = NAME_MAX;
  protected readonly fmt = points;
  protected readonly ordinal = ordinal;
  protected readonly name = new FormControl(rememberedName(), { nonNullable: true, validators: nameValidator });
  protected readonly typedCode = signal('');
  protected readonly room = signal<RoomStatus | null>(null);
  protected readonly match = signal<MatchView | null>(null);
  protected readonly fatal = signal<string | null>(null);
  protected readonly note = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly sending = signal(false);
  protected readonly submitted = signal(false);
  /** The answer just tapped, until the server's view has it. */
  protected readonly picked = signal<string | null>(null);
  private readonly now = signal(Date.now());
  private readonly deadline = signal(0);
  private readonly nameValue = signal(this.name.value);
  private sub = new Subscription();

  /** An account's own name (accounts don't type one); null for guests and new players. */
  protected readonly account = computed(() => {
    const u = this.auth.user();
    return u && u.role !== 'GUEST' ? u.userName : null;
  });
  protected readonly me = computed(() => this.auth.user()?.userId ?? 0);
  protected readonly myName = computed(() => this.auth.user()?.userName ?? this.nameValue());
  protected readonly nameError = computed(() => {
    if (!this.submitted()) return null;
    this.nameValue();
    const e = nameValidator(this.name);
    return e ? (Object.values(e)[0] as string) : null;
  });
  protected readonly secondsLeft = computed(() => Math.max(0, Math.ceil((this.deadline() - this.now()) / 1000)));

  constructor() {
    this.name.valueChanges.subscribe((v) => this.nameValue.set(v));
    const tick = setInterval(() => this.now.set(Date.now()), 250);
    this.destroyRef.onDestroy(() => {
      clearInterval(tick);
      this.sub.unsubscribe();
    });
  }

  ngOnInit(): void {
    if (!this.code()) return;
    // back on this page (a reload, or the phone slept): already in the room means straight back into the quiz
    if (this.auth.isLoggedIn()) {
      this.api.room(this.code()).subscribe({
        next: (r) => {
          if (r.players.some((p) => p.userId === this.me())) this.watch(r);
        },
        error: () => undefined, // the join form says what's wrong once they try
      });
    }
  }

  protected goTo(): void {
    const code = normalizeRoomCode(this.typedCode());
    if (code) this.router.navigate(['/live', code]);
  }

  protected join(): void {
    this.submitted.set(true);
    this.note.set(null);
    const name = this.name.value.trim();
    const u = this.auth.user();
    const needGuest = !u || (u.role === 'GUEST' && u.userName !== name);
    if (needGuest && nameValidator(this.name)) return;
    this.busy.set(true);
    if (needGuest) rememberName(name);
    const join: Observable<RoomStatus> = needGuest
      ? this.auth.playAsGuest(name).pipe(switchMap(() => this.api.joinRoom(this.code())))
      : this.api.joinRoom(this.code());
    join.subscribe({
      next: (r) => {
        this.busy.set(false);
        this.watch(r);
      },
      error: (e) => {
        this.busy.set(false);
        if (e instanceof HttpErrorResponse && e.status === 404) this.fatal.set(`No quiz with code ${this.code()}. Check the big screen.`);
        else this.note.set(errorMessage(e));
      },
    });
  }

  protected leave(): void {
    this.sub.unsubscribe();
    this.api.leaveRoom(this.code()).subscribe({ complete: () => this.router.navigateByUrl('/live'), error: () => this.router.navigateByUrl('/live') });
  }

  protected answer(m: MatchView, questionId: number, option: string): void {
    this.sending.set(true);
    this.picked.set(option);
    this.note.set(null);
    this.api.answer(m.matchId, this.me(), questionId, option).subscribe({
      next: (a) => {
        this.sending.set(false);
        this.show(a.match);
      },
      error: (e) => {
        this.sending.set(false);
        this.picked.set(null);
        this.note.set(errorMessage(e));
      },
    });
  }

  protected rankLine(m: MatchView): string {
    const rank = m.you?.rank;
    return rank ? `You're ${ordinal(rank)} of ${m.playerCount}` : '';
  }

  protected myRight(m: MatchView): number {
    return m.standings.find((p) => p.userId === this.me())?.questionsWon ?? 0;
  }

  /** In the room: wait for the start, then follow the quiz. */
  private watch(r: RoomStatus): void {
    this.room.set(r);
    if (r.state === 'STARTED' && r.matchId) {
      this.play(r.matchId);
      return;
    }
    this.sub.add(
      livePoll(() => this.api.room(this.code()), { every: 1500, max: 3000, firstAfter: 1500 }).subscribe({
        next: (n) => {
          this.room.set(n);
          if (n.state === 'STARTED' && n.matchId) this.play(n.matchId);
        },
        error: (e) => this.fatal.set(errorMessage(e)),
      }),
    );
  }

  private play(matchId: string): void {
    this.sub.unsubscribe();
    this.sub = new Subscription();
    this.sub.add(followMatch(this.api, matchId, this.me()).subscribe((m) => this.show(m)));
  }

  private show(m: MatchView): void {
    const prev = this.match();
    if (prev?.currentQuestion?.questionId !== m.currentQuestion?.questionId || m.phase !== 'QUESTION') this.picked.set(null);
    this.match.set(m);
    this.now.set(Date.now());
    this.deadline.set(Date.now() + (m.currentQuestion?.secondsRemaining ?? 0) * 1000);
  }
}
