import { COUNTRY_ZONES } from './zones';

/** ISO 3166-1 alpha-2 codes offered in the country picker. */
export const CODES = (
  'AF AL DZ AD AO AG AR AM AU AT AZ BS BH BD BB BY BE BZ BJ BT BO BA BW BR BN BG BF BI KH CM CA CV CF TD CL CN CO KM CG CD CR CI HR CU CY CZ ' +
  'DK DJ DM DO EC EG SV GQ ER EE SZ ET FJ FI FR GA GM GE DE GH GR GD GT GN GW GY HT HN HK HU IS IN ID IR IQ IE IL IT JM JP JO KZ KE KI KW KG ' +
  'LA LV LB LS LR LY LI LT LU MO MG MW MY MV ML MT MH MR MU MX FM MD MC MN ME MA MZ MM NA NR NP NL NZ NI NE NG KP MK NO OM PK PW PS PA PG PY PE ' +
  'PH PL PT PR QA RO RU RW KN LC VC WS SM ST SA SN RS SC SL SG SK SI SB SO ZA KR SS ES LK SD SR SE CH SY TW TJ TZ TH TL TG TO TT TN TR TM TV ' +
  'UG UA AE GB US UY UZ VU VA VE VN YE ZM ZW'
).split(' ');

/** 🇨🇦 from "CA" (regional indicator letters); empty for anything else. */
export function flag(code: string | null | undefined): string {
  if (!code || !/^[A-Za-z]{2}$/.test(code)) {
    return '';
  }
  return String.fromCodePoint(...[...code.toUpperCase()].map((c) => 0x1f1e6 + c.charCodeAt(0) - 65));
}

let names: Intl.DisplayNames | null = null;

/** The English name of a country code (e.g. IN gives India), or the code when it is unknown. */
export function countryName(code: string): string {
  try {
    names ??= new Intl.DisplayNames(['en'], { type: 'region' });
    return names.of(code.toUpperCase()) ?? code;
  } catch {
    return code;
  }
}

/** Every country, sorted by name, for the picker. */
export function allCountries(): { code: string; name: string }[] {
  return CODES.map((code) => ({ code, name: countryName(code) })).sort((a, b) => a.name.localeCompare(b.name));
}

/** Time zone → country (Asia/Kolkata → IN), from the generated per-country list in zones.ts. */
const ZONE_COUNTRY: Record<string, string> = Object.fromEntries(
  Object.entries(COUNTRY_ZONES).flatMap(([code, zones]) => zones.split(' ').map((zone) => [zone, code])),
);

/** The country of a time zone (Asia/Kolkata → IN), or null for zones not tied to one country. */
export function countryFromZone(zone: string | null | undefined): string | null {
  return (zone && ZONE_COUNTRY[zone]) || null;
}

/** The device's time zone, e.g. Asia/Kolkata. */
export function browserZone(): string | null {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone ?? null;
  } catch {
    return null;
  }
}

/**
 * The player's likely country, for players who haven't picked one. The device's time zone comes first:
 * browsers in India, Pakistan, the Gulf and elsewhere are very often set to "English (United States)",
 * so the language alone would put those players in the US. The language (en-CA → CA) is the fallback.
 */
export function guessCountry(
  languages: readonly string[] = typeof navigator === 'undefined' ? [] : navigator.languages,
  zone: string | null = browserZone(),
): string | null {
  const fromZone = countryFromZone(zone);
  if (fromZone) {
    return fromZone;
  }
  for (const l of languages) {
    const region = l.split('-')[1];
    if (region && /^[A-Za-z]{2}$/.test(region) && CODES.includes(region.toUpperCase())) {
      return region.toUpperCase();
    }
  }
  return null;
}

/** Approximate centre [latitude, longitude] of countries, for dots on the world map. */
export const CENTROIDS: Record<string, [number, number]> = {
  US: [39, -98], CA: [57, -101], MX: [23, -102], BR: [-10, -52], AR: [-35, -65], CL: [-31, -71], CO: [4, -73], PE: [-10, -75],
  VE: [7, -66], EC: [-1.5, -78], BO: [-17, -65], UY: [-33, -56], PY: [-23, -58], CU: [21.5, -79], JM: [18.1, -77.3], DO: [19, -70.7],
  GB: [54, -2], IE: [53, -8], FR: [46.5, 2.5], DE: [51, 10], ES: [40, -4], PT: [39.5, -8], IT: [42.8, 12.5], NL: [52.2, 5.5],
  BE: [50.6, 4.7], CH: [46.8, 8.2], AT: [47.6, 14.1], PL: [52, 19.4], CZ: [49.8, 15.5], SE: [62, 15], NO: [61, 9], FI: [64, 26],
  DK: [56, 10], GR: [39, 22], TR: [39, 35], RO: [45.9, 25], HU: [47.2, 19.4], UA: [49, 31], RU: [61, 99], IS: [65, -18],
  EG: [26.5, 30], MA: [31.8, -7], DZ: [28, 2.6], TN: [34, 9.5], NG: [9.1, 8.7], GH: [7.9, -1], KE: [0.2, 37.9], ET: [9.1, 40.5],
  ZA: [-29, 24.7], TZ: [-6.4, 34.9], UG: [1.4, 32.3], SN: [14.5, -14.5], CI: [7.5, -5.5], CM: [5.7, 12.4], AO: [-12.3, 17.5],
  SA: [24, 45], AE: [24, 54], IL: [31.4, 35], IR: [32.4, 53.7], IQ: [33, 43.7], PK: [30, 69.3], IN: [22, 79], BD: [23.7, 90.4],
  LK: [7.9, 80.8], NP: [28.4, 84.1], CN: [35, 104], JP: [36.5, 138.5], KR: [36.5, 127.8], TW: [23.7, 121], HK: [22.3, 114.2],
  PH: [12.9, 121.8], VN: [16, 107.8], TH: [15.9, 101], MY: [4.2, 102], SG: [1.35, 103.8], ID: [-2.5, 118], KZ: [48, 67],
  AU: [-25, 134], NZ: [-41.5, 172.5], QA: [25.3, 51.2], KW: [29.3, 47.5], JO: [31.2, 36.5], LB: [33.9, 35.9],
};
