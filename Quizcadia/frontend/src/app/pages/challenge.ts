import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, map, of, startWith, switchMap } from 'rxjs';
import { QuizApi } from '../core/api';
import { errorMessage } from '../core/http-errors';
import { ChallengeView } from '../core/models';

/** Landing page of a challenge link: who to beat, and the button to try. */
@Component({
  selector: 'mv-challenge',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (state(); as st) {
      @if (st.error) {
        <section class="card box"><h2>Challenge not found</h2><div class="alert" role="alert">{{ st.error }}</div><a class="btn" routerLink="/">Back to games</a></section>
      } @else if (st.challenge; as c) {
        <section class="card box fade-up">
          <span class="icon" aria-hidden="true">⚔️</span>
          <span class="muted">{{ c.yourStatus === 'OWN' ? 'Your challenge' : c.challengerName + ' challenges you' }}</span>
          <h1>{{ c.gameName }}</h1>
          <p class="beat">Score to beat: <strong>{{ c.score }}</strong> / {{ c.maxScore }} · {{ c.correctAnswers }}/{{ c.totalQuestions }} right</p>
          @switch (c.yourStatus) {
            @case ('NOT_STARTED') {
              <p class="muted">Same {{ c.totalQuestions }} questions, same order. One attempt. Lifelines allowed.</p>
              <a class="btn btn-primary" [routerLink]="['/challenge', c.id, 'play']">Accept the challenge</a>
            }
            @case ('IN_PROGRESS') { <a class="btn btn-primary" [routerLink]="['/challenge', c.id, 'play']">Resume</a> }
            @case ('COMPLETED') {
              <p class="verdict">{{ (c.yourScore ?? 0) > c.score ? 'You won! 🏆' : (c.yourScore ?? 0) === c.score ? "It's a tie!" : c.challengerName + ' wins this time.' }}
                You scored {{ c.yourScore }}.</p>
              <a class="btn" [routerLink]="['/result', c.yourSessionId]">See your answers</a>
            }
            @case ('OWN') { <p class="muted">Send this page's link to friends: they'll play exactly your questions.</p> }
          }
        </section>
      } @else {
        <div class="skeleton" style="height: 280px; max-width: 560px; margin: 40px auto"></div>
      }
    }
  `,
  styles: `
    .box { max-width: 560px; margin: 40px auto; text-align: center; display: flex; flex-direction: column; align-items: center; gap: 10px; padding: 40px; }
    .icon { font-size: 3rem; }
    h1 { margin: 0; }
    .beat { font-size: 1.15rem; }
    .verdict { font-size: 1.2rem; font-weight: 800; }
  `,
})
export class Challenge {
  readonly challengeId = input.required<string>();
  private readonly api = inject(QuizApi);
  protected readonly state = toSignal(
    toObservable(this.challengeId).pipe(
      switchMap((id) =>
        this.api.challenge(id).pipe(
          map((challenge) => ({ challenge, error: null as string | null })),
          catchError((e) => of({ challenge: null as ChallengeView | null, error: errorMessage(e) })),
          startWith({ challenge: null as ChallengeView | null, error: null as string | null }),
        ),
      ),
    ),
  );
}
