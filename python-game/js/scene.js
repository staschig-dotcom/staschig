// 3D-сцена на three.js: плитки, стены, кристаллы, лазеры, маяк и дрон.
import * as THREE from '../vendor/three.module.min.js';
import { parseLevel } from './sim.js';

const ease = (p) => (p < 0.5 ? 2 * p * p : 1 - Math.pow(-2 * p + 2, 2) / 2);

const tileGeo = new THREE.BoxGeometry(0.94, 0.2, 0.94);
const wallGeo = new THREE.BoxGeometry(0.94, 0.4, 0.94);
const tileEdges = new THREE.EdgesGeometry(tileGeo);
const wallEdges = new THREE.EdgesGeometry(wallGeo);
const crystalGeo = new THREE.OctahedronGeometry(0.2, 0);
const sparkGeo = new THREE.SphereGeometry(0.04, 6, 6);

const mat = {
  floorA: new THREE.MeshStandardMaterial({ color: 0x1b2650, roughness: 0.8, flatShading: true }),
  floorB: new THREE.MeshStandardMaterial({ color: 0x232f66, roughness: 0.8, flatShading: true }),
  wall: new THREE.MeshStandardMaterial({ color: 0x4b2f9e, roughness: 0.6, flatShading: true }),
  laserTile: new THREE.MeshStandardMaterial({ color: 0x3a1020, emissive: 0x5a0a1a, roughness: 0.8 }),
  goalTile: new THREE.MeshStandardMaterial({ color: 0x0f4a3a, emissive: 0x0b6a4a, roughness: 0.7 }),
  edge: new THREE.LineBasicMaterial({ color: 0x38e1ff, transparent: true, opacity: 0.35 }),
  wallEdge: new THREE.LineBasicMaterial({ color: 0xff4fd8, transparent: true, opacity: 0.8 }),
  crystal: new THREE.MeshStandardMaterial({
    color: 0xff4fd8, emissive: 0xaa1f90, roughness: 0.3, flatShading: true,
  }),
  laser: new THREE.MeshBasicMaterial({ color: 0xff3355 }),
  post: new THREE.MeshStandardMaterial({ color: 0x555a7a, roughness: 0.5 }),
  ring: new THREE.MeshBasicMaterial({ color: 0x4dff9a }),
  beam: new THREE.MeshBasicMaterial({
    color: 0x4dff9a, transparent: true, opacity: 0.22, depthWrite: false, blending: THREE.AdditiveBlending,
  }),
  spark: new THREE.MeshBasicMaterial({ color: 0xffe08a }),
};

// Подпись над столбиком или дроном: рисуем текст на canvas и ставим как спрайт.
function makeLabel(text, { color = '#ffffff', size = 0.9 } = {}) {
  const cv = document.createElement('canvas');
  cv.width = 256;
  cv.height = 96;
  const g = cv.getContext('2d');
  let fs = 64;
  g.font = `bold ${fs}px system-ui, sans-serif`;
  while (g.measureText(text).width > 236 && fs > 20) {
    fs -= 4;
    g.font = `bold ${fs}px system-ui, sans-serif`;
  }
  g.textAlign = 'center';
  g.textBaseline = 'middle';
  g.lineWidth = 8;
  g.strokeStyle = '#070a18';
  g.strokeText(text, 128, 50);
  g.fillStyle = color;
  g.fillText(text, 128, 50);
  const tex = new THREE.CanvasTexture(cv);
  const sp = new THREE.Sprite(new THREE.SpriteMaterial({ map: tex, transparent: true, depthTest: false }));
  sp.scale.set(size, size * 0.375, 1);
  sp.renderOrder = 10;
  return sp;
}

const ROW_COLOR = { pos: 0x38e1ff, neg: 0x7a8cff, hidden: 0x555a7a, ok: 0x4dff9a, bad: 0xff3355 };
const pillarGeo = new THREE.BoxGeometry(0.6, 1, 0.6);

