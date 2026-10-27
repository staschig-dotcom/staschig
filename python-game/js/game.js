import { LEVELS } from './levels.js';
import { Sim } from './sim.js';
import { initPrelude, runProgram, explainError } from './runner.js';
import { World } from './scene.js';

const $ = (id) => document.getElementById(id);
const SAVE_KEY = 'kodonavt.v1';
const UNLOCK_ALL = new URLSearchParams(location.search).has('all');

// ---------- сохранения ----------
let save = { stars: {}, code: {} };
try {
  save = { ...save, ...JSON.parse(localStorage.getItem(SAVE_KEY) || '{}') };
} catch { /* приватный режим — играем без сохранений */ }
const persist = () => {
  try { localStorage.setItem(SAVE_KEY, JSON.stringify(save)); } catch { /* ignore */ }
};

// ---------- кнопки-подсказки для набора кода на телефоне ----------
// kind: stmt — отдельная строка, open — строка с двоеточием (дальше отступ),
// else — как open, но на уровень левее, raw — вставить как есть; caret — на сколько символов
// от конца вставки поставить курсор.
const CHIPS = [
  { lvl: 1, label: 'move()', text: 'move()', kind: 'stmt', caret: 1 },
  { lvl: 2, label: 'turn_left()', text: 'turn_left()', kind: 'stmt' },
  { lvl: 2, label: 'turn_right()', text: 'turn_right()', kind: 'stmt' },
  { lvl: 3, label: 'for i in range():', text: 'for i in range():', kind: 'open', caret: 2 },
  { lvl: 4, label: 'take()', text: 'take()', kind: 'stmt' },
  { lvl: 5, label: 'while not wall_ahead():', text: 'while not wall_ahead():', kind: 'open' },
  { lvl: 5, label: 'wall_ahead()', text: 'wall_ahead()', kind: 'raw' },
  { lvl: 5, label: 'not', text: 'not ', kind: 'raw' },
  { lvl: 6, label: 'while not at_goal():', text: 'while not at_goal():', kind: 'open' },
  { lvl: 6, label: 'if wall_ahead():', text: 'if wall_ahead():', kind: 'open' },
  { lvl: 6, label: 'else:', text: 'else:', kind: 'else' },
  { lvl: 6, label: 'at_goal()', text: 'at_goal()', kind: 'raw' },
  { lvl: 7, label: 'if on_crystal():', text: 'if on_crystal():', kind: 'open' },
  { lvl: 7, label: 'on_crystal()', text: 'on_crystal()', kind: 'raw' },
  { lvl: 8, label: 'jump()', text: 'jump()', kind: 'stmt' },
  { lvl: 8, label: 'if laser_ahead():', text: 'if laser_ahead():', kind: 'open' },
  { lvl: 8, label: 'laser_ahead()', text: 'laser_ahead()', kind: 'raw' },
  { lvl: 9, label: 'def step():', text: 'def step():', kind: 'open' },
  { lvl: 9, label: 'step()', text: 'step()', kind: 'stmt' },
];
const CTL = [
  { label: '↵', act: 'enter', title: 'Новая строка' },
  { label: '⇥', act: 'indent', title: 'Отступ вправо' },
  { label: '⇤', act: 'outdent', title: 'Отступ влево' },
  { label: ':', act: 'raw', text: ':' },
  { label: '1', act: 'raw', text: '1' },
  { label: '2', act: 'raw', text: '2' },
  { label: '3', act: 'raw', text: '3' },
  { label: '4', act: 'raw', text: '4' },
  { label: '5', act: 'raw', text: '5' },
];

// ---------- состояние ----------
let py = null;
let world = null;
let current = 0;
let hintIdx = 0;
let running = false;
let speedIdx = 0;
const SPEEDS = [1, 2, 4];
const printed = [];

const code = $('code');
const status = $('status');

function setStatus(text, cls = '') {
  status.textContent = text;
  status.className = cls;
}

function say(html) {
  $('speech').innerHTML = html;
}

// ---------- меню ----------
function buildMenu() {
  const box = $('levels');
  box.innerHTML = '';
  LEVELS.forEach((lv, i) => {
    const stars = save.stars[lv.id] || 0;
    const unlocked = UNLOCK_ALL || i === 0 || (save.stars[LEVELS[i - 1].id] || 0) > 0;
    const b = document.createElement('button');
    b.className = 'lvl' + (unlocked ? '' : ' locked');
    b.innerHTML =
      `<span class="n">${lv.id}</span><span class="t">${lv.title}</span>` +
      `<span class="s">${stars ? '★'.repeat(stars) + '☆'.repeat(3 - stars) : unlocked ? '☆☆☆' : '🔒'}</span>`;
    b.onclick = () => openLevel(i);
    box.appendChild(b);
  });
}

