/**
 * A glowing point-cloud "brain" drawn with plain WebGL 1: neurons on two folded hemispheres, faint links
 * between near neighbours and bright signals travelling along some links. Loaded lazily by {@link NeuralBg}
 * after the first render, in its own chunk. Each kind of page frames it differently ({@link ScenePose}) and the
 * camera glides between those framings, with a swing of the brain, when the player changes page.
 */

/** The brain's colours, from the current theme. */
export interface SceneColors {
  /** Gradient the neurons are coloured along, from one side of the brain to the other. */
  from: RGB;
  via: RGB;
  to: RGB;
  /** The travelling signals. */
  signal: RGB;
  /** Links between neurons. */
  link: RGB;
  /** Light page background: draw with normal blending instead of additive glow. */
  light: boolean;
}

/** A colour as red, green and blue from 0 to 1. */
export type RGB = [number, number, number];

/** How to set up the scene. */
export interface SceneOptions {
  /** Phones get fewer neurons and a lower pixel ratio. */
  small: boolean;
  colors: SceneColors;
}

/** How a page frames the brain. The scene eases from one pose to the next. */
export interface ScenePose {
  /** Sideways and upward shift of the brain, in scene units (about one brain radius). Ignored on tall screens. */
  x: number;
  y: number;
  /** Camera distance as a multiple of the default; above 1 is further away. */
  zoom: number;
  /** Overall brightness, 0..1. */
  alpha: number;
  /** Turning speed as a multiple of the default; 0 holds still. */
  spin: number;
  /** How much the brain leans towards the pointer, 0..1. */
  lean: number;
}

/** The WebGL brain behind the pages. */
export interface NeuralScene {
  /**
   * Draws one frame at {@code time} seconds; pointer is -1..1 on both axes. Returns true once the camera has
   * reached the pose and the swing has died down, so a still pose needs no more frames.
   */
  render(time: number, pointerX: number, pointerY: number): boolean;
  /** Moves the camera towards {@code pose}; {@code swing} spins the brain a little on the way, {@code instant} jumps. */
  setPose(pose: ScenePose, swing?: number, instant?: boolean): void;
  /** Matches the drawing buffer to the canvas's CSS size. */
  resize(): void;
  setColors(colors: SceneColors): void;
  dispose(): void;
}

/** Creates the scene on a canvas; null where WebGL is not available. */
export type SceneFactory = (canvas: HTMLCanvasElement, options: SceneOptions) => NeuralScene | null;

const POINT_VS = `
attribute vec3 aA;
attribute vec3 aB;
attribute vec4 aMeta; // x: colour mix 0..1 (or -1 for a signal), y: size, z: phase, w: speed
uniform mat4 uProj;
uniform mat4 uView;
uniform float uTime;
uniform float uDpr;
uniform float uDist;
uniform vec3 uFrom;
uniform vec3 uVia;
uniform vec3 uTo;
uniform vec3 uSignal;
varying vec3 vColor;
varying float vAlpha;
void main() {
  float f = fract(uTime * aMeta.w + aMeta.z);
  bool signal = aMeta.x < 0.0;
  vec3 p = signal ? mix(aA, aB, f) : aA;
  vec4 mv = uView * vec4(p, 1.0);
  gl_Position = uProj * mv;
  float depth = smoothstep(uDist + 1.8, uDist - 1.0, -mv.z);
  float twinkle = signal ? sin(3.14159 * f) : 0.75 + 0.25 * sin(uTime * 1.3 + aMeta.z * 40.0);
  gl_PointSize = aMeta.y * uDpr * twinkle * (uDist / -mv.z);
  vColor = signal ? uSignal : (aMeta.x < 0.5 ? mix(uFrom, uVia, aMeta.x * 2.0) : mix(uVia, uTo, aMeta.x * 2.0 - 1.0));
  vAlpha = (0.25 + 0.75 * depth) * (signal ? 1.0 : twinkle);
}`;

const POINT_FS = `
precision mediump float;
uniform float uAlpha;
varying vec3 vColor;
varying float vAlpha;
void main() {
  float d = length(gl_PointCoord - 0.5);
  float a = smoothstep(0.5, 0.0, d);
  a = a * a + 0.6 * smoothstep(0.14, 0.0, d);
  a *= vAlpha * uAlpha;
  gl_FragColor = vec4(vColor * a, a);
}`;