function makeDrone() {
  const g = new THREE.Group();
  const bodyMat = new THREE.MeshStandardMaterial({
    color: 0xdfe8ff, roughness: 0.4, metalness: 0.2, flatShading: true, emissive: 0x000000,
  });
  const body = new THREE.Mesh(new THREE.IcosahedronGeometry(0.27, 1), bodyMat);
  g.add(body);
  const visor = new THREE.Mesh(
    new THREE.BoxGeometry(0.32, 0.11, 0.16),
    new THREE.MeshStandardMaterial({ color: 0x0a1030, roughness: 0.2, metalness: 0.6 }),
  );
  visor.position.set(0, 0.04, -0.2);
  g.add(visor);
  const eyeMat = new THREE.MeshBasicMaterial({ color: 0x38e1ff });
  for (const sx of [-0.07, 0.07]) {
    const eye = new THREE.Mesh(new THREE.SphereGeometry(0.03, 8, 8), eyeMat);
    eye.position.set(sx, 0.04, -0.285);
    g.add(eye);
  }
  const finMat = new THREE.MeshStandardMaterial({ color: 0x7a4dff, roughness: 0.5, flatShading: true });
  for (const sx of [-1, 1]) {
    const fin = new THREE.Mesh(new THREE.BoxGeometry(0.22, 0.04, 0.16), finMat);
    fin.position.set(sx * 0.3, 0, 0.04);
    fin.rotation.z = sx * 0.25;
    g.add(fin);
  }
  const glow = new THREE.Mesh(
    new THREE.ConeGeometry(0.1, 0.22, 8),
    new THREE.MeshBasicMaterial({ color: 0x38e1ff, transparent: true, opacity: 0.8 }),
  );
  glow.position.y = -0.3;
  glow.rotation.x = Math.PI;
  g.add(glow);
  g.userData = { bodyMat, body };
  return g;
}

export class World {
  constructor(canvas) {
    this.canvas = canvas;
    this.renderer = new THREE.WebGLRenderer({ canvas, antialias: true, powerPreference: 'high-performance' });
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
    this.scene = new THREE.Scene();
    this.scene.background = new THREE.Color(0x070a18);
    this.camera = new THREE.PerspectiveCamera(40, 1, 0.1, 200);

    this.scene.add(new THREE.HemisphereLight(0xbfd4ff, 0x20204a, 0.9));
    const sun = new THREE.DirectionalLight(0xffffff, 1.1);
    sun.position.set(4, 8, 5);
    this.scene.add(sun);

    this.stars = this.makeStars();
    this.scene.add(this.stars);

    this.group = new THREE.Group();
    this.scene.add(this.group);
    this.drone = makeDrone();
    this.scene.add(this.drone);

    this.azimuth = 0;
    this.pitch = 0.95;
    this.dist = 10;
    this.speed = 1;
    this.token = 0;
    this.crystals = new Map();
    this.lasers = [];
    this.goalFx = [];
    this.heading = 0;
    this.pos = new THREE.Vector3();
    this.lift = 0;
    this.map = null;
    this.row = [];
    this.bubble = null;

    this.bindDrag();
    new ResizeObserver(() => this.resize()).observe(canvas.parentElement);
    this.loop = this.loop.bind(this);
    requestAnimationFrame(this.loop);
  }

  makeStars() {
    const n = 500;
    const arr = new Float32Array(n * 3);
    for (let i = 0; i < n; i++) {
      const r = 40 + Math.random() * 30;
      const a = Math.random() * Math.PI * 2;
      const b = Math.acos(2 * Math.random() - 1);
      arr[i * 3] = r * Math.sin(b) * Math.cos(a);
      arr[i * 3 + 1] = r * Math.cos(b);
      arr[i * 3 + 2] = r * Math.sin(b) * Math.sin(a);
    }
    const geo = new THREE.BufferGeometry();
    geo.setAttribute('position', new THREE.BufferAttribute(arr, 3));
    return new THREE.Points(geo, new THREE.PointsMaterial({ color: 0xaab8ff, size: 0.25, sizeAttenuation: true }));
  }

