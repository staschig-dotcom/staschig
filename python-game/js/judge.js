// Проверка решения: видимый случай + скрытые проверки. Без графики — работает и в браузере, и в Node.
//
// Три вида задач (поле mode у уровня):
//   drone (по умолчанию) — программа ведёт дрона по карте; tests — скрытые карты;
//   data — программа получает данные scan() / вопросы ask() и отправляет ответ report(); sample и tests — данные;
//   func — нужно написать функцию fn; examples и tests — наборы аргументов.
//
// judge() возвращает { ok, message, runs, hidden }:
//   runs — что показать в 3D по порядку: первым всегда видимый запуск, а если он прошёл, но скрытая
//   проверка провалилась — вторым запускается тот скрытый случай, на котором всё сломалось.

import { Sim, DataSim, NO_SIM, show } from './sim.js';
import { runProgram, explainError } from './runner.js';

export const modeOf = (lv) => lv.mode ?? 'drone';

const MAX_ITEMS = 9;

// Что рисовать в 3D для набора данных.
function rowFromScan(scan) {
  if (Array.isArray(scan)) {
    const maxAbs = Math.max(1, ...scan.map((n) => Math.abs(n)));
    return scan.slice(0, MAX_ITEMS).map((n) => ({ label: String(n), h: Math.abs(n) / maxAbs, neg: n < 0 }));
  }
  return [{ label: '?', h: 0.1 }];
}

const argsText = (fn, args) => `${fn}(${args.map((a) => JSON.stringify(a)).join(', ')})`;

function funcView(lv) {
  const items = [...lv.examples, ...lv.tests].slice(0, MAX_ITEMS).map((args, i) => ({
    label: i < lv.examples.length ? args.map((a) => JSON.stringify(a)).join(', ') : '?',
    h: 0.3,
    hidden: i >= lv.examples.length,
  }));
  return { row: items };
}

export function initialView(lv) {
  switch (modeOf(lv)) {
    case 'data': return { row: rowFromScan(lv.sample.scan) };
    case 'func': return funcView(lv);
    default: return { map: lv.map, energy: lv.energy ?? null };
  }
}

const lineText = (lines, n) => (n ? ` (строка ${n}: ${lines[n - 1]?.trim() ?? ''})` : '');

// ---------- дрон ----------
function judgeDrone(py, lv, code, drain) {
  const lines = code.split('\n');
  const cases = [{ map: lv.map, energy: lv.energy }, ...(lv.tests ?? []).map((t) => ({ map: t.map, energy: t.energy ?? lv.energy }))];
  const runs = [];
  let message = '';
  for (const [i, c] of cases.entries()) {
    const sim = new Sim({ ...lv, ...c });
    const res = runProgram(py, sim, code);
    const ev = res.ok ? sim.evaluate() : { won: false, reason: explainError(res, lines) };
    const run = { view: { map: c.map, energy: c.energy ?? null }, actions: sim.actions, printed: drain() };
    if (i === 0) {
      runs.push(run);
      if (!ev.won) return { ok: false, message: ev.reason, runs, hidden: 0 };
    } else if (!ev.won) {
      runs.push(run);
      message = `Скрытая карта ${i} из ${cases.length - 1}: ${ev.reason} Здесь карта другая — программа должна работать на любой похожей.`;
      return { ok: false, message, runs, hidden: cases.length - 1 };
    }
  }
  return { ok: true, message, runs, hidden: cases.length - 1 };
}

// ---------- данные ----------
function judgeData(py, lv, code, drain) {
  const lines = code.split('\n');
  const cases = [lv.sample, ...(lv.tests ?? [])];
  const runs = [];
  for (const [i, c] of cases.entries()) {
    const sim = new DataSim(c, lv);
    const res = runProgram(py, sim, code);
    const ev = res.ok ? sim.evaluate() : { won: false, reason: explainError(res, lines) };
    const run = { view: { row: rowFromScan(c.scan) }, actions: sim.actions, printed: drain() };
    if (i === 0) {
      runs.push(run);
      if (!ev.won) return { ok: false, message: ev.reason, runs, hidden: 0 };
    } else if (!ev.won) {
      runs.push(run);
      const what = c.scan !== undefined ? `данные ${show(c.scan)}` : `загаданное число ${c.secret}`;
      return {
        ok: false,
        message: `Скрытый тест ${i} из ${cases.length - 1} (${what}): ${ev.reason}`,
        runs,
        hidden: cases.length - 1,
      };
    }
  }
  return { ok: true, message: '', runs, hidden: cases.length - 1 };
}

// ---------- функции ----------
function judgeFunc(py, lv, code, drain) {
  const lines = code.split('\n');
  const all = [...lv.examples, ...lv.tests];
  const nEx = lv.examples.length;
  const actions = [];
  let message = '';
  let printed = [];
  for (const [i, args] of all.entries()) {
    const res = runProgram(py, NO_SIM, code, [lv.fn, args]);
    if (i === 0) printed = drain(); else drain();
    if (!res.ok && res.type !== 'TooManySteps') {
      // ошибка в самом коде — дальше проверять нет смысла
      return {
        ok: false,
        message: explainError(res, lines),
        runs: [{ view: funcView(lv), actions, printed }],
        hidden: lv.tests.length,
      };
    }
    const where = i < nEx ? `Пример ${i + 1}` : `Скрытый тест ${i - nEx + 1} из ${lv.tests.length}`;
    const call = argsText(lv.fn, args);
    const want = lv.answer(...args);
    let ok = false;
    let note = '';
    if (!res.ok) {
      note = `${where}: ${call} считалась слишком долго${lineText(lines, res.line)}. На олимпиадах за такое пишут «превышено время». Нужен алгоритм быстрее.`;
    } else if (JSON.stringify(res.ret ?? null) === JSON.stringify(want)) {
      ok = true;
    } else if (res.ret === null || res.ret === undefined) {
      note = `${where}: ${call} ничего не вернула, а должна вернуть ${show(want)}. Не забыто ли слово return?`;
    } else {
      note = `${where}: ${call} вернула ${show(res.ret)}, а правильно — ${show(want)}.`;
    }
    actions.push({ t: 'case', i, ok, label: args.map((a) => JSON.stringify(a)).join(', '), got: show(res.ret ?? null) });
    if (!ok && !message) message = note;
  }
  return { ok: !message, message, runs: [{ view: funcView(lv), actions, printed }], hidden: lv.tests.length };
}

export function judge(py, lv, code, drain = () => []) {
  switch (modeOf(lv)) {
    case 'data': return judgeData(py, lv, code, drain);
    case 'func': return judgeFunc(py, lv, code, drain);
    default: return judgeDrone(py, lv, code, drain);
  }
}
