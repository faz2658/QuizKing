import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { TokenResponse, User } from './models';
import { track } from './analytics';

const STORAGE_KEY = 'quizking.session';
const INVITE_KEY = 'quizking.invite';

/** Remembers the invite code from a friend's /join link until the visitor signs up. */
export function rememberInvite(code: string): void {
  try {
    localStorage.setItem(INVITE_KEY, code);
  } catch {
    /* storage unavailable: the invite just isn't counted */
  }
}

export function pendingInvite(): string | null {
  try {
    return localStorage.getItem(INVITE_KEY);
  } catch {
    return null;
  }
}

function clearInvite(): void {
  try {
    localStorage.removeItem(INVITE_KEY);
  } catch {
    /* ignore */
  }
}

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
    return this.http.post<TokenResponse>('/api/v1/auth/login', { email, password }).pipe(tap((t) => { this.store(t); track('login', { method: 'password' }); }));
  }

  register(userName: string, email: string, password: string): Observable<TokenResponse> {
    return this.http
      .post<TokenResponse>('/api/v1/auth/register', { userName, email, password, invite: pendingInvite() })
      .pipe(tap((t) => { track('sign_up', { method: 'password', invited: !!pendingInvite() }); this.store(t); clearInvite(); }));
  }

  /** Plays without an account: the server hands out a guest token (no account is stored). */
  playAsGuest(name: string): Observable<TokenResponse> {
    return this.http
      .post<TokenResponse>('/api/v1/auth/guest', { userName: name.trim() || 'Guest' })
      .pipe(tap((t) => { this.store(t); track('login', { method: 'guest' }); }));
  }

  /** Which sign-in options the server offers (the Google button needs its public client id). */
  providers(): Observable<{ googleClientId: string | null; analyticsId?: string | null }> {
    return this.http.get<{ googleClientId: string | null; analyticsId?: string | null }>('/api/v1/auth/providers');
  }

  /** Signs in with the ID token Google's button handed us; a first-time player gets an account. */
  signInWithGoogle(credential: string): Observable<TokenResponse> {
    return this.http
      .post<TokenResponse>('/api/v1/auth/google', { credential, invite: pendingInvite() })
      .pipe(tap((t) => { track('login', { method: 'google' }); this.store(t); clearInvite(); }));
  }

  /** Asks for a reset link by email. The server answers the same whether or not the account exists. */
  forgotPassword(email: string): Observable<void> {
    return this.http.post<void>('/api/v1/auth/password/forgot', { email: email.trim() });
  }

  /** Sets a new password from an emailed link and signs in. */
  resetPassword(token: string, password: string): Observable<TokenResponse> {
    return this.http
      .post<TokenResponse>('/api/v1/auth/password/reset', { token, password })
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
