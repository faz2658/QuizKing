import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { BlurText } from './blur-text';
import { CountUp } from './count-up';
import { DecryptText } from './decrypt-text';

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
