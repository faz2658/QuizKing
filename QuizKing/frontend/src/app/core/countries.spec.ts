import { describe, expect, it } from 'vitest';
import { allCountries, flag, guessCountry } from './countries';
import { project } from '../ui/world-map';
import { clock } from '../pages/world';

describe('countries', () => {
  it('turns ISO codes into flag emoji', () => {
    expect(flag('ca')).toBe('🇨🇦');
    expect(flag('IN')).toBe('🇮🇳');
    expect(flag(null)).toBe('');
    expect(flag('CAN')).toBe('');
  });

  it('guesses the country from the browser language', () => {
    expect(guessCountry(['en-CA', 'fr'])).toBe('CA');
    expect(guessCountry(['en'])).toBeNull();
  });

  it('lists countries by name', () => {
    const list = allCountries();
    expect(list.length).toBeGreaterThan(150);
    expect(list.find((c) => c.code === 'CA')?.name).toBe('Canada');
  });

  it('places map points and formats countdowns', () => {
    expect(project(0, 0)).toEqual([180, 90]);
    expect(project(90, -180)).toEqual([0, 0]);
    expect(clock(125)).toBe('2:05');
    expect(clock(-3)).toBe('0:00');
  });
});
