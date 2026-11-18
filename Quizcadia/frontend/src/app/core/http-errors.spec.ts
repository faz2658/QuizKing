import { HttpErrorResponse } from '@angular/common/http';
import { describe, expect, it } from 'vitest';
import { errorMessage } from './http-errors';

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
});
