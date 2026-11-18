import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { NEURAL_SCENE_LOADER, NeuralBg } from './neural-bg';
import { brainLinks, brainPoints, NeuralScene, SceneFactory } from './neural-scene';

function media(reduced: boolean) {
  vi.stubGlobal('matchMedia', (q: string) => ({
    matches: reduced && q.includes('reduce'), media: q, addEventListener() {}, removeEventListener() {},
  }));
}

function fakeScene(): NeuralScene {
  return { render: vi.fn(), resize: vi.fn(), setColors: vi.fn(), dispose: vi.fn() };
}

async function mount(factory?: SceneFactory) {
  const load = vi.fn(async () => factory ?? (() => null));
  TestBed.configureTestingModule({ providers: [{ provide: NEURAL_SCENE_LOADER, useValue: load }] });
  const f = TestBed.createComponent(NeuralBg);
  await f.whenStable();
  await new Promise((r) => setTimeout(r));
  await f.whenStable();
  return { f, load, host: f.nativeElement as HTMLElement };
}

describe('NeuralBg', () => {
  const frames = () => new Promise((r) => setTimeout(r, 80));

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

  it('falls back when the browser has WebGL but no context can be made', async () => {
    media(false);
    vi.stubGlobal('WebGLRenderingContext', class {});
    const { load, host } = await mount(() => null);

    expect(load).toHaveBeenCalledOnce();
    expect(host.getAttribute('data-mode')).toBe('fallback');
  });

  it('with reduced motion it draws one still frame and never starts the animation loop', async () => {
    media(true);
    vi.stubGlobal('WebGLRenderingContext', class {});
    const scene = fakeScene();
    const { host } = await mount(() => scene);

    await frames();
    expect(host.getAttribute('data-mode')).toBe('still');
    expect(scene.render).toHaveBeenCalledOnce();
  });

  it('animates normally, and frees the GL scene and its listeners when destroyed', async () => {
    media(false);
    vi.stubGlobal('WebGLRenderingContext', class {});
    const scene = fakeScene();
    const removed = vi.spyOn(window, 'removeEventListener');
    const { f, host } = await mount(() => scene);

    await frames();
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
