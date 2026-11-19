import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

/**
 * Where streak reminders stand on this device:
 * - hidden: no push here (old browser, or reminders not switched on on the server)
 * - install: iPhone/iPad Safari, where push only works once QuizCadia is added to the Home Screen
 * - denied: the player blocked notifications for this site (only browser settings can undo that)
 * - off / on: ready to switch on, or already on
 */
export type ReminderState = 'hidden' | 'install' | 'denied' | 'off' | 'on';

/** A VAPID key (base64url) as the bytes pushManager.subscribe wants. */
export function keyBytes(base64url: string): Uint8Array<ArrayBuffer> {
  const b64 = (base64url + '='.repeat((4 - (base64url.length % 4)) % 4)).replace(/-/g, '+').replace(/_/g, '/');
  const raw = atob(b64);
  const out = new Uint8Array(new ArrayBuffer(raw.length));
  for (let i = 0; i < raw.length; i++) out[i] = raw.charCodeAt(i);
  return out;
}

/** iPhone and iPad (including iPads that call themselves Macs), where web push needs the Home Screen app. */
export function isAppleMobile(nav: Pick<Navigator, 'userAgent' | 'maxTouchPoints'> = navigator): boolean {
  return /iPhone|iPad|iPod/.test(nav.userAgent) || (/Macintosh/.test(nav.userAgent) && nav.maxTouchPoints > 1);
}

function standalone(): boolean {
  return !!window.matchMedia?.('(display-mode: standalone)').matches || (navigator as Navigator & { standalone?: boolean }).standalone === true;
}

/** Streak reminders by Web Push: this device's state, switching them on and off, and a test notification. */
@Injectable({ providedIn: 'root' })
export class StreakReminderPush {
  private readonly http = inject(HttpClient);
  readonly state = signal<ReminderState>('hidden');
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  private key: string | null = null;
  private checked = false;

  /** Works out the state once per page load; an existing subscription is sent again (same player, current time zone). */
  async refresh(): Promise<void> {
    if (this.checked) return;
    this.checked = true;
    const supported = 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window;
    if (!supported) {
      this.state.set(isAppleMobile() && !standalone() ? 'install' : 'hidden');
      return;
    }
    try {
      this.key = (await firstValueFrom(this.http.get<{ publicKey: string | null }>('/api/v1/reports/push/key'))).publicKey;
    } catch {
      this.key = null;
    }
    if (!this.key) return; // reminders aren't switched on on this server
    if (Notification.permission === 'denied') {
      this.state.set('denied');
      return;
    }
    const sub = await (await this.registration())?.pushManager.getSubscription();
    if (sub && Notification.permission === 'granted') {
      this.state.set('on');
      this.save(sub).catch(() => undefined);
    } else {
      this.state.set('off');
    }
  }

  /** Must run from a tap: browsers only show the permission prompt for one. */
  async enable(): Promise<void> {
    if (!this.key) return;
    this.busy.set(true);
    this.error.set(null);
    try {
      const permission = await Notification.requestPermission();
      if (permission !== 'granted') {
        this.state.set(permission === 'denied' ? 'denied' : 'off');
        return;
      }
      const reg = await this.registration();
      if (!reg) throw new Error('no service worker');
      const sub = (await reg.pushManager.getSubscription()) ?? (await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: keyBytes(this.key) }));
      await this.save(sub);
      this.state.set('on');
    } catch {
      this.error.set("Couldn't turn reminders on. Please try again.");
    } finally {
      this.busy.set(false);
    }
  }

  async disable(): Promise<void> {
    this.busy.set(true);
    this.error.set(null);
    try {
      const sub = await (await this.registration())?.pushManager.getSubscription();
      if (sub) {
        await firstValueFrom(this.http.delete('/api/v1/reports/push/subscriptions', { body: { endpoint: sub.endpoint } })).catch(() => undefined);
        await sub.unsubscribe();
      }
      this.state.set('off');
    } finally {
      this.busy.set(false);
    }
  }

  private save(sub: PushSubscription): Promise<unknown> {
    const json = sub.toJSON();
    return firstValueFrom(
      this.http.post('/api/v1/reports/push/subscriptions', {
        endpoint: sub.endpoint,
        p256dh: json.keys?.['p256dh'],
        auth: json.keys?.['auth'],
        timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone,
      }),
    );
  }

  /** The service worker, if one is (or gets) active within a few seconds. */
  private registration(): Promise<ServiceWorkerRegistration | null> {
    return Promise.race([
      navigator.serviceWorker.ready,
      new Promise<null>((resolve) => setTimeout(() => resolve(null), 4000)),
    ]);
  }
}
