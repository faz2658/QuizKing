import { HttpErrorResponse } from '@angular/common/http';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { OFFLINE_MESSAGE, errorMessage } from './http-errors';

describe('errorMessage', () => {
  it('prefers the server\'s explanation, and explains rate limits without one', () => {
    expect(errorMessage(new HttpErrorResponse({ status: 429, error: { detail: 'Too many sign-in attempts for this email.' } })))
      .toBe('Too many sign-in attempts for this email.');
    expect(errorMessage(new HttpErrorResponse({ status: 429, error: '<html>429 Too Many Requests</html>' })))
      .toContain('Too many attempts from your network');
    expect(errorMessage(new HttpErrorResponse({ status: 500, error: null }), 'fallback')).toBe('fallback');
  });

  it('explains that the servers are being updated when they can\'t be reached', () => {
    for (const status of [0, 502, 504]) {
      expect(errorMessage(new HttpErrorResponse({ status }))).toContain('being updated');
    }
    expect(errorMessage(new HttpErrorResponse({ status: 503, error: { maintenance: true, detail: 'x' } }))).toContain('being updated');
  });

  describe('when the device is offline', () => {
    afterEach(() => vi.unstubAllGlobals());

    it('says so instead of blaming an update', () => {
      vi.stubGlobal('navigator', { onLine: false });
      expect(errorMessage(new HttpErrorResponse({ status: 0 }))).toBe(OFFLINE_MESSAGE);
      // the server did answer: its own explanation still wins
      expect(errorMessage(new HttpErrorResponse({ status: 409, error: { detail: 'That quiz has ended' } }))).toBe('That quiz has ended');
    });
  });

  it('shows the server\'s generic 500 sentence with its error id', () => {
    const detail = 'Something went wrong on our side. Please try again. (error 1a2b3c4d)';
    expect(errorMessage(new HttpErrorResponse({ status: 500, error: { detail, errorId: '1a2b3c4d' } }))).toBe(detail);
  });
});
