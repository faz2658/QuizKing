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
        <span class="word" [style.animation-delay.ms]="delay() + $index * stagger()"><span [class.glow]="w.glow">{{ w.text }}</span></span>{{ ' ' }}
      }
    </span>
  `,
  styles: `
    .word { display: inline-block; animation: blur-in 0.75s cubic-bezier(0.2, 0.7, 0.2, 1) backwards; }
    /* The gradient lives on an inner span with no filter of its own. A filter on the same element as
       background-clip: text (the old drop-shadow glow) left the word stuck blurred and faded in Safari
       and at some sizes, because the blur-in animation had to blend into it. */
    .glow {
      display: inline-block;
      background: linear-gradient(100deg, var(--pink), var(--lavender) 45%, var(--accent));
      -webkit-background-clip: text; background-clip: text; color: transparent; -webkit-text-fill-color: transparent;
      font-style: italic; padding-right: 0.08em;
    }
    @keyframes blur-in {
      from { opacity: 0; filter: blur(12px); transform: translateY(0.35em); }
      to { opacity: 1; filter: none; transform: none; }
    }
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
