import { isWebKit } from '../motion/page-transition';

/**
 * Google sign-in in redirect mode, for iPhone, iPad and Safari. Google's popup is a second tab there, and once the
 * player picks an account it must hand the sign-in back to the QuizCadia tab; iOS often freezes or reloads that tab
 * in the background, which left players on a blank accounts.google.com page. In redirect mode Google posts the
 * sign-in straight to the login service, which sets the session cookie and sends the browser to
 * /welcome?google=ok, where the app picks the session up. No second tab, nothing to hand back.
 */
export const GOOGLE_REDIRECT_PATH = '/api/v1/auth/google/redirect';
/** Must match the login service (AuthController.INVITE_COOKIE). */
const INVITE_COOKIE = 'qc_invite';
const NEXT_KEY = 'quizcadia.googleNext';

/** Apple's engine (Safari, every iOS browser) gets the redirect; other browsers keep the popup. */
export function useGoogleRedirect(vendor?: string): boolean {
  return isWebKit(vendor);
}

/** Where Google should post the sign-in: an Authorized redirect URI of the OAuth client in Google Cloud. */
export function googleLoginUri(origin: string = location.origin): string {
  return origin + GOOGLE_REDIRECT_PATH;
}

/**
 * Before the trip to Google: remembers where the player was going (the page reloads on the way back) and puts a
 * friend's invite where the login service can read it, a cookie only sent to /api/v1/auth for a day. It must be
 * SameSite=None, since Google's form post is cross-site; browsers only accept that on https.
 */
export function prepareGoogleRedirect(next: string | null | undefined, invite: string | null, doc: Document = document): void {
  try {
    if (next && next.startsWith('/') && !next.startsWith('//')) sessionStorage.setItem(NEXT_KEY, next);
    else sessionStorage.removeItem(NEXT_KEY);
  } catch {
    // storage blocked: the player lands on the home page instead
  }
  if (invite && /^[A-Za-z0-9_-]{1,16}$/.test(invite)) {
    const crossSite = doc.location?.protocol === 'https:' ? '; SameSite=None; Secure' : '; SameSite=Lax';
    doc.cookie = `${INVITE_COOKIE}=${invite}; Path=/api/v1/auth; Max-Age=86400${crossSite}`;
  }
}

/** Back from Google: where to go now (once), defaulting to the home page. */
export function takeGoogleNext(): string {
  try {
    const next = sessionStorage.getItem(NEXT_KEY);
    sessionStorage.removeItem(NEXT_KEY);
    return next && next.startsWith('/') && !next.startsWith('//') ? next : '/';
  } catch {
    return '/';
  }
}
