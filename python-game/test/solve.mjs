// Запуск: node python-game/test/solve.mjs
// Проверяет, что каждый уровень проходится эталонным решением (с настоящим Python),
// и что типичные ошибки ребёнка дают понятные сообщения.
import { loadPyodide } from '../vendor/pyodide/pyodide.mjs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { LEVELS } from '../js/levels.js';
import { Sim } from '../js/sim.js';
import { initPrelude, runProgram, explainError } from '../js/runner.js';
import { judge, modeOf } from '../js/judge.js';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const py = await loadPyodide({ indexURL: join(root, 'vendor/pyodide/') });
initPrelude(py);

const SOLUTIONS = {
  1: 'move(5)',
  2: 'move(2)\nturn_right()\nmove(3)',
  3: 'for i in range(4):\n    move()\n    turn_right()\n    move()\n    turn_left()\nmove()',
  4: 'for i in range(3):\n    move(3)\n    take()\n    turn_right()\nmove(2)',
  5: 'while not wall_ahead():\n    move()\nturn_right()\nwhile not wall_ahead():\n    move()',
  6: 'while not at_goal():\n    if wall_ahead():\n        turn_right()\n    else:\n        move()',
  7: 'while not at_goal():\n    move()\n    if on_crystal():\n        take()',
  8: 'while not at_goal():\n    if laser_ahead():\n        jump()\n    else:\n        move()',
  10: 'n = distance()\nmove(n // 2)\ntake()\nmove(n // 2)',
  11: 'while not at_goal():\n    if not wall_right():\n        turn_right()\n        move()\n    elif not wall_ahead():\n        move()\n    else:\n        turn_left()',
  12: 'def charge():\n    turn_right()\n    move()\n    take()\n    turn_right()\n    turn_right()\n    move()\n    turn_right()\n\nfor i in range(3):\n    move(3)\n    charge()\nmove(2)',
  13: 'for i in range(1, 6):\n    move(i)\n    turn_right()',
  14: 'def row():\n    for i in range(4):\n        move()\n        take()\n\nfor r in range(2):\n    row()\n    turn_right()\n    move()\n    turn_right()\n    row()\n    turn_left()\n    move()\n    turn_left()\nrow()',
  15: 'while not at_goal():\n    if not wall_right():\n        turn_right()\n        move()\n    elif not wall_ahead():\n        move()\n    else:\n        turn_left()\n    if on_crystal():\n        take()',
  16: 'turn_left()\nturn_left()\nwhile get_x() > goal_x():\n    move()\nturn_right()\nwhile get_y() > goal_y():\n    move()',
  17: 'while not at_goal():\n    if laser_ahead():\n        jump()\n    else:\n        move()\n    if on_crystal():\n        take()',
  18: 'while not wall_ahead():\n    move()\nturn_right()\nwhile not wall_ahead():\n    move()',
  19: 'while not wall_ahead():\n    move()\nif wall_left():\n    turn_right()\nelse:\n    turn_left()\nwhile not wall_ahead():\n    move()',
  20: 'def row():\n    take()\n    while not wall_ahead():\n        move()\n        take()\n\nrow()\nwhile not at_goal():\n    turn_right()\n    move()\n    turn_right()\n    row()\n    turn_left()\n    move()\n    turn_left()\n    row()',
  21: 'total = 0\nfor x in scan():\n    total += x\nreport(total)',
  22: 't = scan()\nbest = t[0]\nfor x in t:\n    if x > best:\n        best = x\nreport(best)',
  23: 'count = 0\nfor x in scan():\n    if x % 2 == 0:\n        count += 1\nreport(count)',
  24: 'def bigger(a, b):\n    if a > b:\n        return a\n    return b',
  25: 'def digit_sum(n):\n    s = 0\n    while n > 0:\n        s += n % 10\n        n //= 10\n    return s',
  26: 'def is_prime(n):\n    if n < 2:\n        return False\n    d = 2\n    while d * d <= n:\n        if n % d == 0:\n            return False\n        d += 1\n    return True',
  27: 'while not at_goal():\n    if not wall_right():\n        turn_right()\n        move()\n    elif not wall_ahead():\n        move()\n    else:\n        turn_left()\n    if on_crystal():\n        take()',
  28: 'seen = []\nfor x in scan():\n    if x in seen:\n        report(x)\n    seen.append(x)',
  29: 'lo = 1\nhi = 100\nwhile True:\n    mid = (lo + hi) // 2\n    a = ask(mid)\n    if a == 0:\n        report(mid)\n        break\n    if a < 0:\n        hi = mid - 1\n    else:\n        lo = mid + 1',
  30: 'data = scan()\nbest = 1\ncur = 1\nfor i in range(1, len(data)):\n    if data[i] > data[i - 1]:\n        cur += 1\n    else:\n        cur = 1\n    if cur > best:\n        best = cur\nreport(best)',
  9: 'def step():\n    move()\n    turn_right()\n    move()\n    take()\n    turn_left()\n\nfor i in range(5):\n    step()\nmove()',
};

