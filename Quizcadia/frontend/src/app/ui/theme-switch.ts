import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Theme, ThemeName } from '../core/theme';

/** Day / Night pill in the header; the lit half slides across and the whole site relights. */
@Component({
  selector: 'mv-theme-switch',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="switch" role="radiogroup" aria-label="Colour theme" [class.night]="theme.current() === 'dark'">
      <span class="knob" aria-hidden="true"></span>
      @for (o of options; track o.id) {
        <button type="button" role="radio" [attr.aria-checked]="theme.current() === o.id" [attr.aria-label]="o.label" [title]="o.label + ' theme'"
          (click)="theme.set(o.id)">
          <span class="ico" aria-hidden="true">{{ o.icon }}</span><span class="label">{{ o.label }}</span>
        </button>
      }
    </div>
  `,
  styles: `
    .switch {
      position: relative; display: inline-grid; grid-template-columns: 1fr 1fr; padding: 3px; border-radius: 999px;
      background: var(--surface); border: 1px solid var(--border);
    }
    .knob {
      position: absolute; top: 3px; bottom: 3px; left: 3px; width: calc(50% - 3px); border-radius: 999px;
      background: linear-gradient(135deg, #fff3d6, #ffd27a); box-shadow: 0 0 18px color-mix(in srgb, #ffc65a 55%, transparent);
      transition: transform 0.55s cubic-bezier(0.65, 0, 0.35, 1), background 0.55s, box-shadow 0.55s;
    }
    .night .knob {
      transform: translateX(100%);
      background: linear-gradient(135deg, #3b3275, #7c6cff); box-shadow: 0 0 18px color-mix(in srgb, #7c6cff 55%, transparent);
    }
    button {
      position: relative; z-index: 1; appearance: none; border: 0; background: transparent; cursor: pointer; font: inherit;
      display: inline-flex; align-items: center; justify-content: center; gap: 6px;
      padding: 5px 12px; border-radius: 999px; font-size: 0.82rem; font-weight: 700; color: var(--text-muted);
      transition: color 0.4s;
    }
    button[aria-checked='true'] { color: #1f1300; }
    .night button[aria-checked='true'] { color: #fff; }
    button:focus-visible { outline: 2px solid var(--lavender); outline-offset: 2px; }
    .ico { font-size: 0.95rem; line-height: 1; }
    @media (max-width: 680px) { .label { display: none; } button { padding: 5px 9px; } }
  `,
})
export class ThemeSwitch {
  protected readonly theme = inject(Theme);
  protected readonly options: { id: ThemeName; label: string; icon: string }[] = [
    { id: 'light', label: 'Day', icon: '☀' },
    { id: 'dark', label: 'Night', icon: '☾' },
  ];
}