  bindDrag() {
    let last = null;
    this.canvas.addEventListener('pointerdown', (e) => {
      last = { x: e.clientX, y: e.clientY };
      this.canvas.setPointerCapture(e.pointerId);
    });
    this.canvas.addEventListener('pointermove', (e) => {
      if (!last) return;
      this.azimuth = Math.max(-1.2, Math.min(1.2, this.azimuth - (e.clientX - last.x) * 0.008));
      this.pitch = Math.max(0.5, Math.min(1.35, this.pitch + (e.clientY - last.y) * 0.006));
      last = { x: e.clientX, y: e.clientY };
      this.placeCamera();
    });
    const end = () => { last = null; };
    this.canvas.addEventListener('pointerup', end);
    this.canvas.addEventListener('pointercancel', end);
  }

  resize() {
    const p = this.canvas.parentElement;
    const w = p.clientWidth;
    const h = p.clientHeight;
    if (!w || !h) return;
    this.renderer.setSize(w, h, false);
    this.camera.aspect = w / h;
    this.camera.updateProjectionMatrix();
    this.fit();
  }

  fit() {
    if (!this.map) return;
    const radius = Math.hypot(this.map.w, this.map.h) / 2 + 0.7;
    const vHalf = (this.camera.fov * Math.PI) / 360;
    const hHalf = Math.atan(Math.tan(vHalf) * this.camera.aspect);
    this.dist = (radius / Math.sin(Math.min(vHalf, hHalf))) * (this.row.length ? 0.74 : 0.82);
    this.placeCamera();
  }

  placeCamera() {
    const { azimuth: az, pitch: pt, dist: d } = this;
    this.camera.position.set(Math.sin(az) * Math.cos(pt) * d, Math.sin(pt) * d, Math.cos(az) * Math.cos(pt) * d);
    this.camera.lookAt(0, 0, 0);
  }

  tile(x, y) {
    return new THREE.Vector3(x - this.cx, 0, y - this.cz);
  }

  load(levelMap) {
    this.stop();
    this.group.clear();
    this.crystals.clear();
    this.lasers = [];
    this.goalFx = [];
    this.row = [];
    this.map = parseLevel(levelMap);
    const { w, h, cells } = this.map;
    this.cx = (w - 1) / 2;
    this.cz = (h - 1) / 2;
    // С лабиринтами камера смотрит почти сверху, чтобы стены не закрывали клетки за собой.
    this.pitch = cells.some((r) => r.includes('#')) ? 1.25 : 0.95;

    for (let y = 0; y < h; y++) {
      for (let x = 0; x < w; x++) {
        const c = cells[y][x];
        if (c === ' ') continue;
        const p = this.tile(x, y);
        if (c === '#') {
          const wall = new THREE.Mesh(wallGeo, mat.wall);
          wall.position.set(p.x, 0.2, p.z);
          wall.add(new THREE.LineSegments(wallEdges, mat.wallEdge));
          this.group.add(wall);
          continue;
        }
        const m = c === 'L' ? mat.laserTile : c === 'G' ? mat.goalTile : (x + y) % 2 ? mat.floorA : mat.floorB;
        const t = new THREE.Mesh(tileGeo, m);
        t.position.set(p.x, -0.1, p.z);
        t.add(new THREE.LineSegments(tileEdges, mat.edge));
        this.group.add(t);
        if (c === 'L') this.addLaser(p);
        if (c === 'G') this.addGoal(p);
      }
    }
    for (const { x, y } of this.map.crystals) {
      const cm = new THREE.Mesh(crystalGeo, mat.crystal);
      const p = this.tile(x, y);
      cm.position.set(p.x, 0.45, p.z);
      cm.scale.set(1, 1.6, 1);
      cm.userData.phase = Math.random() * 6;
      this.group.add(cm);
      this.crystals.set(`${x},${y}`, cm);
    }
    this.resetDrone();
    this.resize();
    this.fit();
  }

