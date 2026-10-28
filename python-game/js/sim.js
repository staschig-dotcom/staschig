// Логика мира без графики: сетка, дрон, датчики. Работает и в браузере, и в Node (для тестов).

const DX = [0, 1, 0, -1]; // 0 = север, 1 = восток, 2 = юг, 3 = запад
const DY = [-1, 0, 1, 0];
const MAX_ACTIONS = 1500;
const MOVE_COST = 1;
const JUMP_COST = 2;

export function parseLevel(map) {
  const h = map.length;
  const w = Math.max(...map.map((r) => r.length));
  const cells = [];
  const crystals = [];
  let goal = null;
  let start = null;
  for (let y = 0; y < h; y++) {
    const row = [];
    for (let x = 0; x < w; x++) {
      const ch = map[y][x] ?? ' ';
      const dir = '^>v<'.indexOf(ch);
      if (dir >= 0) {
        start = { x, y, dir };
        row.push('.');
      } else if (ch === '*') {
        crystals.push({ x, y });
        row.push('.');
      } else if (ch === 'G') {
        goal = { x, y };
        row.push('G');
      } else {
        row.push(ch);
      }
    }
    cells.push(row);
  }
  return { w, h, cells, crystals, goal, start };
}

export class Sim {
  constructor(level) {
    this.map = parseLevel(level.map);
    const s = this.map.start;
    this.x = s.x;
    this.y = s.y;
    this.dir = s.dir;
    this.left = new Set(this.map.crystals.map((c) => `${c.x},${c.y}`));
    this.total = this.map.crystals.length;
    this.actions = [];
    this.failed = false;
    // Энергия: если у уровня задан energy, каждый шаг стоит энергии, а кристаллы её восполняют.
    this.maxEnergy = level.energy ?? null;
    this.energy = this.maxEnergy ?? Infinity;
    this.crystalEnergy = level.crystalEnergy ?? 0;
  }

  log(action) {
    action.energy = this.maxEnergy === null ? null : this.energy;
    this.actions.push(action);
  }

  cell(x, y) {
    if (x < 0 || y < 0 || x >= this.map.w || y >= this.map.h) return ' ';
    return this.map.cells[y][x];
  }

  walkable(x, y) {
    const c = this.cell(x, y);
    return c === '.' || c === 'L' || c === 'G';
  }

  ahead(n = 1) {
    return [this.x + DX[this.dir] * n, this.y + DY[this.dir] * n];
  }

  // Клетка сбоку от дрона: turn = 1 — справа, 3 — слева.
  side(turn) {
    const d = (this.dir + turn) % 4;
    return [this.x + DX[d], this.y + DY[d]];
  }

  fail(action, err) {
    this.log(action);
    this.failed = true;
    return { err };
  }

  call(name, _arg, line) {
    if (this.failed) return { err: 'Дрон уже не работает' };
    if (this.actions.length >= MAX_ACTIONS) {
      this.failed = true;
      return { err: 'Слишком много действий — программа зациклилась?' };
    }
    switch (name) {
      case 'move': return this.move(line);
      case 'jump': return this.jump(line);
      case 'turn_left':
      case 'turn_right': {
        const d = name === 'turn_left' ? -1 : 1;
        this.dir = (this.dir + d + 4) % 4;
        this.log({ t: 'turn', d, line });
        return { v: null };
      }
      case 'take': {
        const key = `${this.x},${this.y}`;
        if (this.left.delete(key)) {
          if (this.maxEnergy !== null) this.energy = Math.min(this.maxEnergy, this.energy + this.crystalEnergy);
          this.log({ t: 'take', x: this.x, y: this.y, line });
        } else {
          this.log({ t: 'miss', line });
        }
        return { v: null };
      }
      case 'wall_ahead': return { v: !this.walkable(...this.ahead()) };
      case 'wall_left': return { v: !this.walkable(...this.side(3)) };
      case 'wall_right': return { v: !this.walkable(...this.side(1)) };
      case 'distance': {
        let n = 0;
        while (this.walkable(...this.ahead(n + 1))) n++;
        return { v: n };
      }
      case 'get_x': return { v: this.x };
      case 'get_y': return { v: this.y };
      case 'goal_x': return { v: this.map.goal.x };
      case 'goal_y': return { v: this.map.goal.y };
      case 'energy': return { v: this.maxEnergy === null ? 999 : this.energy };
      case 'laser_ahead': return { v: this.cell(...this.ahead()) === 'L' };
      case 'at_goal': return { v: this.cell(this.x, this.y) === 'G' };
      case 'on_crystal': return { v: this.left.has(`${this.x},${this.y}`) };
      case 'crystals': return { v: this.total - this.left.size };
      default: return { err: `Неизвестная команда ${name}` };
    }
  }