function showMenu() {
  world?.stop();
  $('game').classList.add('hidden');
  $('result').classList.add('hidden');
  $('menu').classList.remove('hidden');
  buildMenu();
}

// ---------- уровень ----------
function buildChips(level) {
  const box = $('chips');
  box.innerHTML = '';
  const mk = (label, title, fn, cls = '') => {
    const b = document.createElement('button');
    b.className = 'chip ' + cls;
    b.textContent = label;
    if (title) b.title = title;
    b.onpointerdown = (e) => e.preventDefault(); // не терять фокус и не прятать клавиатуру
    b.onclick = fn;
    box.appendChild(b);
  };
  for (const c of CHIPS.filter((c) => c.lvl <= level.id)) mk(c.label, '', () => applyChip(c));
  for (const c of CTL) mk(c.label, c.title, () => applyCtl(c), 'ctl');
}

function openLevel(i) {
  current = i;
  hintIdx = 0;
  const lv = LEVELS[i];
  $('menu').classList.add('hidden');
  $('result').classList.add('hidden');
  $('game').classList.remove('hidden');
  $('task-title').textContent = `${lv.id}. ${lv.title}`;
  $('task-text').textContent = lv.task;
  $('hint').classList.add('hidden');
  code.value = save.code[lv.id] ?? '';
  buildChips(lv);
  setStatus('');
  say(`<b>ИСКРА:</b> ${lv.story}`);
  world.load(lv.map);
  setRunning(false);
}

function setRunning(v) {
  running = v;
  const run = $('run');
  run.disabled = !py || v;
  run.textContent = !py ? '⏳ Python…' : v ? '… выполняю' : '▶ Запуск';
}

// ---------- редактор ----------
code.addEventListener('input', () => {
  save.code[LEVELS[current].id] = code.value;
  persist();
});

function lineInfo() {
  const v = code.value;
  const pos = code.selectionStart;
  const ls = v.lastIndexOf('\n', pos - 1) + 1;
  const nl = v.indexOf('\n', pos);
  const le = nl < 0 ? v.length : nl;
  const line = v.slice(ls, le);
  const indent = /^ */.exec(line)[0];
  return { v, pos, ls, le, line, indent, blank: line.trim() === '' };
}

function setCode(value, caret) {
  code.value = value;
  code.setSelectionRange(caret, caret);
  code.dispatchEvent(new Event('input'));
}

function applyChip(c) {
  const { v, pos, ls, le, indent, blank } = lineInfo();
  const base = c.kind === 'else' ? indent.slice(0, Math.max(0, indent.length - 4)) : indent;
  let before;
  let after;
  if (c.kind === 'raw') {
    before = v.slice(0, pos);
    after = v.slice(code.selectionEnd);
  } else if (blank) {
    before = v.slice(0, c.kind === 'else' ? ls : pos) + (c.kind === 'else' ? base : '');
    after = v.slice(c.kind === 'else' ? le : code.selectionEnd);
  } else {
    // команда всегда добавляется новой строкой после текущей — даже если курсор внутри скобок
    before = v.slice(0, le) + '\n' + base;
    after = v.slice(le);
  }
  let text = c.text;
  if ((c.kind === 'open' || c.kind === 'else') && !c.caret) text += '\n' + base + '    ';
  setCode(before + text + after, before.length + text.length - (c.caret || 0));
}

// atEnd: кнопка «↵» заканчивает строку целиком, а клавиша Enter режет строку по курсору.
function newline(atEnd) {
  const { v, pos, ls, le, line, indent } = lineInfo();
  const cut = atEnd ? le : pos;
  const head = atEnd ? line : v.slice(ls, pos);
  const extra = head.trimEnd().endsWith(':') ? '    ' : '';
  const ins = '\n' + indent + extra;
  setCode(v.slice(0, cut) + ins + v.slice(atEnd ? le : code.selectionEnd), cut + ins.length);
}

function applyCtl(c) {
  if (c.act === 'enter') return newline(true);
  if (c.act === 'raw') return applyChip({ text: c.text, kind: 'raw' });
  const { v, pos, ls, le, indent, blank } = lineInfo();
  if (c.act === 'indent') {
    return setCode(v.slice(0, ls) + '    ' + v.slice(ls), pos + 4);
  }
  if (blank) {
    const cut = Math.min(4, indent.length);
    return setCode(v.slice(0, ls) + v.slice(ls + cut), Math.max(ls, pos - cut));
  }
  // ⇤ на непустой строке — «выйти из блока»: новая строка на уровень левее
  const ins = '\n' + indent.slice(0, Math.max(0, indent.length - 4));
  return setCode(v.slice(0, le) + ins + v.slice(le), le + ins.length);
}

