import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import { Inquiry } from '../core/inquiries';
import { Events } from './events';
import { Requests } from './requests';
import { Venues } from './venues';

describe('Event and venue requests', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });

  async function fill(el: HTMLElement, name: string, value: string): Promise<void> {
    const input = el.querySelector<HTMLInputElement | HTMLTextAreaElement>(`[name=${name}]`)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  it('sends a quiz night request and thanks the visitor', async () => {
    const f = TestBed.createComponent(Events);
    await f.whenStable();
    const el = f.nativeElement as HTMLElement;
    expect(el.textContent).toContain('A quiz night made for your group');
    await fill(el, 'name', 'Ada');
    await fill(el, 'email', 'ada@example.test');
    await fill(el, 'organisation', 'Acme');
    await fill(el, 'groupSize', '25');
    await fill(el, 'message', 'Holiday party');
    await f.whenStable();
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();

    const req = http.expectOne('/api/v1/reports/inquiries');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toMatchObject({ kind: 'EVENT', name: 'Ada', email: 'ada@example.test', organisation: 'Acme', groupSize: 25, website: '' });
    req.flush({ received: true }, { status: 202, statusText: 'Accepted' });
    await f.whenStable();
    expect(el.textContent).toContain('Thanks, Ada!');
  });

  it('venue requests need a venue name, and explain when there are too many', async () => {
    const f = TestBed.createComponent(Venues);
    await f.whenStable();
    const el = f.nativeElement as HTMLElement;
    await fill(el, 'name', 'Bob');
    await fill(el, 'email', 'bob@example.test');
    await f.whenStable();
    expect(el.querySelector<HTMLButtonElement>('button[type=submit]')!.disabled).toBe(true); // venue name missing

    await fill(el, 'organisation', 'The Fox');
    await f.whenStable();
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    const req = http.expectOne('/api/v1/reports/inquiries');
    expect(req.request.body.kind).toBe('VENUE');
    req.flush({ message: 'slow down' }, { status: 429, statusText: 'Too Many Requests' });
    await f.whenStable();
    expect(el.textContent).toContain('Too many requests');
  });

  it('lists requests for the admin and updates their status', async () => {
    const one: Inquiry = { id: 'a', kind: 'VENUE', name: 'Bob', email: 'bob@example.test', organisation: 'The Fox', city: 'Ottawa',
      occasion: 'Tuesday', groupSize: 40, status: 'NEW', createdAt: '2026-11-16T12:00:00Z', updatedAt: '2026-11-16T12:00:00Z' };
    const f = TestBed.createComponent(Requests);
    http.expectOne('/api/v1/reports/inquiries').flush([one]);
    await f.whenStable();
    const el = f.nativeElement as HTMLElement;
    expect(el.textContent).toContain('The Fox');
    expect(el.textContent).toContain('New (1)');
    expect(el.querySelector('a[href^="mailto:bob@example.test"]')).toBeTruthy();

    const select = el.querySelector<HTMLSelectElement>('select.status')!;
    select.value = 'CONTACTED';
    select.dispatchEvent(new Event('change'));
    const patch = http.expectOne('/api/v1/reports/inquiries/a');
    expect(patch.request.body).toEqual({ status: 'CONTACTED' });
    patch.flush({ ...one, status: 'CONTACTED' });
    await f.whenStable();
    expect(el.textContent).toContain('Contacted (1)');
    expect(el.textContent).toContain('No new requests yet.');
  });
});
