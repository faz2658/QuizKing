import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Subscription, switchMap, timer } from 'rxjs';
import { MultiplayerApi } from '../core/api';
import { AuthService } from '../core/auth.service';
import { errorMessage } from '../core/http-errors';
import { shareLink } from '../core/invite';
import { RoomStatus } from '../core/models';

/** A private room: the code to share, who's in, and the host's start button. */
@Component({
  selector: 'mv-room',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card room">
      @if (error()) {
        <h2>Can't open this room</h2>
        <div class="alert" role="alert">{{ error() }}</div>
        <a class="btn" routerLink="/">Back to games</a>
      } @else if (room(); as r) {
        @if (r.state === 'CLOSED') {
          <h2>The host closed this room</h2>
          <a class="btn btn-primary" routerLink="/">Back to games</a>
        } @else {
          <span class="muted">Private room · share this code</span>
          <button type="button" class="code" (click)="invite(r)" [attr.aria-label]="'Room code ' + r.code + ', tap to share'">{{ r.code }}</button>
          @if (note()) { <p class="muted" aria-live="polite">{{ note() }}</p> }
          <ul class="players" aria-label="Players in the room">
            @for (p of r.players; track p.userId) {
              <li [class.me]="p.userId === me"><span aria-hidden="true">{{ p.userId === r.hostId ? '👑' : '🎮' }}</span> {{ p.name }}{{ p.userId === me ? ' (you)' : '' }}</li>
            }
          </ul>
          <p class="muted">{{ r.players.length }} / {{ r.maxPlayers }} players</p>
          @if (isHost()) {
            <div class="row center">
              <button class="btn btn-primary" [disabled]="r.players.length < 2 || busy()" (click)="start()">
                {{ r.players.length < 2 ? 'Waiting for friends…' : 'Start the match' }}
              </button>
              <button class="btn btn-ghost" (click)="leave()">Close room</button>
            </div>
          } @else {
            <p><strong>Waiting for the host to start…</strong></p>
            <button class="btn btn-ghost" (click)="leave()">Leave</button>
          }
        }
      } @else {
        <div class="skeleton" style="height: 220px; width: 100%"></div>
      }
    </section>
  `,
  styles: `
    .room { max-width: 560px; margin: 40px auto; text-align: center; display: flex; flex-direction: column; align-items: center; gap: 12px; padding: 40px; }
    .code { font-size: clamp(2.2rem, 8vw, 3.4rem); font-weight: 900; letter-spacing: 0.08em; border: 2px dashed var(--accent); border-radius: 20px;
      padding: 10px 26px; background: color-mix(in srgb, var(--accent) 10%, var(--bg-raised)); color: var(--text); cursor: pointer; }
    .players { list-style: none; padding: 0; display: flex; flex-wrap: wrap; gap: 8px; justify-content: center; }
    .players li { padding: 8px 16px; border-radius: 999px; background: var(--surface); border: 1px solid var(--border); font-weight: 700; }
    .players li.me { border-color: var(--accent); }
    .center { justify-content: center; }
  `,
})
export class Room implements OnInit {
  readonly code = input.required<string>();
  private readonly api = inject(MultiplayerApi);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly me = inject(AuthService).user()!.userId;
  protected readonly room = signal<RoomStatus | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly note = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly isHost = computed(() => this.room()?.hostId === this.me);
  private poll = new Subscription();

  ngOnInit(): void {
    this.destroyRef.onDestroy(() => this.poll.unsubscribe());
    // opening an invite link joins the room (joining twice is harmless)
    this.api.joinRoom(this.code()).subscribe({
      next: (r) => {
        this.update(r);
        this.poll = timer(1500, 1500)
          .pipe(switchMap(() => this.api.room(this.code())))
          .subscribe({ next: (n) => this.update(n), error: (e) => this.error.set(errorMessage(e)) });
      },
      error: (e) => this.error.set(errorMessage(e)),
    });
  }

  protected start(): void {
    this.busy.set(true);
    this.api.startRoom(this.code()).subscribe({
      next: (r) => this.update(r),
      error: (e) => {
        this.busy.set(false);
        this.note.set(errorMessage(e));
      },
    });
  }

  protected leave(): void {
    this.poll.unsubscribe();
    this.api.leaveRoom(this.code()).subscribe({ complete: () => this.router.navigateByUrl('/'), error: () => this.router.navigateByUrl('/') });
  }

  protected async invite(r: RoomStatus): Promise<void> {
    const how = await shareLink('QuizCadia', `Join my QuizCadia room ${r.code}!`, `${location.origin}/room/${r.code}`);
    this.note.set(how === 'copied' ? 'Invite link copied.' : how === 'failed' ? `Tell your friends the code: ${r.code}` : null);
  }

  private update(r: RoomStatus): void {
    this.room.set(r);
    if (r.state === 'STARTED' && r.matchId && r.players.some((p) => p.userId === this.me)) {
      this.poll.unsubscribe();
      this.router.navigate(['/match', r.matchId]);
    }
    if (r.state === 'CLOSED') {
      this.poll.unsubscribe();
    }
  }
}
