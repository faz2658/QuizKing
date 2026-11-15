import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { TokenResponse, User } from './models';

const STORAGE_KEY = 'maverick.session';

interface StoredSession {
  token: string;
  expiresAt: string;
  user: User;
}

/** Current player: an account or a guest, each with a JWT from the login service. */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly session = signal<StoredSession | null>(this.restore());

  readonly user = computed(() => this.session()?.user ?? null);
  readonly token = computed(() => this.session()?.token ?? null);
  readonly isLoggedIn = computed(() => this.session() !== null);
  readonly isAdmin = computed(() => this.session()?.user.role === 'ADMIN');

  login(email: string, password: string): Observable<TokenResponse> {
    return this.http.post<TokenResponse>('/api/v1/auth/login', { email, password }).pipe(tap((t) => this.store(t)));
  }

  register(userName: string, email: string, password: string): Observable<TokenResponse> {
    return this.http
      .post<TokenResponse>('/api/v1/auth/register', { userName, email, password })
      .pipe(tap((t) => this.store(t)));
  }

  /** Plays without an account: the server hands out a guest token (no account is stored). */
  playAsGuest(name: string): Observable<TokenResponse> {
    return this.http
      .post<TokenResponse>('/api/v1/auth/guest', { userName: name.trim() || 'Guest' })
      .pipe(tap((t) => this.store(t)));
  }

  logout(): void {
    this.save(null);
  }

  private store(t: TokenResponse): void {
    this.save({ token: t.token, expiresAt: t.expiresAt, user: t.user });
  }

  private save(s: StoredSession | null): void {
    this.session.set(s);
    try {
      if (s) {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(s));
      } else {
        localStorage.removeItem(STORAGE_KEY);
      }
    } catch {
      /* storage unavailable (private mode): session lives in memory only */
    }
  }

  private restore(): StoredSession | null {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) {
        return null;
      }
      const s = JSON.parse(raw) as StoredSession;
      // Every API call needs a token now; sessions saved by older versions (guests without one) start over
      if (!s.token || (s.expiresAt && new Date(s.expiresAt).getTime() < Date.now())) {
        localStorage.removeItem(STORAGE_KEY);
        return null;
      }
      return s;
    } catch {
      return null;
    }
  }
}
