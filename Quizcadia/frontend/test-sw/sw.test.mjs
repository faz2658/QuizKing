// The service worker (public/sw.js), run in Node against fake browser objects: node --test test-sw/
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import vm from 'node:vm';

const source = readFileSync(new URL('../public/sw.js', import.meta.url), 'utf8');

/** Loads sw.js with a given network and cache; returns a function that dispatches one fetch event. */
function worker({ network, cached = null }) {
  const handlers = {};
  const fetches = [];
  const self = {
    location: { origin: 'https://quizcadia.com' },
    addEventListener: (type, fn) => (handlers[type] = fn),
    registration: { showNotification: () => Promise.resolve() },
    clients: { claim: () => Promise.resolve() },
    skipWaiting: () => Promise.resolve(),
  };
  const caches = {
    match: async () => cached,
    open: async () => ({ put: async () => undefined, addAll: async () => undefined }),
    keys: async () => [],
    delete: async () => true,
  };
  const fetch = async (req, init) => {
    fetches.push(typeof req === 'string' ? req : req.url);
    return network(fetches.length, init);
  };
  vm.runInNewContext(source, { self, caches, fetch, Response, URL, console });
  return {
    fetches,
    async navigate(url, referrer = '') {
      let responded = null;
      handlers.fetch({ request: { url, method: 'GET', mode: 'navigate', referrer }, respondWith: (p) => (responded = p) });
      return responded === null ? 'browser' : await responded;
    },
  };
}

const page = (status = 200) => new Response('<app-root></app-root>', { status, headers: { 'Content-Type': 'text/html' } });

test('a page arriving from Google sign-in is left to the browser', async () => {
  const sw = worker({ network: () => page() });
  assert.equal(await sw.navigate('https://quizcadia.com/welcome?google=ok'), 'browser');
  assert.equal(await sw.navigate('https://quizcadia.com/welcome?google=failed'), 'browser');
  assert.equal(await sw.navigate('https://quizcadia.com/welcome', 'https://accounts.google.com/'), 'browser');
  assert.equal(sw.fetches.length, 0);
});

test('ordinary page loads still go through the worker', async () => {
  const sw = worker({ network: () => page() });
  const res = await sw.navigate('https://quizcadia.com/leaderboard', 'https://quizcadia.com/');
  assert.equal(res.status, 200);
});

test('offline with nothing cached: a retry, then a page that explains, never an empty answer', async () => {
  const sw = worker({ network: () => Promise.reject(new TypeError('Load failed')) });
  const res = await sw.navigate('https://quizcadia.com/');
  assert.ok(res instanceof Response, 'answered with a real Response');
  assert.equal(res.status, 503);
  assert.match(await res.text(), /Couldn't load QuizCadia/);
  assert.equal(sw.fetches.length, 2); // the navigation, then one plain retry
});

test('a failed navigation fetch that works on a plain retry shows the page', async () => {
  const sw = worker({ network: (n) => (n === 1 ? Promise.reject(new TypeError('Load failed')) : page()) });
  const res = await sw.navigate('https://quizcadia.com/');
  assert.equal(res.status, 200);
});

test('offline with the app cached: the cached app', async () => {
  const cached = page();
  const sw = worker({ network: () => Promise.reject(new TypeError('offline')), cached });
  assert.equal(await sw.navigate('https://quizcadia.com/'), cached);
});

test('during a deploy (5xx): the cached app, else the proxy page', async () => {
  const cached = page();
  assert.equal(await worker({ network: () => page(502), cached }).navigate('https://quizcadia.com/'), cached);
  assert.equal((await worker({ network: () => page(502) }).navigate('https://quizcadia.com/')).status, 502);
});
