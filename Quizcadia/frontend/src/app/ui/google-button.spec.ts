import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { GoogleButton } from './google-button';

describe('Google button', () => {
  const initialize = vi.fn();
  const renderButton = vi.fn();

  beforeEach(() => {
    initialize.mockReset();
    renderButton.mockReset();
    (window as unknown as { google: unknown }).google = { accounts: { id: { initialize, renderButton } } };
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
  });

  afterEach(() => {
    delete (window as unknown as { google?: unknown }).google;
    vi.restoreAllMocks();
    sessionStorage.clear();
  });

  async function render(vendor: string, next?: string) {
    vi.spyOn(navigator, 'vendor', 'get').mockReturnValue(vendor);
    const f = TestBed.createComponent(GoogleButton);
    if (next) f.componentRef.setInput('next', next);
    f.detectChanges();
    TestBed.inject(HttpTestingController).expectOne('/api/v1/auth/providers').flush({ googleClientId: 'cid.apps.googleusercontent.com' });
    await f.whenStable();
    await Promise.resolve();
    return f;
  }

  it('uses the redirect on Safari and iOS, keeping where to go next', async () => {
    const f = await render('Apple Computer, Inc.', '/party/KING-1');
    expect(initialize).toHaveBeenCalledWith({
      client_id: 'cid.apps.googleusercontent.com', ux_mode: 'redirect', login_uri: `${location.origin}/api/v1/auth/google/redirect`, context: 'signin',
    });
    expect(sessionStorage.getItem('quizcadia.googleNext')).toBe('/party/KING-1');
    expect(renderButton).toHaveBeenCalled();
    f.destroy();
  });

  it('keeps the popup elsewhere', async () => {
    const f = await render('Google Inc.');
    expect(initialize).toHaveBeenCalledWith(expect.objectContaining({ ux_mode: 'popup', callback: expect.any(Function) }));
    expect(initialize.mock.calls[0][0].login_uri).toBeUndefined();
    f.destroy();
  });
});
