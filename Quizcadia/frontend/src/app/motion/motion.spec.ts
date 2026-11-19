import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { BlurText } from './blur-text';
import { CountUp } from './count-up';
import { DecryptText } from './decrypt-text';
import { Tilt } from './tilt';

function reducedMotion(on: boolean) {
  vi.stubGlobal('matchMedia', (q: string) => ({ matches: on && q.includes('reduce'), media: q, addEventListener() {}, removeEventListener() {} }));
}

describe('motion components', () => {
  afterEach(() => vi.unstubAllGlobals());

  describe('BlurText', () => {
    it('glows starred words and gives screen readers the plain sentence', async () => {
      await TestBed.configureTestingModule({ imports: [BlurText] }).compileComponents();
      const f = TestBed.createComponent(BlurText);
      f.componentRef.setInput('text', 'Think fast. Play *smart*.');
      f.detectChanges();
      const el = f.nativeElement as HTMLElement;

      expect(el.querySelector('.sr-only')!.textContent).toBe('Think fast. Play smart.');
      expect(Array.from(el.querySelectorAll('.word')).map((w) => w.textContent)).toEqual(['Think', 'fast.', 'Play', 'smart.']);
      expect(el.querySelectorAll('.glow').length).toBe(1);
    });
  });

  describe('with reduced motion', () => {
    beforeEach(() => reducedMotion(true));

    it('DecryptText shows the final text at once', async () => {
      await TestBed.configureTestingModule({ imports: [DecryptText] }).compileComponents();
      const f = TestBed.createComponent(DecryptText);
      f.componentRef.setInput('text', 'Capital of Peru?');
      f.detectChanges();
      await f.whenStable();

      expect((f.nativeElement as HTMLElement).querySelector('[aria-hidden]')!.textContent).toBe('Capital of Peru?');
    });

    it('CountUp jumps straight to the value', async () => {
      @Component({ imports: [CountUp], template: `<mv-count-up [value]="v()" suffix="%" />` })
      class Host { v = signal(0); }
      await TestBed.configureTestingModule({ imports: [Host] }).compileComponents();
      const f = TestBed.createComponent(Host);
      f.detectChanges();
      f.componentInstance.v.set(86);
      f.detectChanges();
      await f.whenStable();

      expect((f.nativeElement as HTMLElement).textContent!.trim()).toBe('86%');
    });
  });

  it('DecryptText keeps spaces and punctuation in place while scrambling and ends on the real text', async () => {
    reducedMotion(false);
    vi.useFakeTimers({ toFake: ['requestAnimationFrame', 'performance'] });
    await TestBed.configureTestingModule({ imports: [DecryptText] }).compileComponents();
    const f = TestBed.createComponent(DecryptText);
    f.componentRef.setInput('text', 'Hi, you?');
    f.componentRef.setInput('duration', 100);
    f.detectChanges();
    vi.advanceTimersByTime(16);
    f.detectChanges();
    const mid = (f.nativeElement as HTMLElement).querySelector('[aria-hidden]')!.textContent!;
    expect(mid).toHaveLength(8);
    expect(mid[2]).toBe(',');
    expect(mid[3]).toBe(' ');
    vi.advanceTimersByTime(200);
    f.detectChanges();
    expect((f.nativeElement as HTMLElement).querySelector('[aria-hidden]')!.textContent).toBe('Hi, you?');
    vi.useRealTimers();
  });
});

@Component({ imports: [Tilt], template: '<div mvTilt="8" style="width:100px;height:100px"></div>' })
class TiltHost {}

describe('Tilt', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('applies only the newest pointer position, once per frame', () => {
    reducedMotion(false);
    const queued: FrameRequestCallback[] = [];
    vi.stubGlobal('requestAnimationFrame', (cb: FrameRequestCallback) => queued.push(cb));
    vi.stubGlobal('cancelAnimationFrame', () => undefined);
    const f = TestBed.configureTestingModule({ imports: [TiltHost] }).createComponent(TiltHost);
    f.detectChanges();
    const el = (f.nativeElement as HTMLElement).querySelector('div')!;
    vi.spyOn(el, 'getBoundingClientRect').mockReturnValue({ left: 0, top: 0, width: 100, height: 100 } as DOMRect);
    const style = vi.spyOn(el.style, 'setProperty');
    const before = queued.length; // Angular schedules frames of its own

    for (const x of [10, 40, 90]) el.dispatchEvent(new PointerEvent('pointermove', { clientX: x, clientY: 50, pointerType: 'mouse' }));

    expect(style).not.toHaveBeenCalled(); // nothing written until the next frame
    for (const cb of queued.slice(before)) cb(0);
    expect(style).toHaveBeenCalledTimes(2); // --mx and --my once: three moves, one update
    expect(el.style.getPropertyValue('--mx')).toBe('90.0%');
    expect(el.style.transform).toContain('rotateY(3.20deg)');
  });

  it('ignores touch and resets when the pointer leaves', () => {
    reducedMotion(false);
    vi.stubGlobal('requestAnimationFrame', (cb: FrameRequestCallback) => { cb(0); return 1; });
    const f = TestBed.configureTestingModule({ imports: [TiltHost] }).createComponent(TiltHost);
    f.detectChanges();
    const el = (f.nativeElement as HTMLElement).querySelector('div')!;
    vi.spyOn(el, 'getBoundingClientRect').mockReturnValue({ left: 0, top: 0, width: 100, height: 100 } as DOMRect);
    el.dispatchEvent(new PointerEvent('pointermove', { clientX: 10, clientY: 10, pointerType: 'touch' }));
    expect(el.style.transform).toBe('');
    el.dispatchEvent(new PointerEvent('pointermove', { clientX: 10, clientY: 10, pointerType: 'mouse' }));
    expect(el.style.transform).not.toBe('');
    el.dispatchEvent(new PointerEvent('pointerleave'));
    expect(el.style.transform).toBe('');
  });
});
