import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, ViewTransitionInfo } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { NEURAL_SCENE_LOADER, NeuralBg, POSES, poseFor } from './neural-bg';
import { brainLinks, brainPoints, NeuralScene, SceneFactory } from './neural-scene';
import { onPageTransition, PAGE_TRANSITION_CLASS, pageMove } from './page-transition';

function media(reduced: boolean) {
  vi.stubGlobal('matchMedia', (q: string) => ({
    matches: reduced && q.includes('reduce'), media: q, addEventListener() {}, removeEventListener() {},
  }));
}

function fakeScene(settled = false): NeuralScene {
  return { render: vi.fn(() => settled), resize: vi.fn(), setPose: vi.fn(), setColors: vi.fn(), dispose: vi.fn() };
}

@Component({ template: '' })
class Blank {}

/** A few animation frames. */
const frames = () => new Promise((r) => setTimeout(r, 300));

/** The scene only loads on the welcome page, so tests open it there (or at {@code url}). */
async function mount(factory?: SceneFactory, url = '/welcome') {
  const load = vi.fn(async () => factory ?? (() => null));
  TestBed.configureTestingModule({
    providers: [
      provideRouter([{ path: '**', component: Blank }]),
      { provide: NEURAL_SCENE_LOADER, useValue: load },
    ],
  });
  const f = TestBed.createComponent(NeuralBg);
  await f.whenStable();
  await TestBed.inject(Router).navigateByUrl(url);
  await f.whenStable();
  await frames();
  await f.whenStable();
  return { f, load, host: f.nativeElement as HTMLElement };
}

describe('NeuralBg', () => {
  beforeEach(() => {
    // a real-ish frame clock, so a running loop shows up as repeated renders
    vi.stubGlobal('requestAnimationFrame', (cb: FrameRequestCallback) => setTimeout(() => cb(performance.now()), 16) as unknown as number);
    vi.stubGlobal('cancelAnimationFrame', (id: number) => clearTimeout(id));
  });
  afterEach(() => vi.unstubAllGlobals());

  it('without WebGL it never downloads the scene and leaves the Aurora backdrop on its own', async () => {
    media(false);
    vi.stubGlobal('WebGLRenderingContext', undefined);
    const { load, host } = await mount();

    expect(load).not.toHaveBeenCalled();
    expect(host.getAttribute('data-mode')).toBe('fallback');
    expect(host.getAttribute('aria-hidden')).toBe('true');
  });

  it('downloads nothing away from the welcome page, and starts when the player gets there', async () => {
    media(false);
    vi.stubGlobal('WebGLRenderingContext', class {});
    const scene = fakeScene();
    const { f, load, host } = await mount(() => scene, '/leaderboard');
    expect(load).not.toHaveBeenCalled();
    expect(host.getAttribute('data-mode')).toBe('loading');

    await TestBed.inject(Router).navigateByUrl('/welcome');
    await f.whenStable();
    await frames();
    expect(load).toHaveBeenCalledOnce();
    expect(host.getAttribute('data-mode')).toBe('live');
  });

  it('falls back when the browser has WebGL but no context can be made', async () => {
    media(false);
    vi.stubGlobal('WebGLRenderingContext', class {});
    const { load, host } = await mount(() => null);

    expect(load).toHaveBeenCalledOnce();
    expect(host.getAttribute('data-mode')).toBe('fallback');
  });

  it('with reduced motion it draws one still frame, and jumps to a new page framing without a swing', async () => {
    media(true);
    vi.stubGlobal('WebGLRenderingContext', class {});
    const scene = fakeScene();
    const { f, host } = await mount(() => scene);

    expect(host.getAttribute('data-mode')).toBe('still');
    expect(scene.render).toHaveBeenCalledOnce();

    await TestBed.inject(Router).navigateByUrl('/play/7');
    await f.whenStable();
    await frames();
    expect(scene.setPose).toHaveBeenCalledWith(POSES.game, 0, true);
    expect(scene.render).toHaveBeenCalledTimes(2); // one still frame per page, never a loop
  });

  it('animates normally, and frees the GL scene and its listeners when destroyed', async () => {
    media(false);
    vi.stubGlobal('WebGLRenderingContext', class {});
    const scene = fakeScene();
    const removed = vi.spyOn(window, 'removeEventListener');
    const { f, host } = await mount(() => scene);

    expect(host.getAttribute('data-mode')).toBe('live');
    expect(vi.mocked(scene.render).mock.calls.length).toBeGreaterThan(1);

    f.destroy();
    const drawn = vi.mocked(scene.render).mock.calls.length;
    await frames();
    expect(scene.render).toHaveBeenCalledTimes(drawn); // the loop stopped
    expect(scene.dispose).toHaveBeenCalledOnce();
    expect(removed.mock.calls.map((c) => c[0])).toEqual(expect.arrayContaining(['resize', 'pointermove']));
  });

  it('pauses while the tab is hidden', async () => {
    media(false);
    vi.stubGlobal('WebGLRenderingContext', class {});
    const scene = fakeScene();
    await mount(() => scene);
    const hidden = vi.spyOn(document, 'hidden', 'get').mockReturnValue(true);
    document.dispatchEvent(new Event('visibilitychange'));
    const drawn = vi.mocked(scene.render).mock.calls.length;
    await frames();
    expect(scene.render).toHaveBeenCalledTimes(drawn);
    hidden.mockRestore();
  });

  it('drifts away and stops drawing when the player leaves the welcome page', async () => {
    media(false);
    vi.stubGlobal('WebGLRenderingContext', class {});
    const scene = fakeScene(true); // the camera has always "arrived"
    const { f, host } = await mount(() => scene);

    await TestBed.inject(Router).navigateByUrl('/play/7');
    await f.whenStable();
    expect(host.getAttribute('data-pose')).toBe('game');
    expect(scene.setPose).toHaveBeenLastCalledWith(POSES.game, expect.any(Number), false);
    expect(vi.mocked(scene.setPose).mock.lastCall![1]).toBeGreaterThan(0);

    await frames();
    const drawn = vi.mocked(scene.render).mock.calls.length;
    await frames();
    expect(scene.render).toHaveBeenCalledTimes(drawn);
  });
});

