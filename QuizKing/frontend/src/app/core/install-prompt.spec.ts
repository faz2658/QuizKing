import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { InstallPrompt, manualInstall } from './install-prompt';

const IPHONE = 'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1';
const IPAD = 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15';
const ANDROID_FIREFOX = 'Mozilla/5.0 (Android 14; Mobile; rv:130.0) Gecko/130.0 Firefox/130.0';
const MAC = IPAD;

describe('InstallPrompt', () => {
  it('picks the manual steps that fit the device', () => {
    expect(manualInstall({ userAgent: IPHONE, maxTouchPoints: 5 })).toBe('ios');
    expect(manualInstall({ userAgent: IPAD, maxTouchPoints: 5 })).toBe('ios'); // iPadOS says it's a Mac
    expect(manualInstall({ userAgent: ANDROID_FIREFOX, maxTouchPoints: 5 })).toBe('other');
    expect(manualInstall({ userAgent: MAC, maxTouchPoints: 0 })).toBeNull(); // desktop: only the browser's own offer
  });

  it('uses the browser offer when there is one, and the help steps otherwise', async () => {
    const installer = TestBed.inject(InstallPrompt);
    let prompted = false;
    const offer = Object.assign(new Event('beforeinstallprompt', { cancelable: true }), {
      prompt: async () => { prompted = true; },
      userChoice: Promise.resolve({ outcome: 'dismissed' as const }),
    });
    window.dispatchEvent(offer);
    expect(offer.defaultPrevented).toBe(true);
    expect(installer.available()).toBe(true);

    await installer.install();
    expect(prompted).toBe(true);
    expect(installer.help()).toBeNull();

    await installer.install(); // the offer is used up: explain the manual way
    expect(installer.help()).not.toBeNull();
    installer.closeHelp();
    expect(installer.help()).toBeNull();
  });
});
