import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { Support, SUPPORT_URL } from './support';

describe('Support', () => {
  it('links to the Ko-fi page, as a footer link or a results card', async () => {
    const link = TestBed.createComponent(Support);
    await link.whenStable();
    const a = (link.nativeElement as HTMLElement).querySelector('a')!;
    expect(a.getAttribute('href')).toBe('https://ko-fi.com/quizcadia');
    expect(a.getAttribute('target')).toBe('_blank');
    expect(a.getAttribute('rel')).toContain('noopener');
    expect(a.textContent).toContain('Support QuizCadia');

    const card = TestBed.createComponent(Support);
    card.componentRef.setInput('kind', 'card');
    card.componentRef.setInput('placement', 'results');
    await card.whenStable();
    const el = card.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Enjoying QuizCadia?');
    expect(el.querySelector('a.btn')?.getAttribute('href')).toBe(SUPPORT_URL);
  });
});
