import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { IDEMPOTENCY_HEADER, idempotencyInterceptor } from './idempotency.interceptor';

describe('idempotencyInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([idempotencyInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  it('gives each API write its own key', () => {
    http.post('/api/v1/sessions/1/answers', {}).subscribe();
    http.post('/api/v1/sessions/1/answers', {}).subscribe();
    http.delete('/api/v1/knowme/3').subscribe();
    const keys = backend.match(() => true).map((r) => r.request.headers.get(IDEMPOTENCY_HEADER));
    expect(keys).toHaveLength(3);
    keys.forEach((k) => expect(k).toMatch(/^[A-Za-z0-9_-]{8,128}$/));
    expect(new Set(keys).size).toBe(3);
  });

  it('leaves reads, non-API calls and keys set by the caller alone', () => {
    http.get('/api/v1/games').subscribe();
    http.post('/assets/x', {}).subscribe();
    http.post('/api/v1/x', {}, { headers: { [IDEMPOTENCY_HEADER]: 'my-own-key-1' } }).subscribe();
    expect(backend.expectOne('/api/v1/games').request.headers.has(IDEMPOTENCY_HEADER)).toBe(false);
    expect(backend.expectOne('/assets/x').request.headers.has(IDEMPOTENCY_HEADER)).toBe(false);
    expect(backend.expectOne('/api/v1/x').request.headers.get(IDEMPOTENCY_HEADER)).toBe('my-own-key-1');
  });
});
