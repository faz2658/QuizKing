import { describe, expect, it } from 'vitest';
import { readdirSync } from 'node:fs';
import { resolve } from 'node:path';
import { allCountries, CODES, countryFromZone, flag, guessCountry } from './countries';
import { COUNTRY_ZONES } from './zones';
import { project } from '../ui/world-map';
import { clock } from '../pages/world';

describe('countries', () => {
  it('turns ISO codes into flag emoji', () => {
    expect(flag('ca')).toBe('🇨🇦');
    expect(flag('IN')).toBe('🇮🇳');
    expect(flag(null)).toBe('');
    expect(flag('CAN')).toBe('');
  });

  it('guesses the country from the time zone first, then the browser language', () => {
    // a phone in India set to "English (United States)" is in India, not the US
    expect(guessCountry(['en-US'], 'Asia/Kolkata')).toBe('IN');
    expect(guessCountry(['en-US', 'en'], 'Asia/Calcutta')).toBe('IN');
    expect(guessCountry(['en-GB'], 'Asia/Karachi')).toBe('PK');
    expect(guessCountry(['en-US'], 'Asia/Dubai')).toBe('AE');
    expect(guessCountry(['fr-FR'], 'America/Toronto')).toBe('CA');
    // a zone shared by many countries (or none) falls back to the language
    expect(guessCountry(['en-CA', 'fr'], 'UTC')).toBe('CA');
    expect(guessCountry(['en-CA'], null)).toBe('CA');
    expect(guessCountry(['en'], null)).toBeNull();
  });

  it('only maps zones to countries the picker offers', () => {
    for (const zone of ['Asia/Kolkata', 'Asia/Dhaka', 'Europe/London', 'America/Sao_Paulo', 'Pacific/Auckland']) {
      expect(allCountries().some((c) => c.code === countryFromZone(zone))).toBe(true);
    }
    expect(countryFromZone('Etc/GMT+5')).toBeNull();
  });

  it('places players in every country by time zone, including the newer question packs', () => {
    expect(countryFromZone('Europe/Tallinn')).toBe('EE');
    expect(countryFromZone('Europe/Zagreb')).toBe('HR');
    expect(countryFromZone('America/Montevideo')).toBe('UY');
    expect(countryFromZone('Africa/Kinshasa')).toBe('CD');
    expect(countryFromZone('Africa/Lubumbashi')).toBe('CD');
    expect(guessCountry(['en-US'], 'Europe/Riga')).toBe('LV');
    // old zone names some browsers still report
    expect(countryFromZone('Asia/Calcutta')).toBe('IN');
    expect(countryFromZone('Europe/Kiev')).toBe('UA');
    expect(countryFromZone('US/Eastern')).toBe('US');
  });

  it('has at least one time zone for every country in the picker', () => {
    const zoneCountries = new Set(Object.keys(COUNTRY_ZONES));
    expect(CODES.filter((c) => !zoneCountries.has(c))).toEqual([]);
    for (const [code, zones] of Object.entries(COUNTRY_ZONES)) {
      expect(CODES).toContain(code);
      for (const zone of zones.split(' ')) {
        expect(countryFromZone(zone)).toBe(code);
      }
    }
  });

  it('has a time zone for the country of every question pack', () => {
    const dir = resolve(process.cwd(), '../questionGenerator/src/main/resources/packs');
    const packs = readdirSync(dir)
      .filter((f) => f.endsWith('.json'))
      .map((f) => f.slice(0, -5).toUpperCase());
    expect(packs.length).toBeGreaterThan(100);
    expect(packs.filter((c) => !COUNTRY_ZONES[c])).toEqual([]);
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
