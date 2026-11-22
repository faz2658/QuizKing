import { Injectable, computed, signal } from '@angular/core';

interface InstallEvent extends Event {
  prompt(): Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

/** How this device adds QuizCadia to its home screen when the browser can't do it with one tap. */
export type ManualInstall = 'ios' | 'other';

/**
 * Powers the header's Install button.
 * - Chrome, Edge and Samsung Internet (Android and desktop) fire `beforeinstallprompt`: the button opens the
 *   browser's own install dialog.
 * - iPhone and iPad never fire it (Safari and every other iOS browser): the button shows the
 *   "Share > Add to Home Screen" steps instead.
 * - Other phone and tablet browsers: the button shows the "browser menu > Install / Add to Home screen" steps.
 * Hidden once QuizCadia is already running as an installed app.
 */
@Injectable({ providedIn: 'root' })
export class InstallPrompt {
  private deferred: InstallEvent | null = null;
  private readonly native = signal(false);
  private readonly installed = signal(false);
  private readonly manual: ManualInstall | null = null;

  /** The steps to show, while the help sheet is open. */
  readonly help = signal<ManualInstall | null>(null);
  /** Show the Install button. */
  readonly available = computed(() => !this.installed() && (this.native() || this.manual !== null));

  constructor() {
    if (typeof window === 'undefined') {
      return;
    }
    this.installed.set(isStandalone());
    this.manual = manualInstall(navigator);
    window.addEventListener('beforeinstallprompt', (e) => {
      e.preventDefault(); // show our own button instead of the browser's mini bar
      this.deferred = e as InstallEvent;
      this.native.set(true);
    });
    window.addEventListener('appinstalled', () => {
      this.deferred = null;
      this.native.set(false);
      this.installed.set(true);
      this.help.set(null);
    });
  }

  /** Shows the browser's install prompt, or the manual steps where the browser has none. */
  async install(): Promise<void> {
    const e = this.deferred;
    if (!e) {
      this.help.set(this.manual ?? 'other');
      return;
    }
    await e.prompt();
    const { outcome } = await e.userChoice;
    this.deferred = null; // the browser's offer can be used once
    this.native.set(false);
    if (outcome === 'accepted') {
      this.installed.set(true);
    }
  }

  /** Closes the manual install steps. */
  closeHelp(): void {
    this.help.set(null);
  }
}

function isStandalone(): boolean {
  const iosStandalone = (navigator as Navigator & { standalone?: boolean }).standalone === true;
  return iosStandalone || window.matchMedia?.('(display-mode: standalone)').matches === true;
}

/** Which manual steps fit this device, or null where the browser's own prompt is the only sensible route (desktop). */
export function manualInstall(nav: Pick<Navigator, 'userAgent' | 'maxTouchPoints'>): ManualInstall | null {
  const ua = nav.userAgent;
  // iPadOS reports itself as a Mac; a Mac with a touch screen is an iPad
  const ios = /iPhone|iPad|iPod/.test(ua) || (/Macintosh/.test(ua) && nav.maxTouchPoints > 1);
  if (ios) {
    return 'ios';
  }
  return /Android|Mobi|Tablet/i.test(ua) ? 'other' : null;
}
