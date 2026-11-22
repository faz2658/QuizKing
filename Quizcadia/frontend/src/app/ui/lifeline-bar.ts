import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { Lifeline } from '../core/models';

/** The lifeline buttons, in the order they are shown. */
export const LIFELINES: { id: Lifeline; label: string; icon: string; hint: string }[] = [
  { id: 'FIFTY_FIFTY', label: '50/50', icon: '✂️', hint: 'Remove two wrong answers' },
  { id: 'EXTRA_TIME', label: '+10s', icon: '⏱️', hint: 'Ten more seconds' },
  { id: 'SKIP', label: 'Skip', icon: '⏭️', hint: 'Move on without losing your combo' },
];

/** The three once-per-game helps. */
@Component({
  selector: 'mv-lifeline-bar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="bar" role="group" aria-label="Lifelines (once each per game)">
      @for (l of all; track l.id) {
        <button type="button" class="life" [disabled]="disabled() || !available().includes(l.id) || (l.id === 'FIFTY_FIFTY' && optionCount() < 4)"
          [attr.aria-label]="l.label + ': ' + l.hint" [title]="l.hint" (click)="use.emit(l.id)">
          <span aria-hidden="true">{{ l.icon }}</span> {{ l.label }}
        </button>
      }
    </div>
  `,
  styles: `
    .bar { display: flex; gap: 10px; justify-content: center; margin-top: 16px; flex-wrap: wrap; }
    .life { border: 1px solid var(--border); background: var(--bg-raised); color: var(--text); border-radius: 999px; padding: 8px 16px;
      font-weight: 800; cursor: pointer; transition: transform 0.15s ease, box-shadow 0.15s ease; }
    .life:hover:not(:disabled) { transform: translateY(-2px); box-shadow: 0 6px 18px color-mix(in srgb, var(--accent) 30%, transparent); }
    .life:disabled { opacity: 0.35; cursor: not-allowed; text-decoration: line-through; }
  `,
})
export class LifelineBar {
  protected readonly all = LIFELINES;
  readonly available = input<Lifeline[]>([]);
  readonly disabled = input(false);
  readonly optionCount = input(4);
  readonly use = output<Lifeline>();
}