let bad = 0;
const check = (name, cond, extra = '') => {
  console.log(`${cond ? 'OK  ' : 'FAIL'} ${name}${extra ? ' — ' + extra : ''}`);
  if (!cond) bad++;
};
const play = (level, code) => {
  const sim = new Sim(level);
  const res = runProgram(py, sim, code);
  return { sim, res, ev: sim.evaluate() };
};

const solves = (lv, code) => judge(py, lv, code).ok;

for (const lv of LEVELS) {
  const j = judge(py, lv, SOLUTIONS[lv.id]);
  check(`уровень ${lv.id} «${lv.title}» (${modeOf(lv)})`, j.ok, j.message);
  const lines = SOLUTIONS[lv.id].split('\n').filter((l) => l.trim()).length;
  check(`  решение укладывается в par (${lines} ≤ ${lv.par})`, lines <= lv.par);
  const last = lv.hints[lv.hints.length - 1];
  check('  последняя подсказка решает уровень', solves(lv, last));
  if (lv.guide) {
    check(`  разбор: последний шаг решает уровень (шагов: ${lv.guide.length})`, solves(lv, lv.guide[lv.guide.length - 1].code));
  }
  if (lv.starter) check('  исходный код (starter) НЕ решает уровень', !solves(lv, lv.starter));
  if (lv.tests?.length) check(`  скрытых проверок: ${lv.tests.length} (≥ 3)`, lv.tests.length >= 3);
}

// «В лоб» должно проходить видимый случай, но не скрытые проверки.
const bad_solutions = {
  19: 'move(6)\nturn_right()\nmove(4)',
  20: 'for r in range(1):\n    move(4)\n    take()\n    turn_right()\n    move()\n    turn_right()\n    move(4)\n    turn_left()\n    move()\n    turn_left()\n    move(4)',
  21: 'report(25 - 2)',
  22: 'best = 0\nfor x in scan():\n    if x > best:\n        best = x\nreport(best)',
  23: 'report(3)',
  24: 'def bigger(a, b):\n    return a + b - min(a, b) + 0 if a != b else 0',
  25: 'def digit_sum(n):\n    return n % 10 + n // 10 % 10 + n // 100',
  26: 'def is_prime(n):\n    if n < 2:\n        return False\n    for d in range(2, n):\n        if n % d == 0:\n            return False\n    return True',
  27: 'move(10)\nturn_right()\nmove(2)',
  28: 'report(9)',
  29: 'for n in range(1, 101):\n    if ask(n) == 0:\n        report(n)\n        break',
  30: 'report(4)',
};
for (const [id, code] of Object.entries(bad_solutions)) {
  const lv = LEVELS.find((l) => l.id === +id);
  const j = judge(py, lv, code);
  const sample = j.runs[0];
  check(`уровень ${id}: решение «в лоб» отвергается`, !j.ok, j.message.slice(0, 90));
  void sample;
}
// особые: 19, 20, 21, 22, 23, 28, 30 «в лоб» проходят видимый случай, а 24 и 26 — не обязательно
for (const id of [19, 21, 22, 23, 28, 30]) {
  const lv = LEVELS.find((l) => l.id === id);
  const j = judge(py, lv, bad_solutions[id]);
  check(`уровень ${id}: видимый случай «в лоб» проходит (ловит именно скрытый тест)`, j.runs.length === 2 || j.hidden === 0 ? j.runs.length === 2 : false, `runs=${j.runs.length}`);
}

