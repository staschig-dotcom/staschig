// Кэш для работы без интернета: всё приложение (включая Python) сохраняется после первого запуска.
const CACHE = 'kodonavt-v2';
const CORE = [
  './', 'index.html', 'style.css', 'manifest.webmanifest', 'icons/icon.svg', 'icons/icon-192.png',
  'js/game.js', 'js/judge.js', 'js/levels.js', 'js/runner.js', 'js/scene.js', 'js/sim.js',
  'vendor/three.module.min.js',
  'vendor/pyodide/pyodide.js', 'vendor/pyodide/pyodide.asm.js', 'vendor/pyodide/pyodide.asm.wasm',
  'vendor/pyodide/python_stdlib.zip', 'vendor/pyodide/pyodide-lock.json',
];

self.addEventListener('install', (e) => {
  e.waitUntil(
    caches.open(CACHE).then((c) => Promise.all(CORE.map((u) => c.add(u).catch(() => {})))).then(() => self.skipWaiting()),
  );
});

self.addEventListener('activate', (e) => {
  e.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim()),
  );
});

// Сначала кэш, потом сеть; для страницы и кода — наоборот, чтобы обновления доходили.
self.addEventListener('fetch', (e) => {
  const req = e.request;
  if (req.method !== 'GET' || new URL(req.url).origin !== location.origin) return;
  const fresh = /\.(html|js|css|webmanifest)$/.test(new URL(req.url).pathname) || req.mode === 'navigate';
  const fromNet = () => fetch(req).then((r) => {
    if (r.ok) caches.open(CACHE).then((c) => c.put(req, r.clone()));
    return r;
  });
  e.respondWith(
    fresh && !req.url.includes('/vendor/')
      ? fromNet().catch(() => caches.match(req))
      : caches.match(req).then((hit) => hit || fromNet()),
  );
});
