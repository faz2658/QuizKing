import { afterEach, describe, expect, it } from 'vitest';
import { googleLoginUri, prepareGoogleRedirect, takeGoogleNext, useGoogleRedirect } from './google-redirect';

describe('Google redirect sign-in', () => {
  afterEach(() => sessionStorage.clear());

  it('is for Apple\'s engine only', () => {
    expect(useGoogleRedirect('Apple Computer, Inc.')).toBe(true);
    expect(useGoogleRedirect('Google Inc.')).toBe(false);
    expect(useGoogleRedirect('')).toBe(false);
  });

  it('posts to the login service on this site', () => {
    expect(googleLoginUri('https://quizcadia.com')).toBe('https://quizcadia.com/api/v1/auth/google/redirect');
  });

  it('remembers where the player was going, once, and only paths on this site', () => {
    prepareGoogleRedirect('/party/KING-1', null);
    expect(takeGoogleNext()).toBe('/party/KING-1');
    expect(takeGoogleNext()).toBe('/'); // used up
    prepareGoogleRedirect('//evil.example', null);
    expect(takeGoogleNext()).toBe('/');
    prepareGoogleRedirect('https://evil.example', null);
    expect(takeGoogleNext()).toBe('/');
  });

  it('carries a friend\'s invite in a cross-site cookie on https, and ignores junk', () => {
    const set: string[] = [];
    const doc = (protocol: string) => ({ location: { protocol }, set cookie(v: string) { set.push(v); } }) as unknown as Document;

    prepareGoogleRedirect(null, 'AB12cd', doc('https:'));
    prepareGoogleRedirect(null, 'AB12cd', doc('http:'));
    prepareGoogleRedirect(null, 'bad; Path=/', doc('https:'));

    expect(set).toEqual([
      'qc_invite=AB12cd; Path=/api/v1/auth; Max-Age=86400; SameSite=None; Secure',
      'qc_invite=AB12cd; Path=/api/v1/auth; Max-Age=86400; SameSite=Lax',
    ]);
  });
});