const L = LEVELS;
// энергия
let r = play(L[11], 'move(11)');
check('энергия: без подзарядки не дойти', !r.res.ok && /энерги/.test(r.res.msg), explainError(r.res));
r = play(L[16], 'while not at_goal():\n    if laser_ahead():\n        jump()\n    else:\n        move()');
check('энергия: без кристаллов в уровне 17 не дойти', !r.res.ok && /энерги/.test(r.res.msg), explainError(r.res));
r = play(L[9], 'move(1)\nprint(distance(), get_x(), get_y(), goal_x(), goal_y(), energy())');
check('датчики distance/координаты работают', r.res.ok);
// у лабиринтов нет второго «лёгкого» решения левой рукой, но обычная ходьба по стене должна проходить

r = play(L[0], 'move(2)');
check('недоход до цели не засчитывается', r.res.ok && !r.ev.won);
r = play(L[0], 'move(9)');
check('стена: ошибка дрона', !r.res.ok && r.res.type === 'DroneError', explainError(r.res));
r = play(L[7], 'move(5)');
check('лазер: ошибка дрона', !r.res.ok && /лазер/.test(r.res.msg), explainError(r.res));
r = play(L[3], 'move(3)\nturn_right()\nmove(3)\nturn_right()\nmove(3)\nturn_right()\nmove(2)');
check('без кристаллов не засчитывается', r.res.ok && !r.ev.won, r.ev.reason);
r = play(L[0], 'while True:\n    pass');
check('бесконечный цикл без команд прерывается', !r.res.ok && r.res.type === 'TooManySteps', explainError(r.res));
r = play(L[0], 'while True:\n    turn_left()');
check('бесконечный цикл с командами прерывается', !r.res.ok, explainError(r.res));
r = play(L[0], 'for i in range(3)\n    move()');
check('нет двоеточия', !r.res.ok && /двоеточие/.test(explainError(r.res)), explainError(r.res));
r = play(L[4], 'while not wall_ahead():\n    move(1)\nwall_ahead():\n    turn_right()\n    move(3)');
check('команда с двоеточием без while: понятное объяснение', !r.res.ok && /while/.test(explainError(r.res, ['while not wall_ahead():', '    move(1)', 'wall_ahead():'])) && /строка 3: wall_ahead\(\):/.test(explainError(r.res, ['while not wall_ahead():', '    move(1)', 'wall_ahead():'])), explainError(r.res, ['', '', 'wall_ahead():']));
r = play(L[0], 'mov(3)');
check('опечатка в имени', r.res.type === 'NameError' && /mov/.test(explainError(r.res)), explainError(r.res));
r = play(L[0], 'for i in range(3):\nmove()');
check('нет отступа', !r.res.ok && /отступ/.test(explainError(r.res)), explainError(r.res));
r = play(L[0], 'move("a")');
check('не то в аргументе', !r.res.ok, explainError(r.res));
r = play(L[0], 'import sys\nsys.exit()');
check('sys.exit() не ломает игру', !r.res.ok);
r = play(L[0], 'move(5)');
check('после ошибок игра работает дальше', r.ev.won);

console.log(bad ? `\nПровалено: ${bad}` : '\nВсе проверки пройдены');
process.exit(bad ? 1 : 0);
