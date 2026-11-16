import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import { BRAIN_TEASERS, LETTER_VAULTS } from '../core/puzzles/content';
import { pick } from '../core/puzzles/logic';
import { PuzzleProgress } from '../core/puzzles/progress';
import { Puzzles } from './puzzles';

describe('Puzzle of the Day page', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
  });

  function click(el: HTMLElement, selector: string, text?: string): void {
    const all = Array.from(el.querySelectorAll<HTMLElement>(selector));
    const target = text ? all.find((b) => b.textContent?.includes(text)) : all[0];
    expect(target, `${selector} ${text ?? ''}`).toBeTruthy();
    target!.click();
  }

  it('shows the four puzzles and lets you solve the Brain Teaser, then saves it', async () => {
    const f = TestBed.createComponent(Puzzles);
    await f.whenStable();
    const el = f.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Clue Trail');
    expect(el.textContent).toContain('Letter Vault');
    expect(el.textContent).toContain('Four by Four');
    expect(el.textContent).toContain('Brain Teaser');

    click(el, '.pcard', 'Brain Teaser');
    await f.whenStable();
    const progress = TestBed.inject(PuzzleProgress);
    const teaser = pick(BRAIN_TEASERS, progress.day(), 11);
    click(el, '.option', teaser.options[teaser.answer]);
    await f.whenStable();

    expect(el.textContent).toContain('Correct!');
    expect(progress.result('teaser')).toMatchObject({ done: true, won: true });
    expect(progress.streak()).toBe(1);
    expect(JSON.parse(localStorage.getItem('quizcadia.puzzles')!).results.teaser.won).toBe(true);
    expect(el.textContent).toContain('Share today\'s results (1/4)');
  });

  it('cracks the Letter Vault with the right word', async () => {
    const f = TestBed.createComponent(Puzzles);
    await f.whenStable();
    const el = f.nativeElement as HTMLElement;
    click(el, '.pcard', 'Letter Vault');
    await f.whenStable();
    const progress = TestBed.inject(PuzzleProgress);
    const word = pick(LETTER_VAULTS, progress.day(), 7).word;

    const input = el.querySelector<HTMLInputElement>('#vault-guess')!;
    input.value = word.toLowerCase();
    input.dispatchEvent(new Event('input'));
    await f.whenStable();
    click(el, 'button[type=submit]');
    await f.whenStable();

    expect(progress.result('vault')).toMatchObject({ done: true, won: true });
    expect(progress.result('vault')!.summary).toContain('1/6');
    expect(el.querySelectorAll('.cell.hit').length).toBe(word.length);
  });
});
