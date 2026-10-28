import { LEVELS } from './levels.js';
import { Sim } from './sim.js';
import { initPrelude, runProgram, explainError } from './runner.js';
import { World } from './scene.js';
import { judge, initialView, modeOf } from './judge.js';

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
  { lvl: 10, label: 'n = distance()', text: 'n = distance()', kind: 'stmt' },
  { lvl: 10, label: 'n', text: 'n', kind: 'raw' },
  { lvl: 10, label: 'n // 2', text: 'n // 2', kind: 'raw' },
  { lvl: 10, label: 'distance()', text: 'distance()', kind: 'raw' },
  { lvl: 11, label: 'if not wall_right():', text: 'if not wall_right():', kind: 'open' },
  { lvl: 11, label: 'elif not wall_ahead():', text: 'elif not wall_ahead():', kind: 'else' },
  { lvl: 11, label: 'wall_right()', text: 'wall_right()', kind: 'raw' },
  { lvl: 11, label: 'wall_left()', text: 'wall_left()', kind: 'raw' },
  { lvl: 12, label: 'def charge():', text: 'def charge():', kind: 'open' },
  { lvl: 12, label: 'charge()', text: 'charge()', kind: 'stmt' },
  { lvl: 12, label: 'energy()', text: 'energy()', kind: 'raw' },
  { lvl: 13, label: 'for i in range(1, 6):', text: 'for i in range(1, 6):', kind: 'open', caret: 2 },
  { lvl: 13, label: 'move(i)', text: 'move(i)', kind: 'stmt' },
  { lvl: 13, label: 'i', text: 'i', kind: 'raw' },
  { lvl: 14, label: 'def row():', text: 'def row():', kind: 'open' },
  { lvl: 14, label: 'row()', text: 'row()', kind: 'stmt' },
  { lvl: 14, label: 'for r in range():', text: 'for r in range():', kind: 'open', caret: 2 },
  { lvl: 16, label: 'while', text: 'while ', kind: 'raw' },
  { lvl: 16, label: 'get_x()', text: 'get_x()', kind: 'raw' },
  { lvl: 16, label: 'get_y()', text: 'get_y()', kind: 'raw' },
  { lvl: 16, label: 'goal_x()', text: 'goal_x()', kind: 'raw' },
  { lvl: 16, label: 'goal_y()', text: 'goal_y()', kind: 'raw' },
  { lvl: 16, label: '<', text: ' < ', kind: 'raw' },
  { lvl: 16, label: '>', text: ' > ', kind: 'raw' },
  { lvl: 16, label: '==', text: ' == ', kind: 'raw' },
  { lvl: 16, label: '!=', text: ' != ', kind: 'raw' },
  // Акт 3: больше «кирпичиков» (слова и знаки), а не готовых строк решения.
  { lvl: 19, label: 'if wall_left():', text: 'if wall_left():', kind: 'open' },
  { lvl: 21, label: 'for x in scan():', text: 'for x in scan():', kind: 'open' },
  { lvl: 21, label: 'report()', text: 'report()', kind: 'stmt', caret: 1 },
  { lvl: 21, label: 'scan()', text: 'scan()', kind: 'raw' },
  { lvl: 21, label: 'x', text: 'x', kind: 'raw' },
  { lvl: 22, label: 'for x in t:', text: 'for x in t:', kind: 'open' },
  { lvl: 22, label: 'if x > best:', text: 'if x > best:', kind: 'open' },
  { lvl: 23, label: 'if x % 2 == 0:', text: 'if x % 2 == 0:', kind: 'open' },
  { lvl: 24, label: 'def bigger(a, b):', text: 'def bigger(a, b):', kind: 'open' },
  { lvl: 24, label: 'return', text: 'return ', kind: 'stmt' },
  { lvl: 24, label: 'if a > b:', text: 'if a > b:', kind: 'open' },
  { lvl: 25, label: 'def digit_sum(n):', text: 'def digit_sum(n):', kind: 'open' },
  { lvl: 25, label: 'while n > 0:', text: 'while n > 0:', kind: 'open' },
  { lvl: 26, label: 'def is_prime(n):', text: 'def is_prime(n):', kind: 'open' },
  { lvl: 26, label: 'return True', text: 'return True', kind: 'stmt' },
  { lvl: 26, label: 'return False', text: 'return False', kind: 'stmt' },
  { lvl: 28, label: 'if x in seen:', text: 'if x in seen:', kind: 'open' },
  { lvl: 28, label: '.append()', text: '.append()', kind: 'raw', caret: 1 },
  { lvl: 29, label: 'ask()', text: 'ask()', kind: 'raw', caret: 1 },
  { lvl: 29, label: 'while True:', text: 'while True:', kind: 'open' },
  { lvl: 29, label: 'break', text: 'break', kind: 'stmt' },
  { lvl: 30, label: 'for i in range(1, len(data)):', text: 'for i in range(1, len(data)):', kind: 'open', caret: 2 },
  { lvl: 30, label: 'len()', text: 'len()', kind: 'raw', caret: 1 },
];
// Самые нужные кнопки всегда слева, остальные — от новых к старым.
const CORE = new Set(['move()', 'turn_left()', 'turn_right()', 'take()']);
const CTL = [
  { label: '↵', act: 'enter', title: 'Новая строка' },
  { label: '⇥', act: 'indent', title: 'Отступ вправо' },
  { label: '⇤', act: 'outdent', title: 'Отступ влево' },
  { label: ':', act: 'raw', text: ':' },
  { label: '=', act: 'raw', text: ' = ' },
  { label: '1', act: 'raw', text: '1' },
  { label: '2', act: 'raw', text: '2' },
  { label: '3', act: 'raw', text: '3' },
  { label: '4', act: 'raw', text: '4' },
  { label: '5', act: 'raw', text: '5' },
  { label: '0', act: 'raw', text: '0', lvl: 21 },
  { label: '( )', act: 'raw', text: '()', lvl: 21, caret: 1 },
  { label: '[ ]', act: 'raw', text: '[]', lvl: 21, caret: 1 },
  { label: ',', act: 'raw', text: ', ', lvl: 21 },
  { label: '+=', act: 'raw', text: ' += ', lvl: 21 },
  { label: '+ −', act: 'raw', text: ' + ', lvl: 21 },
  { label: '%', act: 'raw', text: ' % ', lvl: 23 },
  { label: '//', act: 'raw', text: ' // ', lvl: 24 },
];

