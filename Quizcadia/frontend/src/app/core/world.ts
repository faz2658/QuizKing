import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Subscription, catchError, of, switchMap, timer } from 'rxjs';
import { guessCountry } from './countries';
import { OnlineView } from './models';

const STORAGE_KEY = 'quizcadia.country';

/** The player's country and the heartbeat that puts them on the "online now" map. */
@Injectable({ providedIn: 'root' })
export class World {
  private readonly http = inject(HttpClient);
  private readonly _country = signal<string | null>(this.restore());
  readonly country = this._country.asReadonly();
  readonly online = signal<OnlineView | null>(null);
  private beat: Subscription | null = null;

  /** Starts the once-a-minute heartbeat (and keeps the online numbers fresh). */
  start(): void {
    if (this.beat) {
      return;
    }
    this.beat = timer(0, 60_000)
      .pipe(
        switchMap(() => this.http.post('/api/v1/matching/presence', { country: this._country() }).pipe(catchError(() => of(null)))),
        switchMap(() => this.http.get<OnlineView>('/api/v1/matching/online').pipe(catchError(() => of(null)))),
      )
      .subscribe((o) => o && this.online.set(o));
  }

  stop(): void {
    this.beat?.unsubscribe();
    this.beat = null;
  }

  refresh(): void {
    this.http.get<OnlineView>('/api/v1/matching/online').pipe(catchError(() => of(null))).subscribe((o) => o && this.online.set(o));
  }

  setCountry(code: string | null): void {
    this._country.set(code);
    try {
      if (code) {
        localStorage.setItem(STORAGE_KEY, code);
      } else {
        localStorage.removeItem(STORAGE_KEY);
      }
    } catch {
      /* storage unavailable */
    }
    this.http.post('/api/v1/reports/me', { country: code }).pipe(catchError(() => of(null))).subscribe();
    this.http.post('/api/v1/matching/presence', { country: code }).pipe(catchError(() => of(null))).subscribe(() => this.refresh());
  }

  private restore(): string | null {
    try {
      return localStorage.getItem(STORAGE_KEY) ?? guessCountry();
    } catch {
      return guessCountry();
    }
  }
}
