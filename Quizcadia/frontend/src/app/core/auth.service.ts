import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { finalize, Observable, shareReplay, tap } from 'rxjs';
import { TokenResponse, User } from './models';
import { track } from './analytics';
import { Providers, SignInOptions } from './sign-in-options';

export type { Providers } from './sign-in-options';

const STORAGE_KEY = 'quizcadia.session';
const INVITE_KEY = 'quizcadia.invite';

/** Remembers the invite code from a friend's /join link until the visitor signs up. */
export function rememberInvite(code: string): void {
  try {
    localStorage.setItem(INVITE_KEY, code);
  } catch {
    /* storage unavailable: the invite just isn't counted */
  }
}

/** The invite code waiting to be sent with the sign-up, if any. */
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

/**
 * What survives a reload. {@code token} is a short-lived access token (15 minutes): the refresh token that renews it
 * is in an HttpOnly cookie that page scripts can't read. {@code sessionExpiresAt}: when that cookie runs out.
 */
interface StoredSession {
  token: string;
  expiresAt: string;
  user: User;
  sessionExpiresAt?: string | null;
}

/** Renew the access token this long before it runs out. */
const REFRESH_AHEAD_MS = 60_000;

/** Current player: an account or a guest, each with a JWT from the login service. */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly signInOptions = inject(SignInOptions);
  private readonly session = signal<StoredSession | null>(this.restore());

  readonly user = computed(() => this.session()?.user ?? null);
  readonly token = computed(() => this.session()?.token ?? null);
  readonly isLoggedIn = computed(() => this.session() !== null);
  readonly isAdmin = computed(() => this.session()?.user.role === 'ADMIN');
  private refreshing: Observable<TokenResponse> | null = null;

  /** Signs in with email and password and keeps the session. */
  login(email: string, password: string): Observable<TokenResponse> {
    return this.http.post<TokenResponse>('/api/v1/auth/login', { email, password }).pipe(tap((t) => { this.store(t); track('login', { method: 'password' }); }));
  }

  /** Creates an account (with any pending invite code) and signs in. */
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

  /** Which sign-in options the server offers (fetched once per visit and shared, see SignInOptions). */
  providers(): Observable<Providers> {
    return this.signInOptions.get();
  }

  /** Signs in with the ID token Google's button handed us; a first-time player gets an account. */
  /**
   * Back from Google's redirect sign-in (core/google-redirect.ts): the login service has set the session cookie,
   * so swapping it for a token signs this tab in.
   */
  finishGoogleRedirect(): Observable<TokenResponse> {
    return this.refresh().pipe(tap(() => { track('login', { method: 'google' }); clearInvite(); }));
  }

  /** Signs in with a Google ID token (with any pending invite code); a new email gets a new account. */
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

  /** True when the access token is gone or about to run out, so the next API call should renew it first. */
  expiresSoon(now = Date.now()): boolean {
    const s = this.session();
    return !!s && new Date(s.expiresAt).getTime() - REFRESH_AHEAD_MS <= now;
  }

  /**
   * Swaps the refresh cookie for a new access token. Calls made while one is on its way share it, so a burst
   * of requests renews once. Errors with 401 when the sign-in has ended (see {@link endLocally}).
   */
  refresh(): Observable<TokenResponse> {
    this.refreshing ??= this.http.post<TokenResponse>('/api/v1/auth/refresh', {}).pipe(
      tap((t) => this.store(t)),
      finalize(() => (this.refreshing = null)),
      shareReplay({ bufferSize: 1, refCount: false }),
    );
    return this.refreshing;
  }

  /** Signs out here and on the server (the refresh cookie stops working), even if the server can't be reached. */
  logout(): void {
    this.http.post<void>('/api/v1/auth/logout', {}).subscribe({ error: () => undefined });
    this.save(null);
  }

  /** Forgets the sign-in in this browser only: the server already said it has ended. */
  endLocally(): void {
    this.save(null);
  }

  private store(t: TokenResponse): void {
    // a refresh inside the two-tabs grace period sets no new cookie: the current one keeps its end
    const sessionExpiresAt = t.sessionExpiresAt ?? this.session()?.sessionExpiresAt ?? null;
    this.save({ token: t.token, expiresAt: t.expiresAt, user: t.user, sessionExpiresAt });
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
      // Every API call needs a token now; sessions saved by older versions (guests without one) start over.
      // An expired access token is fine while the refresh cookie lasts: the first API call renews it.
      const now = Date.now();
      const until = s.sessionExpiresAt ?? s.expiresAt;
      if (!s.token || !until || new Date(until).getTime() < now) {
        localStorage.removeItem(STORAGE_KEY);
        return null;
      }
      return s;
    } catch {
      return null;
    }
  }
}