code.addEventListener('keydown', (e) => {
  if (e.key === 'Enter' && !e.shiftKey && !e.ctrlKey) {
    e.preventDefault();
    newline(false);
  } else if (e.key === 'Tab') {
    e.preventDefault();
    applyCtl({ act: 'indent' });
  }
});

// ---------- запуск программы ----------
function codeLines(src) {
  return src.split('\n').filter((l) => l.trim() && !l.trim().startsWith('#')).length;
}

function starsFor(lv, lines) {
  return lines <= lv.par ? 3 : lines <= lv.par + 3 ? 2 : 1;
}

async function run() {
  if (running || !py) return;
  const lv = LEVELS[current];
  const src = code.value;
  if (!src.trim()) return setStatus('Сначала напиши программу — например, move(1).', 'err');
  $('result').classList.add('hidden');
  code.blur();
  say('');
  setRunning(true);
  world.reset();
  printed.length = 0;

  const sim = new Sim(lv);
  const res = runProgram(py, sim, src);
  const srcLines = src.split('\n');
  const completed = await world.play(sim.actions, (a) => {
    if (a.line) setStatus(`Строка ${a.line}:  ${srcLines[a.line - 1]?.trim() ?? ''}`);
  });
  if (!completed) return setRunning(false); // нажали «сброс» или ушли в меню

  if (printed.length) say(`<b>Дрон говорит:</b> ${printed.map(escapeHtml).join('<br>')}`);

  if (!res.ok) {
    setStatus(explainError(res), 'err');
    return setRunning(false);
  }
  const ev = sim.evaluate();
  if (!ev.won) {
    setStatus(ev.reason, 'err');
    return setRunning(false);
  }
  setStatus('Маяк достигнут!', 'ok');
  await world.celebrate();
  win(lv, codeLines(src));
  setRunning(false);
}

const escapeHtml = (s) => s.replace(/[&<>]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' })[c]);

function win(lv, lines) {
  const stars = starsFor(lv, lines);
  save.stars[lv.id] = Math.max(save.stars[lv.id] || 0, stars);
  persist();
  $('stars').innerHTML = [1, 2, 3].map((n) => `<span class="${n <= stars ? '' : 'off'}">★</span>`).join('');
  $('result-title').textContent = 'Уровень пройден!';
  $('result-text').textContent =
    `${lv.win}\nСтрок кода: ${lines}` +
    (lines > lv.par ? ` — а можно за ${lv.par}. Попробуй короче, чтобы получить 3 звезды!` : ' — идеально коротко!');
  $('result-text').style.whiteSpace = 'pre-line';
  $('r-next').classList.toggle('hidden', current >= LEVELS.length - 1);
  $('result').classList.remove('hidden');
}

// ---------- кнопки ----------
$('run').onclick = run;
$('reset').onclick = () => {
  world.reset();
  setRunning(false);
  setStatus('');
};
$('speed').onclick = () => {
  speedIdx = (speedIdx + 1) % SPEEDS.length;
  world.speed = SPEEDS[speedIdx];
  $('speed').textContent = `${SPEEDS[speedIdx]}×`;
};
$('hintbtn').onclick = () => {
  const lv = LEVELS[current];
  const h = $('hint');
  h.textContent = lv.hints[Math.min(hintIdx, lv.hints.length - 1)];
  h.classList.remove('hidden');
  hintIdx++;
};
$('back').onclick = showMenu;
$('speech').onclick = () => say('');
$('r-menu').onclick = showMenu;
$('r-retry').onclick = () => {
  $('result').classList.add('hidden');
  world.reset();
  setStatus('');
};
$('r-next').onclick = () => openLevel(current + 1);

// Кнопка «Назад» в Android-приложении: из уровня — в меню, из меню — выход.
window.kodonavtBack = () => {
  if (!$('game').classList.contains('hidden')) {
    showMenu();
    return true;
  }
  return false;
};

// ---------- запуск приложения ----------
world = new World($('c'));
buildMenu();
setRunning(false);

async function bootPython() {
  try {
    py = await loadPyodide({ indexURL: new URL('vendor/pyodide/', location.href).href });
    py.setStdout({ batched: (s) => printed.push(s) });
    py.setStderr({ batched: () => {} });
    initPrelude(py);
  } catch (e) {
    console.error(e);
    setStatus('Не удалось загрузить Python: ' + e.message, 'err');
    return;
  }
  setRunning(running);
}
bootPython();

// В APK все файлы уже внутри приложения, service worker нужен только в браузере.
const inApp = typeof window.KodonavtNative !== 'undefined';
if (!inApp && 'serviceWorker' in navigator && (location.protocol === 'https:' || location.hostname === 'localhost')) {
  navigator.serviceWorker.register('sw.js').catch(() => {});
}