  show(view) {
    if (view.row) this.loadRow(view.row);
    else this.load(view.map);
  }

  // Набор данных / тестов: ряд столбиков с подписями, слева стартовая площадка дрона.
  loadRow(items) {
    this.stop();
    this.group.clear();
    this.crystals.clear();
    this.lasers = [];
    this.goalFx = [];
    this.row = [];
    const n = items.length;
    this.map = { w: n + 2, h: 1.6, cells: [[]], crystals: [], start: { x: 0, y: 0, dir: 1 }, goal: null };
    this.cx = (n + 1) / 2;
    this.cz = 0;
    this.pitch = 0.95;
    for (let x = 0; x <= n; x++) {
      const t = new THREE.Mesh(tileGeo, x % 2 ? mat.floorA : mat.floorB);
      const p = this.tile(x, 0);
      t.position.set(p.x, -0.1, p.z);
      t.add(new THREE.LineSegments(tileEdges, mat.edge));
      this.group.add(t);
    }
    items.forEach((it, i) => {
      const p = this.tile(i + 1, 0);
      const h = 0.15 + (it.h ?? 0.2) * 1.5;
      const color = it.hidden ? ROW_COLOR.hidden : it.neg ? ROW_COLOR.neg : ROW_COLOR.pos;
      const m = new THREE.MeshStandardMaterial({ color, emissive: color, emissiveIntensity: 0.25, roughness: 0.5, flatShading: true });
      const pillar = new THREE.Mesh(pillarGeo, m);
      pillar.scale.y = h;
      pillar.position.set(p.x, h / 2, p.z);
      this.group.add(pillar);
      const entry = { pillar, m, h, base: color, label: null, text: it.label, hidden: !!it.hidden };
      this.row.push(entry);
      this.setRowLabel(entry, it.label);
    });
    this.resetDrone();
    this.resize();
    this.fit();
  }

  setRowLabel(entry, text) {
    if (entry.label) this.group.remove(entry.label);
    entry.label = makeLabel(text, { size: 1.05 });
    entry.label.userData.text = text;
    entry.label.position.set(entry.pillar.position.x, entry.h + 0.45, 0);
    this.group.add(entry.label);
  }

  rowTile(i) {
    return this.tile(i + 1, 0);
  }

  say(text) {
    if (this.bubble) this.scene.remove(this.bubble);
    this.bubble = text ? makeLabel(text, { color: '#ffe08a', size: 2 }) : null;
    if (this.bubble) this.scene.add(this.bubble);
  }

  addLaser(p) {
    const beam = new THREE.Mesh(new THREE.BoxGeometry(0.9, 0.05, 0.05), mat.laser);
    beam.position.set(p.x, 0.32, p.z);
    this.group.add(beam);
    this.lasers.push(beam);
    for (const sx of [-0.45, 0.45]) {
      const post = new THREE.Mesh(new THREE.CylinderGeometry(0.05, 0.05, 0.45, 8), mat.post);
      post.position.set(p.x + sx, 0.22, p.z);
      this.group.add(post);
    }
  }

  addGoal(p) {
    const ring = new THREE.Mesh(new THREE.TorusGeometry(0.32, 0.04, 8, 28), mat.ring);
    ring.rotation.x = Math.PI / 2;
    ring.position.set(p.x, 0.06, p.z);
    const beam = new THREE.Mesh(new THREE.CylinderGeometry(0.26, 0.26, 2.2, 20, 1, true), mat.beam);
    beam.position.set(p.x, 1.1, p.z);
    this.group.add(ring, beam);
    this.goalFx.push(ring, beam);
  }

