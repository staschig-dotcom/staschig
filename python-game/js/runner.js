// Мост между настоящим Python (Pyodide) и симулятором.
// Python-код ребёнка вызывает move(), take() и т.д. — они пишутся в журнал действий,
// а потом 3D-сцена проигрывает этот журнал.

const PRELUDE = `
import sys, json

_cur = [0]
_n = [0]
_LIMIT = 30000

class DroneError(Exception):
    pass

class TooManySteps(BaseException):
    pass

def _trace(frame, event, arg):
    if frame.f_code.co_filename == '<drone>':
        if event == 'line':
            _cur[0] = frame.f_lineno
            _n[0] += 1
            if _n[0] > _LIMIT:
                raise TooManySteps()
        return _trace
    return None

def _call(name):
    r = json.loads(_sim_call(name, 0, _cur[0]))
    if r.get('err'):
        raise DroneError(r['err'])
    return r.get('v')

def move(n=1):
    for _ in range(n):
        _call('move')

def jump():
    _call('jump')

def turn_left():
    _call('turn_left')

def turn_right():
    _call('turn_right')

def take():
    _call('take')

def wall_ahead():
    return _call('wall_ahead')

def laser_ahead():
    return _call('laser_ahead')

def at_goal():
    return _call('at_goal')

def on_crystal():
    return _call('on_crystal')

def crystals():
    return _call('crystals')

_API = ['move', 'jump', 'turn_left', 'turn_right', 'take', 'wall_ahead',
        'laser_ahead', 'at_goal', 'on_crystal', 'crystals']

def _last_user_line(tb):
    line = None
    while tb:
        if tb.tb_frame.f_code.co_filename == '<drone>':
            line = tb.tb_lineno
        tb = tb.tb_next
    return line

def _run(code):
    _cur[0] = 0
    _n[0] = 0
    ns = {k: globals()[k] for k in _API}
    ns['__name__'] = '__main__'
    out = {'ok': True}
    try:
        src = compile(code, '<drone>', 'exec')
        sys.settrace(_trace)
        try:
            exec(src, ns)
        finally:
            sys.settrace(None)
    except SyntaxError as e:
        out = {'ok': False, 'type': type(e).__name__, 'msg': str(e.msg), 'line': e.lineno}
    except TooManySteps:
        out = {'ok': False, 'type': 'TooManySteps', 'msg': '', 'line': _cur[0]}
    except DroneError as e:
        out = {'ok': False, 'type': 'DroneError', 'msg': str(e), 'line': _cur[0]}
    except BaseException as e:
        out = {'ok': False, 'type': type(e).__name__, 'msg': str(e),
               'line': _last_user_line(e.__traceback__)}
    return json.dumps(out)
`;

export function initPrelude(py) {
  py.runPython(PRELUDE);
}

export function runProgram(py, sim, code) {
  py.globals.set('_sim_call', (name, arg, line) => JSON.stringify(sim.call(name, arg, line)));
  py.globals.set('_user_code', code);
  return JSON.parse(py.runPython('_run(_user_code)'));
}

// Понятное объяснение ошибки Python для ребёнка.
export function explainError(err, lines = []) {
  const where = err.line ? ` (строка ${err.line})` : '';
  const text = err.line ? lines[err.line - 1]?.trim() : '';
  const at = err.line ? ` (строка ${err.line}${text ? ': ' + text : ''})` : '';
  const m = err.msg || '';
  switch (err.type) {
    case 'DroneError':
      return `${m}${where}`;
    case 'TooManySteps':
      return `Программа зациклилась${where}. Проверь условие в while.`;
    case 'IndentationError':
    case 'TabError':
      return `Неправильные отступы${where}. Всё внутри for/while/if сдвигается на 4 пробела.`;
    case 'SyntaxError':
      if (/expected ':'/.test(m)) return `Забыто двоеточие в конце строки${at}.`;
      if (/never closed|was never closed|unmatched/.test(m)) return `Не хватает или лишняя скобка${at}.`;
      if (text && /^[A-Za-z_]\w*\(.*\)\s*:$/.test(text)) {
        return `Двоеточие ставят после while, if, for или def, а тут просто команда${at}. Может, в начале не хватает «while not» или «if»?`;
      }
      return `Ошибка в записи кода${at}. Проверь скобки, двоеточия и отступы.`;
    case 'NameError': {
      const name = /name '(.+?)'/.exec(m)?.[1];
      return `Дрон не знает слова «${name ?? '?'}»${where}. Проверь, нет ли опечатки.`;
    }
    case 'TypeError':
      return `Команде дали не то, что она ждёт${where}. Например, move(3), а не move(три).`;
    case 'ZeroDivisionError':
      return `Деление на ноль${where}.`;
    default:
      return `Ошибка ${err.type}${where}: ${m}`;
  }
}
