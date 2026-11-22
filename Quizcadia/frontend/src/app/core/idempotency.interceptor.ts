import { HttpInterceptorFn } from '@angular/common/http';

/** The header carrying a write's idempotency key. */
export const IDEMPOTENCY_HEADER = 'Idempotency-Key';
const WRITES = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

/**
 * Gives every API write (an answer, a new duel, a sign-up...) its own random Idempotency-Key. Each retry of that
 * write is the same request object, so it carries the same key, and the server answers a repeat with its first
 * answer instead of doing it twice (see IdempotencyFilter in the backend). That makes writes safe for the
 * maintenance interceptor to send again once the servers are back.
 */
export const idempotencyInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith('/api/') || !WRITES.has(req.method) || req.headers.has(IDEMPOTENCY_HEADER)) {
    return next(req);
  }
  return next(req.clone({ setHeaders: { [IDEMPOTENCY_HEADER]: newIdempotencyKey() } }));
};

/** A random UUID, from crypto.randomUUID where the browser has it. */
export function newIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
}