  resetDrone() {
    const s = this.map.start;
    this.pos.copy(this.tile(s.x, s.y));
    this.heading = -s.dir * (Math.PI / 2);
    this.lift = 0;
    this.drone.visible = true;
    this.drone.scale.setScalar(1);
    this.drone.rotation.set(0, this.heading, 0);
    this.drone.userData.bodyMat.emissive.setHex(0x000000);
    this.say('');
    for (const e of this.row) {
      e.m.color.setHex(e.base);
      e.m.emissive.setHex(e.base);
      if (e.label.userData.text !== e.text) this.setRowLabel(e, e.text);
    }
    for (const c of this.crystals.values()) {
      c.visible = true;
      c.scale.set(1, 1.6, 1);
    }
  }

  stop() {
    this.token++;
  }

  reset() {
    this.stop();
    this.resetDrone();
  }

  tween(dur, fn) {
    const tok = this.token;
    return new Promise((resolve) => {
      const t0 = performance.now();
      const step = (now) => {
        if (tok !== this.token) return resolve(false);
        const p = Math.min(1, (now - t0) / (dur / this.speed));
        fn(p);
        if (p < 1) requestAnimationFrame(step);
        else resolve(true);
      };
      requestAnimationFrame(step);
    });
  }

  flash(hex) {
    this.drone.userData.bodyMat.emissive.setHex(hex);
  }

  async perform(a) {
    const d = this.drone;
    switch (a.t) {
      case 'move': {
        const p0 = this.tile(a.from.x, a.from.y);
        const p1 = this.tile(a.to.x, a.to.y);
        return this.tween(450, (p) => this.pos.lerpVectors(p0, p1, ease(p)));
      }
      case 'jump': {
        const p0 = this.tile(a.from.x, a.from.y);
        const p1 = this.tile(a.to.x, a.to.y);
        return this.tween(650, (p) => {
          this.pos.lerpVectors(p0, p1, ease(p));
          this.lift = Math.sin(p * Math.PI) * 0.75;
        }).then((ok) => { this.lift = 0; return ok; });
      }
      case 'turn': {
        const h0 = this.heading;
        const h1 = h0 - a.d * (Math.PI / 2);
        const ok = await this.tween(300, (p) => { this.heading = h0 + (h1 - h0) * ease(p); });
        this.heading = h1;
        return ok;
      }
      case 'take': {
        const c = this.crystals.get(`${a.x},${a.y}`);
        this.spark(this.pos);
        return this.tween(350, (p) => {
          this.lift = Math.sin(p * Math.PI) * 0.18;
          if (c) { c.scale.set(1 - p, (1 - p) * 1.6, 1 - p); c.position.y = 0.45 + p * 0.6; }
        }).then((ok) => { if (c) { c.visible = false; c.position.y = 0.45; } this.lift = 0; return ok; });
      }
      case 'scan': {
        // дрон пролетает над всеми столбиками, подсвечивая каждый
        const first = this.rowTile(0);
        const s0 = this.pos.clone();
        if (!(await this.tween(250, (p) => this.pos.lerpVectors(s0, first, ease(p))))) return false;
        for (let i = 0; i < this.row.length; i++) {
          const p0 = this.pos.clone();
          const p1 = this.rowTile(i);
          const e = this.row[i];
          e.m.emissiveIntensity = 1;
          if (!(await this.tween(i ? 220 : 80, (p) => this.pos.lerpVectors(p0, p1, ease(p))))) return false;
          e.m.emissiveIntensity = 0.25;
        }
        return true;
      }
      case 'say':
        this.say(a.text);
        this.spark(this.pos);
        return this.tween(500, (p) => { this.lift = Math.sin(p * Math.PI) * 0.2; }).then((ok) => { this.lift = 0; return ok; });
      case 'ask':
        this.say(`${a.n}? ${a.a < 0 ? 'меньше ▼' : a.a > 0 ? 'больше ▲' : 'верно!'}`);
        if (a.a === 0) this.spark(this.pos);
        return this.tween(450, (p) => { this.lift = Math.sin(p * Math.PI) * 0.15; }).then((ok) => { this.lift = 0; return ok; });
      case 'case': {
        const e = this.row[a.i];
        if (!e) return true;
        const p0 = this.pos.clone();
        const p1 = this.rowTile(a.i);
        if (!(await this.tween(260, (p) => this.pos.lerpVectors(p0, p1, ease(p))))) return false;
        if (e.hidden) this.setRowLabel(e, a.label);
        const col = a.ok ? ROW_COLOR.ok : ROW_COLOR.bad;
        e.m.color.setHex(col);
        e.m.emissive.setHex(col);
        this.say(`${a.ok ? '✓' : '✗'} ${a.got}`);
        if (a.ok) this.spark(this.pos);
        else this.flash(0xaa1020);
        return this.tween(380, () => {});
      }
      case 'miss':
        return this.tween(300, (p) => { d.rotation.z = Math.sin(p * Math.PI * 4) * 0.25 * (1 - p); });
      case 'bump': {
        this.flash(0xaa1020);
        const dir = new THREE.Vector3(0, 0, -1).applyAxisAngle(new THREE.Vector3(0, 1, 0), this.heading);
        const base = this.pos.clone();
        return this.tween(500, (p) => {
          this.pos.copy(base).addScaledVector(dir, Math.sin(Math.min(1, p * 2) * Math.PI) * 0.3 * (p < 0.5 ? 1 : 0));
          d.rotation.z = Math.sin(p * Math.PI * 6) * 0.2 * (1 - p);
        });
      }
      case 'zap': {
        this.flash(0xff2040);
        this.spark(this.pos);
        return this.tween(700, (p) => {
          d.rotation.z = Math.sin(p * Math.PI * 10) * 0.4;
          d.scale.setScalar(1 - ease(p) * 0.5);
        });
      }
      default:
        return true;
    }
  }

