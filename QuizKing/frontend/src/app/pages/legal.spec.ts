import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { Privacy, Terms } from './legal';

describe('Legal pages', () => {
  it('render the privacy policy and terms', async () => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    const p = TestBed.createComponent(Privacy);
    await p.whenStable();
    expect((p.nativeElement as HTMLElement).textContent).toContain('Sign in with Google');
    expect((p.nativeElement as HTMLElement).textContent).toContain('Google Analytics (only if you allow it)');
    const t = TestBed.createComponent(Terms);
    await t.whenStable();
    expect((t.nativeElement as HTMLElement).textContent).toContain('Playing fair');
  });
});
