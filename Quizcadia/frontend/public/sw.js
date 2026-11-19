// QuizCadia service worker: makes the app installable and opens it instantly (and offline) after the first visit.
// - API calls (/api/*) always go to the network: scores and questions must never come from a cache.
// - Pages: network first, falling back to the cached app shell when offline.
// - Hashed build files, icons and fonts: cache first (their names change when their content does).
const CACHE = 'quizcadia-v3'; // bump when unhashed files (icons, manifest) change, so old copies are dropped
const SHELL = ['/', '/manifest.webmanifest', '/favicon.svg', '/icon-192.png', '/icon-512.png'];

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(CACHE).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener('fetch', (event) => {
  const req = event.request;
  const url = new URL(req.url);
  if (req.method !== 'GET' || url.origin !== self.location.origin || url.pathname.startsWith('/api/')) {
    return; // let the browser handle it
  }
  if (req.mode === 'navigate') {
    // Coming back from another site (Google's sign-in posts back and is redirected to /welcome?google=...): let the
    // browser load the page itself. Safari intermittently showed a black page when this went through the worker,
    // and there's nothing to gain here: the network just answered, so we're not offline.
    if (url.searchParams.has('google') || crossSite(req.referrer)) {
      return;
    }
    event.respondWith(
      fetch(req)
        .then((res) => {
          // during a deploy the proxy answers with its maintenance page: open the cached app instead (it shows its own
          // maintenance banner and recovers by itself), and never cache that page as the app shell
          if (res.status >= 500) {
            return caches.match('/').then((hit) => hit || res);
          }
          const copy = res.clone();
          caches.open(CACHE).then((c) => c.put('/', copy));
          return res;
        })
        // never answer a page load with nothing (Safari shows that as a blank page): the cached app, else one plain
        // retry, else a small page that says what happened
        .catch(() => caches.match('/'))
        .then((hit) => hit || fetch(req.url, { credentials: 'same-origin' }))
        .catch(() => offlinePage()),
    );
    return;
  }
  if (/\.(js|css|woff2?|png|svg|ico|webmanifest)$/.test(url.pathname)) {
    event.respondWith(
      caches.match(req).then((hit) => hit || fetch(req).then((res) => {
        if (res.ok) {
          const copy = res.clone();
          caches.open(CACHE).then((c) => c.put(req, copy));
        }
        return res;
      })),
    );
  }
});

// Streak reminders (Web Push). The server sends {title, body, url, tag}; a tag replaces an older notification of
// the same kind instead of stacking up.
self.addEventListener('push', (event) => {
  let msg = {};
  try {
    msg = event.data ? event.data.json() : {};
  } catch {
    msg = { body: event.data ? event.data.text() : '' };
  }
  event.waitUntil(
    self.registration.showNotification(msg.title || 'QuizCadia', {
      body: msg.body || '',
      icon: '/icon-192.png',
      badge: '/icon-192.png',
      tag: msg.tag || 'quizcadia',
      data: { url: typeof msg.url === 'string' && msg.url.startsWith('/') ? msg.url : '/' },
    }),
  );
});

// Tapping it opens the page it points to, in an open QuizCadia tab when there is one.
self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const url = new URL(event.notification.data?.url || '/', self.location.origin).href;
  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((wins) => {
      const open = wins.find((w) => new URL(w.url).origin === self.location.origin);
      if (open) {
        return open.focus().then((w) => (w && 'navigate' in w ? w.navigate(url) : null));
      }
      return self.clients.openWindow(url);
    }),
  );
});

/** A navigation that came from another site (its referrer); an empty or unreadable referrer counts as ours. */
function crossSite(referrer) {
  try {
    return !!referrer && new URL(referrer).origin !== self.location.origin;
  } catch {
    return false;
  }
}

/** What a page load shows when neither the network nor the cache can give the app. */
function offlinePage() {
  return new Response(
    '<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">' +
      '<title>QuizCadia</title><body style="font-family:system-ui;background:#08070f;color:#f5f3ff;display:grid;place-items:center;min-height:90vh;text-align:center">' +
      '<div><h1>Couldn\'t load QuizCadia</h1><p>Check your connection and try again.</p>' +
      '<p><a href="" style="color:#ffb547;font-weight:700">Try again</a></p></div>',
    { status: 503, headers: { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store' } },
  );
}

