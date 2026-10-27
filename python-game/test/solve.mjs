// Запуск: node python-game/test/solve.mjs
// Проверяет, что каждый уровень проходится эталонным решением (с настоящим Python),
// и что типичные ошибки ребёнка дают понятные сообщения.
import { loadPyodide } from '../vendor/pyodide/pyodide.mjs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { LEVELS } from '../js/levels.js';
import { Sim } from '../js/sim.js';
import { initPrelude, runProgram, explainError } from '../js/runner.js';

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

for (const lv of LEVELS) {
  const { res, ev, sim } = play(lv, SOLUTIONS[lv.id]);
  check(`уровень ${lv.id} «${lv.title}»`, res.ok && ev.won, res.ok ? ev.reason : JSON.stringify(res));
  const lines = SOLUTIONS[lv.id].split('\n').filter((l) => l.trim()).length;
  check(`  решение укладывается в par (${lines} ≤ ${lv.par})`, lines <= lv.par);
  // подсказка №2 из уровня тоже должна решать уровень
  const hint = play(lv, lv.hints[1]);
  check('  последняя подсказка решает уровень', hint.res.ok && hint.ev.won);
  void sim;
}

const L = LEVELS;
let r = play(L[0], 'move(2)');
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
