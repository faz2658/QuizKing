import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { GameCardView } from './game-card';

function initials(name: string): string {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({ imports: [GameCardView], providers: [provideRouter([])] });
  const f = TestBed.createComponent(GameCardView);
  f.componentRef.setInput('game', { gameId: 1, gameName: name, categoryId: 1, mode: 'SINGLE' });
  f.detectChanges();
  return (f.nativeElement as HTMLElement).querySelector('.art span')!.textContent!.trim();
}

describe('GameCardView', () => {
  it('builds initials from words, skipping symbols', () => {
    expect(initials('Musicals & Theatres')).toBe('MT');
    expect(initials('Video Games')).toBe('VG');
    expect(initials('Mythology')).toBe('M');
  });
});