// ---------- состояние ----------
let py = null;
let world = null;
let current = 0;
let hintIdx = 0;
let fails = 0; // неудачные запуски подряд на этом уровне — после двух предлагаем «Разбор»
let running = false;
let speedIdx = 0;
const SPEEDS = [1, 2, 4, 8];
const printed = [];

const code = $('code');
const status = $('status');

function setStatus(text, cls = '') {
  status.textContent = text;
  status.className = cls;
}

let speechTimer = null;
function say(html, autoHideMs = 0) {
  $('speech').innerHTML = html;
  clearTimeout(speechTimer);
  if (html && autoHideMs) speechTimer = setTimeout(() => say(''), autoHideMs);
}

// ---------- меню ----------
const ACTS = [
  { from: 1, title: 'Акт 1 · Пробуждение' },
  { from: 10, title: 'Акт 2 · Мостик' },
  { from: 19, title: 'Акт 3 · Нижние палубы' },
];

function buildMenu() {
  const box = $('levels');
  box.innerHTML = '';
  LEVELS.forEach((lv, i) => {
    const act = ACTS.find((a) => a.from === lv.id);
    if (act) {
      const h = document.createElement('div');
      h.className = 'act';
      h.textContent = act.title;
      box.appendChild(h);
    }
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
  const avail = CHIPS.filter((c) => c.lvl <= level.id);
  const rest = avail.filter((c) => !CORE.has(c.label)).sort((a, b) => b.lvl - a.lvl);
  for (const c of avail.filter((c) => CORE.has(c.label))) mk(c.label, '', () => applyChip(c));
  for (const c of CTL.filter((c) => !c.lvl || c.lvl <= level.id)) mk(c.label, c.title, () => applyCtl(c), 'ctl');
  for (const c of rest) mk(c.label, '', () => applyChip(c));
}

function openLevel(i) {
  current = i;
  hintIdx = 0;
  fails = 0;
  const lv = LEVELS[i];
  $('menu').classList.add('hidden');
  $('result').classList.add('hidden');
  $('game').classList.remove('hidden');
  $('task-title').textContent = `${lv.id}. ${lv.title}`;
  $('task-text').textContent = lv.task;
  $('hint').classList.add('hidden');
  $('guidebtn').classList.toggle('hidden', !lv.guide);
  $('guidebtn').classList.remove('pulse');
  code.value = save.code[lv.id] ?? lv.starter ?? '';
  buildChips(lv);
  setStatus('');
  say(`<b>ИСКРА:</b> ${lv.story}`, 9000);
  restoreView();
  setRunning(false);
}

// Показать в 3D стартовую картинку уровня (видимая карта или данные).
function restoreView() {
  const lv = LEVELS[current];
  world.show(initialView(lv));
  setEnergy(lv.energy ?? null);
}

function setEnergy(v, max = LEVELS[current].energy) {
  const el = $('energy');
  if (v === null || v === undefined || max === undefined) return el.classList.add('hidden');
  el.classList.remove('hidden');
  el.textContent = `⚡ ${v}/${max}`;
  el.classList.toggle('low', v <= 1);
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
  if (c.act === 'raw') return applyChip({ text: c.text, kind: 'raw', caret: c.caret });
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
  if (!src.trim()) {
    return setStatus(modeOf(lv) === 'drone' ? 'Сначала напиши программу — например, move(1).' : 'Сначала напиши код.', 'err');
  }
  $('result').classList.add('hidden');
  code.blur();
  say('');
  setRunning(true);
  restoreView();
  printed.length = 0;

  // Python выполняется сразу (и видимый случай, и скрытые проверки), потом 3D проигрывает журнал.
  const verdict = judge(py, lv, src, () => printed.splice(0));
  const srcLines = src.split('\n');
  const onAction = (a) => {
    if (a.line) setStatus(`Строка ${a.line}:  ${srcLines[a.line - 1]?.trim() ?? ''}`);
    if (a.energy != null) setEnergy(a.energy);
  };
  for (const [i, r] of verdict.runs.entries()) {
    if (i > 0) {
      setStatus('Проверка на скрытых данных… показываю, где сломалось.');
      world.show(r.view);
      setEnergy(r.view.energy ?? null);
    }
    const completed = await world.play(r.actions, onAction);
    if (!completed) return setRunning(false); // нажали «сброс» или ушли в меню
    if (i === 0 && r.printed.length) say(`<b>Дрон говорит:</b> ${r.printed.map(escapeHtml).join('<br>')}`);
  }

  if (!verdict.ok) {
    fails++;
    const offer = fails >= 2 && lv.guide;
    setStatus(verdict.message + (offer ? '  Не получается? Загляни в «📖 Разбор».' : ''), 'err');
    $('guidebtn').classList.toggle('pulse', !!offer);
    return setRunning(false);
  }
  setStatus(
    verdict.hidden ? `Готово! Скрытые проверки пройдены: ${verdict.hidden} из ${verdict.hidden} ✓` : 'Готово!',
    'ok',
  );
  await world.celebrate();
  win(lv, codeLines(src), verdict.hidden);
  setRunning(false);
}

const escapeHtml = (s) => s.replace(/[&<>]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' })[c]);

function win(lv, lines, hidden = 0) {
  const stars = starsFor(lv, lines);
  save.stars[lv.id] = Math.max(save.stars[lv.id] || 0, stars);
  persist();
  $('stars').innerHTML = [1, 2, 3].map((n) => `<span class="${n <= stars ? '' : 'off'}">★</span>`).join('');
  $('result-title').textContent = 'Уровень пройден!';
  $('result-text').textContent =
    `${lv.win}\n${hidden ? `Скрытых проверок пройдено: ${hidden}\n` : ''}Строк кода: ${lines}` +
    (lines > lv.par ? ` — а можно за ${lv.par}. Попробуй короче, чтобы получить 3 звезды!` : ' — идеально коротко!');
  $('result-text').style.whiteSpace = 'pre-line';
  $('r-next').classList.toggle('hidden', current >= LEVELS.length - 1);
  $('result').classList.remove('hidden');
}

// ---------- кнопки ----------
$('run').onclick = run;
$('reset').onclick = () => {
  restoreView();
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
  const idx = Math.min(hintIdx, lv.hints.length - 1);
  h.textContent = lv.hints[idx];
  if (idx === lv.hints.length - 1 && /\n|\(/.test(lv.hints[idx])) {
    // последняя подсказка — готовый код: даём вставить его, чтобы не мучиться с набором
    const b = document.createElement('button');
    b.className = 'chip';
    b.textContent = '⤵ Заменить мой код этим';
    b.onclick = () => setCode(lv.hints[idx], lv.hints[idx].length);
    h.appendChild(document.createElement('br'));
    h.appendChild(b);
  }
  h.classList.remove('hidden');
  hintIdx++;
};
// «Разбор»: пошаговый пример с растущим кодом, каждый шаг можно вставить в редактор.
function showGuide(i) {
  const g = LEVELS[current].guide;
  const n = Math.max(0, Math.min(i, g.length - 1));
  const step = g[n];
  const h = $('hint');
  h.textContent = '';
  const el = (tag, cls, text) => {
    const e = document.createElement(tag);
    e.className = cls;
    e.textContent = text;
    h.appendChild(e);
    return e;
  };
  el('div', 'gtitle', `Разбор · шаг ${n + 1} из ${g.length}`);
  el('div', 'gtext', step.text);
  el('pre', 'gcode', step.code);
  const row = document.createElement('div');
  row.className = 'grow';
  const btn = (label, fn) => {
    const b = document.createElement('button');
    b.className = 'chip';
    b.textContent = label;
    b.onclick = fn;
    row.appendChild(b);
  };
  if (n > 0) btn('◀ Назад', () => showGuide(n - 1));
  btn('⤵ Вставить код', () => setCode(step.code, step.code.length));
  if (n < g.length - 1) btn('Дальше ▶', () => showGuide(n + 1));
  h.appendChild(row);
  h.classList.remove('hidden');
  $('guidebtn').classList.remove('pulse');
}
$('guidebtn').onclick = () => showGuide(0);
$('back').onclick = showMenu;
$('speech').onclick = () => say('');
$('storybtn').onclick = () => say(`<b>ИСКРА:</b> ${LEVELS[current].story}`, 12000);
$('r-menu').onclick = showMenu;
$('r-retry').onclick = () => {
  $('result').classList.add('hidden');
  restoreView();
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
window.__booted = true;

async function bootPython() {
  try {
    py = await loadPyodide({ indexURL: new URL('vendor/pyodide/', location.href).href });
    py.setStdout({ batched: (s) => printed.push(s) });
    py.setStderr({ batched: () => {} });
    initPrelude(py);
  } catch (e) {
    console.error(e);
    window.showFatal?.('Не удалось загрузить Python: ' + e.message);
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
