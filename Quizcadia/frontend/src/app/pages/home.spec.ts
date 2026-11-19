import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { World } from '../core/world';
import { Home } from './home';

const game = (gameId: number, gameName: string, gameTypeId = 1) => ({ gameId, gameName, categoryId: 1, gameType: { gameTypeId } });

describe('Games page', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => localStorage.clear());

  /** Answers the catalog calls for one country (WORLD or a country code). */
  function catalog(country: string, solo: object[], multi: object[]) {
    http.match((r) => r.url === '/api/game/games' && r.params.get('country') === country).forEach((r) => r.flush(solo));
    http.match((r) => r.url === '/api/game/games/mp' && r.params.get('country') === country).forEach((r) => r.flush(multi));
  }

  const names = (el: HTMLElement) => [...el.querySelectorAll('mv-game-card h3')].map((h) => h.textContent?.trim());

  it('shows the shared games and the player\'s own country, not other countries', async () => {
    TestBed.inject(World).setCountry('IN');
    const f = TestBed.createComponent(Home);
    f.detectChanges();
    await new Promise((r) => setTimeout(r, 300)); // the list waits a moment for typing in the search box
    catalog('WORLD', [game(1, 'History')], [game(2, 'Sports Showdown', 2)]);
    catalog('IN', [game(40, 'Bollywood')], []);
    // the engine's cache knows every country's games; it must not be listed while the game manager is up
    http.match('/api/v1/games').forEach((r) => r.flush([game(1, 'History'), game(77, 'Anime (Japan)')]));
    await f.whenStable();
    f.detectChanges();

    const el = f.nativeElement as HTMLElement;
    expect(names(el)).toEqual(['Bollywood', 'History', 'Sports Showdown']);
    expect(el.textContent).not.toContain('Anime (Japan)');
    http.expectNone((r) => r.url.startsWith('/api/game/games') && !!r.params.get('country') && !['WORLD', 'IN'].includes(r.params.get('country')!));
    f.destroy();
  });

  it('shows only the shared games when no country is known', async () => {
    TestBed.inject(World).setCountry(null); // otherwise it's guessed from the browser's language (en-CA: CA)
    const f = TestBed.createComponent(Home);
    f.detectChanges();
    await new Promise((r) => setTimeout(r, 300));
    catalog('WORLD', [game(1, 'History')], []);
    http.match('/api/v1/games').forEach((r) => r.flush([game(77, 'Anime (Japan)')]));
    await f.whenStable();
    f.detectChanges();

    expect(names(f.nativeElement as HTMLElement)).toEqual(['History']);
    http.expectNone((r) => r.url === '/api/game/games' && r.params.get('country') !== 'WORLD');
    f.destroy();
  });

  it('asks for the games straight away instead of waiting for the search box\'s typing pause', async () => {
    TestBed.inject(World).setCountry(null);
    const f = TestBed.createComponent(Home);
    f.detectChanges();
    await Promise.resolve();
    await new Promise((r) => setTimeout(r, 20)); // well under the 250 ms typing pause
    expect(http.match((r) => r.url === '/api/game/games' && r.params.get('country') === 'WORLD')).toHaveLength(1);
    http.expectNone('/api/v1/games'); // the engine's catalog is only a fallback
    f.destroy();
  });
});
