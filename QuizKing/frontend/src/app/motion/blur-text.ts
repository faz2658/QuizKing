import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/**
 * Words fade in from a blur, one after another. Wrap a word in *asterisks* to give it the gradient
 * glow ("Think fast. Play *smart*."). Screen readers get the plain sentence.
 */
@Component({
  selector: 'mv-blur-text',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="sr-only">{{ plain() }}</span>
    <span aria-hidden="true">
      @for (w of words(); track $index) {
        <span class="word" [class.glow]="w.glow" [style.animation-delay.ms]="delay() + $index * stagger()">{{ w.text }}</span>{{ ' ' }}
      }
    </span>
  `,
  styles: `
    .word { display: inline-block; animation: blur-in 0.75s cubic-bezier(0.2, 0.7, 0.2, 1) both; }
    .glow {
      background: linear-gradient(100deg, var(--pink), var(--lavender) 45%, var(--accent));
      -webkit-background-clip: text; background-clip: text; color: transparent;
      filter: drop-shadow(0 0 22px color-mix(in srgb, var(--pink) 45%, transparent));
      font-style: italic; padding-right: 0.06em;
    }
    @keyframes blur-in { from { opacity: 0; filter: blur(12px); transform: translateY(0.35em); } }
    @media (prefers-reduced-motion: reduce) { .word { animation: none; } }
  `,
})
export class BlurText {
  readonly text = input.required<string>();
  readonly delay = input(0);
  readonly stagger = input(90);

  protected readonly words = computed(() =>
    this.text().split(/\s+/).filter(Boolean).map((w) => {
      const glow = /^\*.*\*[.,!?]?$/.test(w);
      return { text: glow ? w.replace(/\*/g, '') : w, glow };
    }),
  );
  protected readonly plain = computed(() => this.text().replace(/\*/g, ''));
}
