import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { project, WorldMap } from './world-map';

describe('World map', () => {
  it('draws the continents and a dot per country', async () => {
    const f = TestBed.createComponent(WorldMap);
    f.componentRef.setInput('points', [{ country: 'CA', players: 3 }, { country: 'IN', players: 1 }]);
    f.detectChanges();
    await f.whenStable();
    const el = f.nativeElement as HTMLElement;
    const land = el.querySelector('path.land');
    expect(land).not.toBeNull();
    const d = land!.getAttribute('d') ?? '';
    expect(d.startsWith('M')).toBe(true);
    expect((d.match(/M/g) ?? []).length).toBeGreaterThan(50); // dozens of landmasses and islands
    expect(el.querySelectorAll('circle.dot').length).toBe(2);
    expect(el.querySelector('svg')?.getAttribute('aria-label')).toContain('Canada 3');
  });

  it('keeps every land point on the map, apart from copies wrapped across the 180th meridian', async () => {
    const f = TestBed.createComponent(WorldMap);
    f.detectChanges();
    const d = (f.nativeElement as HTMLElement).querySelector('path.land')!.getAttribute('d')!;
    const ys = [...d.matchAll(/[ML](-?[\d.]+) (-?[\d.]+)/g)].map((m) => Number(m[2]));
    expect(Math.min(...ys)).toBeGreaterThanOrEqual(0);
    expect(Math.max(...ys)).toBeLessThanOrEqual(180);
  });

  it('places Canada in the north-west quarter', () => {
    const [x, y] = project(56, -106);
    expect(x).toBeLessThan(180);
    expect(y).toBeLessThan(90);
  });
});