  async play(actions, onAction) {
    const tok = ++this.token;
    for (const a of actions) {
      if (tok !== this.token) return false;
      onAction?.(a);
      if (!(await this.perform(a))) return false;
      await this.tween(60, () => {});
    }
    return tok === this.token;
  }

  async celebrate() {
    return this.tween(900, (p) => {
      this.lift = Math.abs(Math.sin(p * Math.PI * 2)) * 0.4;
      this.heading += 0.18;
    }).then((ok) => { this.lift = 0; return ok; });
  }

  spark(at) {
    const bits = [];
    for (let i = 0; i < 10; i++) {
      const m = new THREE.Mesh(sparkGeo, mat.spark);
      m.position.set(at.x, 0.45, at.z);
      const a = (i / 10) * Math.PI * 2;
      m.userData.v = new THREE.Vector3(Math.cos(a) * 0.8, 0.6 + Math.random() * 0.6, Math.sin(a) * 0.8);
      this.scene.add(m);
      bits.push(m);
    }
    const t0 = performance.now();
    const step = (now) => {
      const p = (now - t0) / 600;
      for (const m of bits) {
        m.position.addScaledVector(m.userData.v, 0.016);
        m.scale.setScalar(Math.max(0.01, 1 - p));
      }
      if (p < 1) requestAnimationFrame(step);
      else for (const m of bits) this.scene.remove(m);
    };
    requestAnimationFrame(step);
  }

  loop(now) {
    const t = now / 1000;
    this.stars.rotation.y = t * 0.01;
    for (const [i, c] of [...this.crystals.values()].entries()) {
      if (!c.visible) continue;
      c.rotation.y = t * 1.5 + i;
      if (c.scale.x === 1) c.position.y = 0.45 + Math.sin(t * 2 + c.userData.phase) * 0.05;
    }
    for (const l of this.lasers) l.material.color.setHex(Math.sin(t * 8) > -0.2 ? 0xff3355 : 0xaa1f33);
    for (const g of this.goalFx) g.rotation.y = t * 0.8;
    const bob = Math.sin(t * 3) * 0.03;
    this.drone.position.set(this.pos.x, 0.45 + this.lift + bob, this.pos.z);
    this.drone.rotation.y = this.heading;
    this.renderer.render(this.scene, this.camera);
    requestAnimationFrame(this.loop);
  }
}