describe('poseFor', () => {
  it('frames each kind of page', () => {
    expect(poseFor('/welcome?next=%2Fparty%2FABC')).toBe('hero');
    for (const url of ['/play/3', '/daily', '/vs/9/play', '/challenge/x/play', '/custom/play', '/match/4', '/party/ABCD', '/know-me/12', '/puzzles']) {
      expect(poseFor(url), url).toBe('game');
    }
    for (const url of ['/admin/stats', '/privacy', '/reset-password?token=x']) expect(poseFor(url), url).toBe('quiet');
    for (const url of ['/', '/leaderboard', '/me', '/know-me/new', '/vs/new', '/result/5', '/party']) expect(poseFor(url), url).toBe('browse');
  });

  it('shows the brain only on the welcome page', () => {
    expect(POSES.hero.alpha).toBeGreaterThan(0);
    for (const p of [POSES.browse, POSES.game, POSES.quiet]) expect([p.alpha, p.spin]).toEqual([0, 0]);
  });
});

describe('pageMove', () => {
  it('pans between sections in nav order, walks in to detail pages and back out', () => {
    expect(pageMove('/', '/leaderboard')).toBe('right');
    expect(pageMove('/me', '/world')).toBe('left');
    expect(pageMove('/welcome?next=x', '/')).toBe('right');
    expect(pageMove('/', '/play/7?intl=1')).toBe('in');
    expect(pageMove('/result/3', '/')).toBe('out');
    expect(pageMove('/vs/4', '/vs/4/play')).toBe('in');
    expect(pageMove('/admin/stats', '/admin')).toBe('out');
    expect(pageMove('/play/7', '/result/3')).toBe('right');
  });
});

describe('onPageTransition', () => {
  afterEach(() => vi.unstubAllGlobals());

  const snap = (...path: string[]) => ({ url: [], firstChild: { url: path.map((p) => ({ path: p })), firstChild: null } });
  const info = (finished: Promise<void>) => {
    const transition = { finished, skipTransition: vi.fn() };
    return { info: { transition, from: snap(), to: snap('leaderboard') } as unknown as ViewTransitionInfo, transition };
  };

  it('names <main> only while the 3D page change runs', async () => {
    media(false);
    let done!: () => void;
    const { info: i, transition } = info(new Promise<void>((r) => (done = r)));
    onPageTransition(i);
    expect(document.documentElement.classList.contains(PAGE_TRANSITION_CLASS)).toBe(true);
    expect(document.documentElement.dataset['move']).toBe('right');
    done();
    await transition.finished;
    await Promise.resolve();
    expect(document.documentElement.classList.contains(PAGE_TRANSITION_CLASS)).toBe(false);
    expect(document.documentElement.dataset['move']).toBeUndefined();
    expect(transition.skipTransition).not.toHaveBeenCalled();
  });

  it('swaps pages at once with reduced motion', () => {
    media(true);
    const { info: i, transition } = info(Promise.resolve());
    onPageTransition(i);
    expect(transition.skipTransition).toHaveBeenCalledOnce();
    expect(document.documentElement.classList.contains(PAGE_TRANSITION_CLASS)).toBe(false);
  });
});

describe('brain geometry', () => {
  it('is the same on every visit and links neurons only within a hemisphere', () => {
    const a = brainPoints(200);
    expect(brainPoints(200)).toEqual(a);
    const links = brainLinks(a);
    expect(links.length).toBeGreaterThan(0);
    for (let i = 0; i < links.length; i += 2) {
      expect(Math.sign(a[links[i] * 3])).toBe(Math.sign(a[links[i + 1] * 3]));
    }
  });
});