  move(line) {
    const [nx, ny] = this.ahead();
    const from = { x: this.x, y: this.y };
    if (!this.walkable(nx, ny)) {
      return this.fail({ t: 'bump', line }, 'Дрон врезался в стену!');
    }
    if (this.energy < MOVE_COST) return this.noEnergy(line);
    this.energy -= MOVE_COST;
    this.x = nx;
    this.y = ny;
    this.log({ t: 'move', from, to: { x: nx, y: ny }, line });
    if (this.cell(nx, ny) === 'L') {
      return this.fail({ t: 'zap', line }, 'Дрона поразил лазер!');
    }
    return { v: null };
  }

  jump(line) {
    const [mx, my] = this.ahead(1);
    const [nx, ny] = this.ahead(2);
    const from = { x: this.x, y: this.y };
    if (!this.walkable(mx, my) || !this.walkable(nx, ny)) {
      return this.fail({ t: 'bump', line }, 'Прыжок не получился: впереди нет места для приземления!');
    }
    if (this.energy < JUMP_COST) return this.noEnergy(line);
    this.energy -= JUMP_COST;
    this.x = nx;
    this.y = ny;
    this.log({ t: 'jump', from, to: { x: nx, y: ny }, line });
    if (this.cell(nx, ny) === 'L') {
      return this.fail({ t: 'zap', line }, 'Дрон приземлился на лазер!');
    }
    return { v: null };
  }

  noEnergy(line) {
    return this.fail({ t: 'bump', line }, 'Не хватило энергии! Подбирай кристаллы по дороге: они заряжают дрона.');
  }

  // Итог после выполнения программы (если Python не выбросил ошибку).
  evaluate() {
    if (this.failed) return { won: false, reason: 'Дрон вышел из строя.' };
    if (this.cell(this.x, this.y) !== 'G') {
      return { won: false, reason: 'Программа закончилась, а дрон не на маяке.' };
    }
    if (this.left.size > 0) {
      return { won: false, reason: `Дрон на маяке, но остались кристаллы: ${this.left.size}.` };
    }
    return { won: true, reason: '' };
  }
}

// ---------- задачи с данными (списки, числа, интерактив) ----------
// Дрон не двигается: программа получает данные командой scan(), может задавать вопросы ask(n)
// и отправляет ответ report(x). Скрытые проверки — те же задачи на других данных.

const NO_DRONE = 'В этой задаче дрон не движется — работай с данными: scan(), ask(), report().';
const MAX_DATA_ACTIONS = 300;

const sameJson = (a, b) => JSON.stringify(a) === JSON.stringify(b);
export const show = (v) => (v === null || v === undefined ? 'ничего' : JSON.stringify(v));

export class DataSim {
  constructor(c, level) {
    this.c = c;
    this.maxAsks = level.asks ?? null;
    this.asks = 0;
    this.actions = [];
    this.failed = false;
    this.answered = false;
    this.answer = null;
    this.expected = level.answer(c);
  }

  call(name, arg, line) {
    if (this.failed) return { err: 'Программа уже остановлена' };
    if (this.actions.length >= MAX_DATA_ACTIONS) {
      this.failed = true;
      return { err: 'Слишком много действий — программа зациклилась?' };
    }
    switch (name) {
      case 'scan':
        if (this.c.scan === undefined) return { err: 'В этой задаче нечего сканировать.' };
        this.actions.push({ t: 'scan', line });
        return { v: this.c.scan };
      case 'ask': {
        if (this.c.secret === undefined) return { err: 'В этой задаче вопросов задавать не нужно.' };
        if (!Number.isInteger(arg)) return { err: 'ask() принимает целое число: ask(50).' };
        if (this.asks >= this.maxAsks) {
          this.failed = true;
          this.actions.push({ t: 'bump', line });
          return { err: `Слишком много вопросов! Разрешено не больше ${this.maxAsks}. Перебором не выйдет — делай пополам.` };
        }
        this.asks++;
        const a = Math.sign(this.c.secret - arg); // -1: загаданное меньше, 1: больше
        this.actions.push({ t: 'ask', n: arg, a, line });
        return { v: a };
      }
      case 'report':
        this.answered = true;
        this.answer = arg ?? null;
        this.actions.push({ t: 'say', text: `Ответ: ${show(arg)}`, line });
        return { v: null };
      default:
        return { err: NO_DRONE };
    }
  }

  evaluate() {
    if (this.failed) return { won: false, reason: 'Программа остановилась.' };
    if (!this.answered) return { won: false, reason: 'Ответ не отправлен: в конце нужно вызвать report(...).' };
    if (!sameJson(this.answer, this.expected)) {
      return { won: false, reason: `Твой ответ: ${show(this.answer)}, а правильный: ${show(this.expected)}.` };
    }
    return { won: true, reason: '' };
  }
}

// Заглушка для задач-функций: дрон там не нужен.
export const NO_SIM = { call: () => ({ err: 'В этой задаче дрон не нужен — пиши только функцию.' }) };
