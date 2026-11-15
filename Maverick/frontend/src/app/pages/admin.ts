import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { catchError, of } from 'rxjs';
import { CatalogApi } from '../core/api';
import { AuthService } from '../core/auth.service';
import { errorMessage } from '../core/http-errors';
import { Category } from '../core/models';

/** Creates a game from a topic's question bank in the game manager. */
@Component({
  selector: 'mv-admin',
  imports: [FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1>Create a game</h1>
    <form class="card stack form" (ngSubmit)="create()">
      <div class="two">
        <label class="field">Category
          <select class="input" name="category" [ngModel]="categoryId()" (ngModelChange)="categoryId.set(+$event); topic = ''" required>
            <option [ngValue]="0" disabled>Choose…</option>
            @for (c of categories(); track c.categoryId) { <option [ngValue]="c.categoryId">{{ c.categoryName }}</option> }
          </select>
        </label>
        <label class="field">Topic
          <select class="input" name="topic" [(ngModel)]="topic" required [disabled]="!topics().length">
            <option value="" disabled>Choose…</option>
            @for (t of topics(); track t.topicId) { <option [value]="t.topicName">{{ t.topicName }} ({{ t.questions?.length ?? 0 }} questions)</option> }
          </select>
        </label>
      </div>
      <label class="field">Name <input class="input" name="name" [(ngModel)]="name" required maxlength="80" /></label>
      <label class="field">Description <textarea class="input" name="desc" rows="2" [(ngModel)]="description"></textarea></label>

      <fieldset class="field">
        <legend>Type</legend>
        <div class="row">
          <label><input type="radio" name="mode" value="SINGLE" [(ngModel)]="mode" /> Solo (timed &amp; adaptive)</label>
          <label><input type="radio" name="mode" value="MULTI" [(ngModel)]="mode" /> Multiplayer</label>
        </div>
      </fieldset>

      <div class="three">
        <label class="field">Easy questions <input class="input" type="number" min="0" name="easy" [(ngModel)]="easy" /></label>
        <label class="field">Medium questions <input class="input" type="number" min="0" name="medium" [(ngModel)]="medium" /></label>
        <label class="field">Advanced questions <input class="input" type="number" min="0" name="adv" [(ngModel)]="advanced" /></label>
      </div>
      @if (mode === 'SINGLE') {
        <p class="muted">Defaults: 10 / 15 / 20 seconds and 10 / 20 / 30 points per easy / medium / advanced question.</p>
      } @else {
        <label class="field">Seconds per question <input class="input" type="number" min="5" name="tpq" [(ngModel)]="secondsPerQuestion" /></label>
      }

      @if (error()) { <div class="alert" role="alert">{{ error() }}</div> }
      @if (created()) { <p class="ok">Created “{{ created() }}”.</p> }
      <div class="row"><button class="btn btn-primary" type="submit" [disabled]="busy()">Create game</button></div>
    </form>
  `,
  styles: `
    .form { max-width: 760px; }
    .two { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
    .three { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
    fieldset { border: 0; padding: 0; margin: 0; }
    legend { margin-bottom: 6px; }
    .ok { color: var(--good); font-weight: 700; }
    @media (max-width: 640px) { .two, .three { grid-template-columns: 1fr; } }
  `,
})
export class Admin {
  private readonly api = inject(CatalogApi);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly categories = toSignal(this.api.categories().pipe(catchError(() => of([] as Category[]))), { initialValue: [] as Category[] });
  protected readonly categoryId = signal(0);
  protected readonly topics = computed(() => this.categories().find((c) => c.categoryId === this.categoryId())?.topic ?? []);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly created = signal<string | null>(null);

  protected topic = '';
  protected name = '';
  protected description = '';
  protected mode: 'SINGLE' | 'MULTI' = 'SINGLE';
  protected easy = 3;
  protected medium = 2;
  protected advanced = 1;
  protected secondsPerQuestion = 15;

  protected create(): void {
    this.error.set(null);
    this.created.set(null);
    if (!this.categoryId() || !this.topic || !this.name.trim()) {
      this.error.set('Choose a category and topic and give the game a name.');
      return;
    }
    this.busy.set(true);
    const levels = { easyLevel: this.easy, mediumLevel: this.medium, advancedLevel: this.advanced };
    const base = { gameName: this.name, gameDescription: this.description, createdBy: this.auth.user()?.userName, userId: this.auth.user()?.userId };
    const call = this.mode === 'SINGLE'
      ? this.api.createGame(this.categoryId(), this.topic, { ...base, questionLevels: levels })
      : this.api.createMultiplayerGame(this.categoryId(), this.topic, { ...base, questionLevel: levels, timePerQuestion: this.secondsPerQuestion });
    call.subscribe({
      next: (g) => {
        this.busy.set(false);
        this.created.set(g.gameName);
      },
      error: (e) => {
        this.busy.set(false);
        this.error.set(errorMessage(e));
      },
    });
  }
}
