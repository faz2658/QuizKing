import { Injectable, signal } from '@angular/core';

const STORAGE_KEY = 'quizcadia.fx';

/** Whether games play sounds and vibrate. */
export interface FxSettings {
  sound: boolean;
  vibration: boolean;
}

type Cue = 'correct' | 'wrong' | 'tick' | 'combo' | 'fanfare';

/** Notes (Hz) and length (s) of each cue: short synth blips, no audio files to download. */
export const CUES: Record<Cue, { notes: number[]; step: number; type: OscillatorType; gain: number }> = {
  correct: { notes: [660, 990], step: 0.09, type: 'sine', gain: 0.18 },
  wrong: { notes: [220, 165], step: 0.14, type: 'square', gain: 0.07 },
  tick: { notes: [1200], step: 0.04, type: 'sine', gain: 0.06 },
  combo: { notes: [660, 830, 990, 1320], step: 0.07, type: 'triangle', gain: 0.16 },
  fanfare: { notes: [523, 659, 784, 1047], step: 0.12, type: 'triangle', gain: 0.18 },
};

const VIBRATION: Partial<Record<Cue, number[]>> = { correct: [25], wrong: [70, 50, 70], combo: [20, 30, 20, 30, 40] };

/** Sound effects and phone vibration for answers, the last seconds of the timer and great results. */
@Injectable({ providedIn: 'root' })
export class GameFx {
  private readonly _settings = signal<FxSettings>(this.restore());
  readonly settings = this._settings.asReadonly();
  private ctx: AudioContext | null = null;

  /** Switches sound or vibration on or off and remembers the choice on this device. */
  toggle(key: keyof FxSettings): void {
    const next = { ...this._settings(), [key]: !this._settings()[key] };
    this._settings.set(next);
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
    } catch {
      /* private mode: the setting lasts for this visit */
    }
  }

  /** Plays a cue's sound and vibration, as far as the settings and the device allow. */
  play(cue: Cue): void {
    const s = this._settings();
    if (s.vibration && VIBRATION[cue] && typeof navigator !== 'undefined' && 'vibrate' in navigator) {
      navigator.vibrate(VIBRATION[cue]!);
    }
    if (!s.sound) {
      return;
    }
    const ctx = this.audio();
    if (!ctx) {
      return;
    }
    const { notes, step, type, gain } = CUES[cue];
    const start = ctx.currentTime;
    notes.forEach((freq, i) => {
      const osc = ctx.createOscillator();
      const amp = ctx.createGain();
      osc.type = type;
      osc.frequency.value = freq;
      const t = start + i * step;
      amp.gain.setValueAtTime(0.0001, t);
      amp.gain.exponentialRampToValueAtTime(gain, t + 0.01);
      amp.gain.exponentialRampToValueAtTime(0.0001, t + step * 1.6);
      osc.connect(amp).connect(ctx.destination);
      osc.start(t);
      osc.stop(t + step * 1.7);
    });
  }

  private audio(): AudioContext | null {
    if (typeof window === 'undefined' || !('AudioContext' in window)) {
      return null;
    }
    this.ctx ??= new AudioContext();
    if (this.ctx.state === 'suspended') {
      void this.ctx.resume();
    }
    return this.ctx;
  }

  private restore(): FxSettings {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (raw) {
        return { sound: true, vibration: true, ...(JSON.parse(raw) as Partial<FxSettings>) };
      }
    } catch {
      /* ignore broken or unavailable storage */
    }
    return { sound: true, vibration: true };
  }
}
