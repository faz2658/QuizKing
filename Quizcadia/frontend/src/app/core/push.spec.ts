import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { isAppleMobile, keyBytes, StreakReminderPush } from './push';

const KEY = 'BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8';

/** A browser with push: a service worker whose push manager holds {@code existing} (or nothing). */
function browser(permission: NotificationPermission, existing: unknown = null) {
  const created = {
    endpoint: 'https://fcm.googleapis.com/fcm/send/new',
    toJSON: () => ({ keys: { p256dh: 'P256', auth: 'AUTH' } }),
    unsubscribe: vi.fn(async () => true),
  };
  const pushManager = { getSubscription: vi.fn(async () => existing), subscribe: vi.fn(async (_opts: PushSubscriptionOptionsInit) => created) };
  const notification = { permission, requestPermission: vi.fn(async () => 'granted' as NotificationPermission) };
  vi.stubGlobal('Notification', notification);
  vi.stubGlobal('PushManager', class {});
  Object.defineProperty(navigator, 'serviceWorker', { configurable: true, value: { ready: Promise.resolve({ pushManager }) } });
  return { pushManager, notification, created };
}

describe('Streak reminders (push)', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    delete (navigator as unknown as { serviceWorker?: unknown }).serviceWorker;
  });

  async function refreshed(push: StreakReminderPush, key: string | null = KEY) {
    const done = push.refresh();
    await Promise.resolve();
    http.expectOne('/api/v1/reports/push/key').flush({ publicKey: key });
    await done;
  }

  it('offers reminders when the browser can push and the server has a key', async () => {
    browser('default');
    const push = TestBed.inject(StreakReminderPush);
    await refreshed(push);
    expect(push.state()).toBe('off');
  });

  it('stays hidden when the server has no key', async () => {
    browser('default');
    const push = TestBed.inject(StreakReminderPush);
    await refreshed(push, null);
    expect(push.state()).toBe('hidden');
  });

  it('turning on asks permission, subscribes with our key and sends the subscription with the time zone', async () => {
    const b = browser('default');
    const push = TestBed.inject(StreakReminderPush);
    await refreshed(push);

    const on = push.enable();
    const sent = await vi.waitFor(() => http.expectOne('/api/v1/reports/push/subscriptions'));
    expect(b.notification.requestPermission).toHaveBeenCalled();
    const opts = b.pushManager.subscribe.mock.calls[0][0];
    expect(opts.userVisibleOnly).toBe(true);
    expect(Array.from(opts.applicationServerKey as Uint8Array)).toEqual(Array.from(keyBytes(KEY)));
    expect(sent.request.body).toEqual({ endpoint: 'https://fcm.googleapis.com/fcm/send/new', p256dh: 'P256', auth: 'AUTH', timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone });
    sent.flush(null);
    await on;
    expect(push.state()).toBe('on');
  });

  it('a "no" to the permission prompt leaves them off, and a block shows as denied', async () => {
    const b = browser('default');
    b.notification.requestPermission.mockResolvedValueOnce('denied');
    const push = TestBed.inject(StreakReminderPush);
    await refreshed(push);

    await push.enable();

    expect(b.pushManager.subscribe).not.toHaveBeenCalled();
    expect(push.state()).toBe('denied');
  });

  it('an existing subscription shows as on, and is sent again for this player and time zone', async () => {
    browser('granted', { endpoint: 'https://fcm.googleapis.com/fcm/send/old', toJSON: () => ({ keys: { p256dh: 'P', auth: 'A' } }) });
    const push = TestBed.inject(StreakReminderPush);
    await refreshed(push);

    expect(push.state()).toBe('on');
    await vi.waitFor(() => expect(http.match('/api/v1/reports/push/subscriptions')).toHaveLength(1));
  });

  it('turning off unsubscribes the browser and tells the server', async () => {
    const existing = { endpoint: 'https://fcm.googleapis.com/fcm/send/old', toJSON: () => ({ keys: {} }), unsubscribe: vi.fn(async () => true) };
    browser('granted', existing);
    const push = TestBed.inject(StreakReminderPush);
    await refreshed(push);
    http.match('/api/v1/reports/push/subscriptions').forEach((r) => r.flush(null));

    const off = push.disable();
    await vi.waitFor(() => {
      const del = http.expectOne((r) => r.method === 'DELETE' && r.url === '/api/v1/reports/push/subscriptions');
      expect(del.request.body).toEqual({ endpoint: 'https://fcm.googleapis.com/fcm/send/old' });
      del.flush(null);
    });
    await off;
    expect(existing.unsubscribe).toHaveBeenCalled();
    expect(push.state()).toBe('off');
  });

  it('on an iPhone outside the Home Screen app it explains how to get reminders', async () => {
    vi.spyOn(navigator, 'userAgent', 'get').mockReturnValue('Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 Safari/604.1');
    const push = TestBed.inject(StreakReminderPush);
    await push.refresh(); // no PushManager in plain Safari tabs on iOS
    expect(push.state()).toBe('install');
    http.expectNone('/api/v1/reports/push/key');
    vi.restoreAllMocks();
  });

  it('reads VAPID keys and spots iPhones and iPads', () => {
    const bytes = keyBytes(KEY);
    expect(bytes).toHaveLength(65);
    expect(bytes[0]).toBe(4);
    expect(isAppleMobile({ userAgent: 'Mozilla/5.0 (iPad; CPU OS 17_0 like Mac OS X)', maxTouchPoints: 5 })).toBe(true);
    expect(isAppleMobile({ userAgent: 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)', maxTouchPoints: 5 })).toBe(true); // iPadOS
    expect(isAppleMobile({ userAgent: 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)', maxTouchPoints: 0 })).toBe(false);
    expect(isAppleMobile({ userAgent: 'Mozilla/5.0 (Linux; Android 14)', maxTouchPoints: 5 })).toBe(false);
  });
});
