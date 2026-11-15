import { Injectable, signal } from '@angular/core';

interface InstallEvent extends Event {
  prompt(): Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

/** Keeps the browser's "install this app" offer so the header can show an Install button. */
@Injectable({ providedIn: 'root' })
export class InstallPrompt {
  private deferred: InstallEvent | null = null;
  readonly available = signal(false);

  constructor() {
    if (typeof window === 'undefined') {
      return;
    }
    window.addEventListener('beforeinstallprompt', (e) => {
      e.preventDefault(); // show our own button instead of the browser's mini bar
      this.deferred = e as InstallEvent;
      this.available.set(true);
    });
    window.addEventListener('appinstalled', () => {
      this.deferred = null;
      this.available.set(false);
    });
  }

  async install(): Promise<void> {
    const e = this.deferred;
    if (!e) {
      return;
    }
    await e.prompt();
    await e.userChoice;
    this.deferred = null;
    this.available.set(false);
  }
}