const LINE_VS = `
attribute vec3 aPos;
uniform mat4 uProj;
uniform mat4 uView;
uniform float uDist;
varying float vDepth;
void main() {
  vec4 mv = uView * vec4(aPos, 1.0);
  gl_Position = uProj * mv;
  vDepth = smoothstep(uDist + 1.6, uDist - 0.9, -mv.z);
}`;

const LINE_FS = `
precision mediump float;
uniform vec3 uColor;
uniform float uAlpha;
varying float vDepth;
void main() {
  float a = uAlpha * (0.15 + 0.85 * vDepth);
  gl_FragColor = vec4(uColor * a, a);
}`;

/** Seeded random numbers so every visit draws the same brain. */
function rng(seed: number): () => number {
  let s = seed >>> 0;
  return () => {
    s = (s + 0x6d2b79f5) >>> 0;
    let t = s;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/** Neuron positions on two folded hemispheres plus a few inside, as x,y,z triples. */
export function brainPoints(count: number, seed = 7): Float32Array {
  const rand = rng(seed);
  const out = new Float32Array(count * 3);
  for (let i = 0; i < count; i++) {
    // a random direction on the sphere
    const u = rand() * 2 - 1;
    const th = rand() * Math.PI * 2;
    const s = Math.sqrt(1 - u * u);
    let x = s * Math.cos(th);
    let y = u;
    let z = s * Math.sin(th);
    // folds (gyri) and a few neurons deeper inside
    const fold = 1 + 0.07 * Math.sin(7 * x + 2) * Math.sin(6 * y + 1) * Math.sin(5 * z);
    const r = (rand() < 0.12 ? 0.55 + rand() * 0.35 : 1) * fold;
    x *= r;
    y *= r;
    z *= r;
    // two hemispheres with a groove between them, flatter underneath, longer front to back
    const side = x >= 0 ? 1 : -1;
    x = side * (0.12 + Math.abs(x) * 0.78);
    y = y < 0 ? y * 0.68 : y * 0.86;
    z *= 1.18;
    out.set([x, y, z], i * 3);
  }
  return out;
}

/** Each neuron linked to up to {@code k} near neighbours on the same hemisphere, as index pairs. */
export function brainLinks(points: Float32Array, k = 3, maxDist = 0.32): Uint16Array {
  const n = points.length / 3;
  const pairs: number[] = [];
  const seen = new Set<number>();
  const max2 = maxDist * maxDist;
  for (let i = 0; i < n; i++) {
    const near: [number, number][] = [];
    for (let j = 0; j < n; j++) {
      if (i === j || Math.sign(points[i * 3]) !== Math.sign(points[j * 3])) continue;
      const dx = points[i * 3] - points[j * 3];
      const dy = points[i * 3 + 1] - points[j * 3 + 1];
      const dz = points[i * 3 + 2] - points[j * 3 + 2];
      const d2 = dx * dx + dy * dy + dz * dz;
      if (d2 < max2) near.push([d2, j]);
    }
    near.sort((a, b) => a[0] - b[0]);
    for (const [, j] of near.slice(0, k)) {
      const key = Math.min(i, j) * n + Math.max(i, j);
      if (!seen.has(key)) {
        seen.add(key);
        pairs.push(i, j);
      }
    }
  }
  return new Uint16Array(pairs);
}

function perspective(fovY: number, aspect: number, near: number, far: number): Float32Array {
  const f = 1 / Math.tan(fovY / 2);
  const nf = 1 / (near - far);
  return new Float32Array([f / aspect, 0, 0, 0, 0, f, 0, 0, 0, 0, (far + near) * nf, -1, 0, 0, 2 * far * near * nf, 0]);
}

/**
 * Rotate about X then Y, then push back by {@code dist} (column-major), written into {@code out}: this runs every
 * frame, so it fills one array instead of making a new one for the garbage collector each time.
 */
export function view(rx: number, ry: number, dist: number, tx: number, ty: number, out = new Float32Array(16)): Float32Array {
  const cx = Math.cos(rx), sx = Math.sin(rx), cy = Math.cos(ry), sy = Math.sin(ry);
  out[0] = cy; out[1] = sx * sy; out[2] = -cx * sy; out[3] = 0;
  out[4] = 0; out[5] = cx; out[6] = sx; out[7] = 0;
  out[8] = sy; out[9] = -sx * cy; out[10] = cx * cy; out[11] = 0;
  out[12] = tx; out[13] = ty; out[14] = -dist; out[15] = 1;
  return out;
}

const POSE_KEYS = ['x', 'y', 'zoom', 'alpha', 'spin', 'lean'] as const;

/** The WebGL scene: neurons as points and the connections between them as lines. */
export const createNeuralScene: SceneFactory = (canvas, { small, colors: initial }) => {
  const gl = canvas.getContext('webgl', { alpha: true, antialias: true, premultipliedAlpha: true, powerPreference: 'low-power' });
  if (!gl) {
    return null;
  }
  const compile = (vs: string, fs: string): WebGLProgram | null => {
    const p = gl.createProgram();
    for (const [type, src] of [[gl.VERTEX_SHADER, vs], [gl.FRAGMENT_SHADER, fs]] as const) {
      const s = gl.createShader(type)!;
      gl.shaderSource(s, src);
      gl.compileShader(s);
      gl.attachShader(p, s);
      gl.deleteShader(s);
    }
    gl.linkProgram(p);
    return gl.getProgramParameter(p, gl.LINK_STATUS) ? p : null;
  };
  const pointProg = compile(POINT_VS, POINT_FS);
  const lineProg = compile(LINE_VS, LINE_FS);
  if (!pointProg || !lineProg) {
    return null;
  }

  const count = small ? 520 : 1300;
  const signals = small ? 60 : 150;
  const pts = brainPoints(count);
  const links = brainLinks(pts);
  const rand = rng(11);

  // neurons, then signals riding along random links
  const stride = 10;
  const data = new Float32Array((count + signals) * stride);
  for (let i = 0; i < count; i++) {
    const p = pts.subarray(i * 3, i * 3 + 3);
    const mix = Math.min(1, Math.max(0, 0.5 + p[0] * 0.35 - p[1] * 0.3));
    data.set([...p, ...p, mix, 2.2 + rand() * 3.2, rand(), 0], i * stride);
  }
  for (let i = 0; i < signals; i++) {
    const l = Math.floor(rand() * (links.length / 2)) * 2;
    const [a, b] = rand() < 0.5 ? [links[l], links[l + 1]] : [links[l + 1], links[l]];
    data.set([...pts.subarray(a * 3, a * 3 + 3), ...pts.subarray(b * 3, b * 3 + 3), -1, 7 + rand() * 4, rand(), 0.35 + rand() * 0.5], (count + i) * stride);
  }
  const pointBuf = gl.createBuffer();
  gl.bindBuffer(gl.ARRAY_BUFFER, pointBuf);
  gl.bufferData(gl.ARRAY_BUFFER, data, gl.STATIC_DRAW);

  const lineData = new Float32Array(links.length * 3);
  links.forEach((idx, i) => lineData.set(pts.subarray(idx * 3, idx * 3 + 3), i * 3));
  const lineBuf = gl.createBuffer();
  gl.bindBuffer(gl.ARRAY_BUFFER, lineBuf);
  gl.bufferData(gl.ARRAY_BUFFER, lineData, gl.STATIC_DRAW);

  const u = (p: WebGLProgram, name: string) => gl.getUniformLocation(p, name);
  const pu = Object.fromEntries(['uProj', 'uView', 'uTime', 'uDpr', 'uDist', 'uFrom', 'uVia', 'uTo', 'uSignal', 'uAlpha'].map((n) => [n, u(pointProg, n)]));
  const lu = Object.fromEntries(['uProj', 'uView', 'uDist', 'uColor', 'uAlpha'].map((n) => [n, u(lineProg, n)]));
  const aA = gl.getAttribLocation(pointProg, 'aA');
  const aB = gl.getAttribLocation(pointProg, 'aB');
  const aMeta = gl.getAttribLocation(pointProg, 'aMeta');
  // made once, reused every frame (render() allocates nothing)
  const pointAttribs = [[aA, 3, 0], [aB, 3, 3], [aMeta, 4, 6]] as const;
  const pointLocs = [aA, aB, aMeta] as const;
  const aPos = gl.getAttribLocation(lineProg, 'aPos');

  let colors = initial;
  let dpr = 1;
  let proj = perspective(0.8, 1, 0.1, 20);
  const viewMatrix = new Float32Array(16);
  let dist = 3.4;
  let aspect = 1;
  let rx = 0, ry = 0; // eased pointer tilt
  let angle = 0.5; // turn about the vertical axis
  let swingSpeed = 0; // extra turning speed from a page change, dying away
  let last = -1;
  const cur: ScenePose = { x: 0, y: 0, zoom: 1, alpha: 1, spin: 1, lean: 1 };
  let target: ScenePose = { ...cur };

  const resize = () => {
    dpr = Math.min(window.devicePixelRatio || 1, small ? 1.5 : 2);
    const w = Math.max(1, Math.round(canvas.clientWidth * dpr));
    const h = Math.max(1, Math.round(canvas.clientHeight * dpr));
    if (canvas.width !== w || canvas.height !== h) {
      canvas.width = w;
      canvas.height = h;
    }
    aspect = w / h;
    // keep the whole brain in view on tall phone screens
    dist = aspect < 1 ? 3.1 / Math.max(aspect, 0.45) : 3.4;
    proj = perspective(0.8, aspect, 0.1, 20);
    gl.viewport(0, 0, w, h);
  };
  resize();

  return {
    resize,
    setColors: (c) => (colors = c),
    setPose(pose, swing = 0, instant = false) {
      target = { ...pose };
      swingSpeed += swing;
      if (instant) {
        Object.assign(cur, pose);
        swingSpeed = 0;
      }
    },
    render(time, px, py) {
      const dt = last < 0 ? 0 : Math.min(Math.max(time - last, 0), 0.05);
      last = time;
      const k = 1 - Math.exp(-dt * 2.6);
      let settled = swingSpeed < 0.01;
      for (const key of POSE_KEYS) {
        cur[key] += (target[key] - cur[key]) * k;
        if (Math.abs(target[key] - cur[key]) > 0.002) settled = false;
      }
      angle += dt * (0.06 * cur.spin + swingSpeed);
      swingSpeed *= Math.exp(-dt * 2.2);
      const lk = 1 - Math.exp(-dt * 3);
      rx += (py * 0.22 * cur.lean - rx) * lk;
      ry += (px * 0.35 * cur.lean - ry) * lk;
      // side-by-side framings only make sense on wide screens; phones keep the brain centred
      const wide = Math.min(1, Math.max(0, (aspect - 0.9) / 0.5));
      const v = view(0.18 + rx, angle + ry, dist * cur.zoom, cur.x * wide, 0.05 + cur.y, viewMatrix);
      gl.clearColor(0, 0, 0, 0);
      gl.clear(gl.COLOR_BUFFER_BIT);
      gl.enable(gl.BLEND);
      // additive glow on the dark theme, plain premultiplied "over" on the light one
      gl.blendFunc(gl.ONE, colors.light ? gl.ONE_MINUS_SRC_ALPHA : gl.ONE);

      gl.useProgram(lineProg);
      gl.uniformMatrix4fv(lu['uProj'], false, proj);
      gl.uniformMatrix4fv(lu['uView'], false, v);
      gl.uniform1f(lu['uDist'], dist * cur.zoom);
      gl.uniform3fv(lu['uColor'], colors.link);
      gl.uniform1f(lu['uAlpha'], (colors.light ? 0.16 : 0.13) * cur.alpha);
      gl.bindBuffer(gl.ARRAY_BUFFER, lineBuf);
      gl.enableVertexAttribArray(aPos);
      gl.vertexAttribPointer(aPos, 3, gl.FLOAT, false, 0, 0);
      gl.drawArrays(gl.LINES, 0, links.length);
      gl.disableVertexAttribArray(aPos);

      gl.useProgram(pointProg);
      gl.uniformMatrix4fv(pu['uProj'], false, proj);
      gl.uniformMatrix4fv(pu['uView'], false, v);
      gl.uniform1f(pu['uTime'], time);
      gl.uniform1f(pu['uDpr'], dpr);
      gl.uniform1f(pu['uDist'], dist * cur.zoom);
      gl.uniform3fv(pu['uFrom'], colors.from);
      gl.uniform3fv(pu['uVia'], colors.via);
      gl.uniform3fv(pu['uTo'], colors.to);
      gl.uniform3fv(pu['uSignal'], colors.signal);
      gl.uniform1f(pu['uAlpha'], (colors.light ? 0.7 : 0.85) * cur.alpha);
      gl.bindBuffer(gl.ARRAY_BUFFER, pointBuf);
      const bytes = stride * 4;
      for (const [loc, size, offset] of pointAttribs) {
        gl.enableVertexAttribArray(loc);
        gl.vertexAttribPointer(loc, size, gl.FLOAT, false, bytes, offset * 4);
      }
      gl.drawArrays(gl.POINTS, 0, count + signals);
      for (const loc of pointLocs) gl.disableVertexAttribArray(loc);
      return settled;
    },
    dispose() {
      gl.deleteBuffer(pointBuf);
      gl.deleteBuffer(lineBuf);
      gl.deleteProgram(pointProg);
      gl.deleteProgram(lineProg);
      gl.getExtension('WEBGL_lose_context')?.loseContext();
    },
  };
};
