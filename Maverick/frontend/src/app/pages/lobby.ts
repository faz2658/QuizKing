import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Subscription, switchMap, timer } from 'rxjs';
import { MultiplayerApi } from '../core/api';
import { AuthService } from '../core/auth.service';
import { errorMessage } from '../core/http-errors';
import { LobbyStatus } from '../core/models';

@Component({
  selector: 'mv-lobby',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card lobby">
      @if (error()) {
        <h2>Matchmaking is unavailable</h2>
        <div class="alert" role="alert">{{ error() }}</div>
        <a class="btn" routerLink="/">Back to games</a>
      } @else if (status()?.state === 'EXPIRED') {
        <h2>Nobody else showed up</h2>
        <p class="muted">A match needs at least two players. Invite a friend to join this game and try again.</p>
        <div class="row center-row"><button class="btn btn-primary" (click)="join()">Try again</button><a class="btn" routerLink="/">Back to games</a></div>
      } @else {
        <div class="pulse" aria-hidden="true"><span></span></div>
        <h2>{{ status()?.state === 'MATCHED' ? 'Match found!' : 'Looking for opponents…' }}</h2>
        <p class="muted" aria-live="polite">
          {{ (status()?.players?.length ?? 1) }} player{{ (status()?.players?.length ?? 1) === 1 ? '' : 's' }} in the lobby
          @if (secondsToStart() !== null && status()?.state === 'WAITING') { · starting in {{ secondsToStart() }}s if 2+ players }
        </p>
        <ul class="players">
          @for (p of status()?.players ?? []; track p) {
            <li [class.me]="p === me">{{ p === me ? 'You' : 'Player ' + (p % 10000) }}</li>
          }
        </ul>
        <button class="btn btn-ghost" (click)="cancel()">Cancel</button>
      }
    </section>
  `,
  styles: `
    .lobby { max-width: 560px; margin: 40px auto; text-align: center; display: flex; flex-direction: column; align-items: center; gap: 10px; padding: 40px; }
    .center-row { justify-content: center; }
    .pulse { position: relative; width: 84px; height: 84px; margin-bottom: 8px; }
    .pulse::before, .pulse span { content: ''; position: absolute; inset: 0; border-radius: 50%; background: var(--accent-2); }
    .pulse::before { animation: ping 1.6s ease-out infinite; opacity: 0.5; }
    .pulse span { inset: 18px; background: var(--accent); }
    @keyframes ping { from { transform: scale(0.6); opacity: 0.6; } to { transform: scale(1.5); opacity: 0; } }
    .players { list-style: none; padding: 0; display: flex; gap: 8px; flex-wrap: wrap; justify-content: center; }
    .players li { padding: 6px 14px; border-radius: 999px; background: var(--surface); border: 1px solid var(--border); font-weight: 650; }
    .players li.me { border-color: var(--accent); }
  `,
})
export class Lobby implements OnInit {
  readonly gameId = input.required<string>();
  private readonly api = inject(MultiplayerApi);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly me = inject(AuthService).user()!.userId;

  protected readonly status = signal<LobbyStatus | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly secondsToStart = signal<number | null>(null);
  private poll = new Subscription();

  ngOnInit(): void {
    this.destroyRef.onDestroy(() => this.poll.unsubscribe());
    this.join();
  }

  protected join(): void {
    this.error.set(null);
    this.poll.unsubscribe();
    const gameId = Number(this.gameId());
    this.api.join(gameId, this.me).subscribe({
      next: (s) => {
        this.update(s);
        this.poll = timer(1000, 1000)
          .pipe(switchMap(() => this.api.lobby(gameId, this.me)))
          .subscribe({ next: (n) => this.update(n), error: (e) => this.error.set(errorMessage(e)) });
      },
      error: (e) => this.error.set(errorMessage(e)),
    });
  }

  protected cancel(): void {
    this.poll.unsubscribe();
    this.api.leave(Number(this.gameId()), this.me).subscribe({ complete: () => this.router.navigateByUrl('/'), error: () => this.router.navigateByUrl('/') });
  }

  private update(s: LobbyStatus): void {
    this.status.set(s);
    this.secondsToStart.set(s.closesAt ? Math.max(0, Math.ceil((new Date(s.closesAt).getTime() - Date.now()) / 1000)) : null);
    if (s.state === 'MATCHED' && s.matchId) {
      this.poll.unsubscribe();
      this.router.navigate(['/match', s.matchId]);
    } else if (s.state === 'EXPIRED') {
      this.poll.unsubscribe();
    }
  }
}
