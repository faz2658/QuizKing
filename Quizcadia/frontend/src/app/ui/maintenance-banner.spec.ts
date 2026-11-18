import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it, vi } from 'vitest';
import { Maintenance } from '../core/maintenance';
import { MaintenanceBanner } from './maintenance-banner';

function render(state: { active: boolean; secondsLeft?: number; checking?: boolean }) {
  const tryNow = vi.fn();
  TestBed.configureTestingModule({
    providers: [{
      provide: Maintenance,
      useValue: { active: signal(state.active), secondsLeft: signal(state.secondsLeft ?? 0), checking: signal(state.checking ?? false), tryNow },
    }],
  });
  const fixture = TestBed.createComponent(MaintenanceBanner);
  fixture.detectChanges();
  return { el: fixture.nativeElement as HTMLElement, tryNow };
}

describe('MaintenanceBanner', () => {
  it('shows nothing while the servers are up', () => {
    expect(render({ active: false }).el.textContent?.trim()).toBe('');
  });

  it('says the site is being updated, with the countdown and a "Try now" button', () => {
    const { el, tryNow } = render({ active: true, secondsLeft: 75 });
    expect(el.textContent).toContain('QuizCadia is being updated');
    expect(el.textContent).toContain('Trying again in 1:15');
    (el.querySelector('button') as HTMLButtonElement).click();
    expect(tryNow).toHaveBeenCalled();
  });

  it('says when it is checking', () => {
    const { el } = render({ active: true, checking: true });
    expect(el.textContent).toContain('Checking');
    expect((el.querySelector('button') as HTMLButtonElement).disabled).toBe(true);
  });
});
