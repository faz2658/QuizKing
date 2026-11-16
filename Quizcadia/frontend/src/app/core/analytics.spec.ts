import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { Analytics, track } from './analytics';

const gaScripts = () => document.head.querySelectorAll('script[src*="googletagmanager"]');

describe('Analytics', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.removeItem('quizcadia.analytics');
    gaScripts().forEach((s) => s.remove());
    delete (window as unknown as { gtag?: unknown }).gtag;
    (window as unknown as { dataLayer: unknown[] }).dataLayer = [];
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('asks first and loads nothing until the player allows it', () => {
    const a = TestBed.inject(Analytics);
    a.init();
    http.expectOne('/api/v1/auth/providers').flush({ googleClientId: null, analyticsId: 'G-TEST1234' });
    expect(a.ask()).toBe(true);
    expect(gaScripts().length).toBe(0);
    track('login', { method: 'password' }); // no gtag yet: a no-op

    a.choose('granted');
    expect(a.ask()).toBe(false);
    expect(gaScripts().length).toBe(1);
    expect(localStorage.getItem('quizcadia.analytics')).toBe('granted');
    const layer = (window as unknown as { dataLayer: IArguments[] }).dataLayer.map((x) => Array.from(x));
    expect(layer.some((x) => x[0] === 'config' && x[1] === 'G-TEST1234')).toBe(true);
    expect(layer.some((x) => x[0] === 'event' && x[1] === 'login')).toBe(false);
  });

  it('never asks or loads when the player said no, or the server has no id', () => {
    localStorage.setItem('quizcadia.analytics', 'denied');
    const a = TestBed.inject(Analytics);
    a.init();
    http.expectOne('/api/v1/auth/providers').flush({ googleClientId: null, analyticsId: 'G-TEST1234' });
    expect(a.ask()).toBe(false);
    expect(gaScripts().length).toBe(0);
  });

  it('ignores a malformed id', () => {
    const a = TestBed.inject(Analytics);
    a.init();
    http.expectOne('/api/v1/auth/providers').flush({ googleClientId: null, analyticsId: '"><script>' });
    expect(a.ask()).toBe(false);
  });
});
