// Логика мира без графики: сетка, дрон, датчики. Работает и в браузере, и в Node (для тестов).

const DX = [0, 1, 0, -1]; // 0 = север, 1 = восток, 2 = юг, 3 = запад
const DY = [-1, 0, 1, 0];
const MAX_ACTIONS = 400;

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

  fail(action, err) {
    this.actions.push(action);
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
        this.actions.push({ t: 'turn', d, line });
        return { v: null };
      }
      case 'take': {
        const key = `${this.x},${this.y}`;
        if (this.left.delete(key)) {
          this.actions.push({ t: 'take', x: this.x, y: this.y, line });
        } else {
          this.actions.push({ t: 'miss', line });
        }
        return { v: null };
      }
      case 'wall_ahead': return { v: !this.walkable(...this.ahead()) };
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
    this.x = nx;
    this.y = ny;
    this.actions.push({ t: 'move', from, to: { x: nx, y: ny }, line });
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
    this.x = nx;
    this.y = ny;
    this.actions.push({ t: 'jump', from, to: { x: nx, y: ny }, line });
    if (this.cell(nx, ny) === 'L') {
      return this.fail({ t: 'zap', line }, 'Дрон приземлился на лазер!');
    }
    return { v: null };
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
