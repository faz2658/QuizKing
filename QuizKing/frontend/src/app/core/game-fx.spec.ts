import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { GameFx } from './game-fx';

describe('GameFx', () => {
  beforeEach(() => localStorage.clear());

  it('starts with sound and vibration on and remembers a switch-off', () => {
    const fx = TestBed.runInInjectionContext(() => new GameFx());
    expect(fx.settings()).toEqual({ sound: true, vibration: true });

    fx.toggle('sound');
    expect(fx.settings().sound).toBe(false);
    expect(TestBed.runInInjectionContext(() => new GameFx()).settings().sound).toBe(false);
  });

  it('vibrates on a wrong answer unless vibration is off', () => {
    const vibrate = vi.fn();
    Object.defineProperty(navigator, 'vibrate', { value: vibrate, configurable: true });
    const fx = TestBed.runInInjectionContext(() => new GameFx());
    fx.toggle('sound'); // no AudioContext in the test browser
    fx.play('wrong');
    expect(vibrate).toHaveBeenCalledWith([70, 50, 70]);

    vibrate.mockClear();
    fx.toggle('vibration');
    fx.play('wrong');
    expect(vibrate).not.toHaveBeenCalled();
  });
});
