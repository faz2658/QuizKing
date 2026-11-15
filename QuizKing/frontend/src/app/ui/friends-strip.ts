import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

interface Mode {
  icon: string;
  title: string;
  text: string;
  link: string;
}

/** Home-page row of the "play with your people" modes. */
@Component({
  selector: 'mv-friends-strip',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2>Play with friends</h2>
    <div class="strip">
      @for (m of modes; track m.link) {
        <a class="card spotlight tile" [routerLink]="m.link">
          <span class="icon" aria-hidden="true">{{ m.icon }}</span>
          <span><strong>{{ m.title }}</strong><span class="muted">{{ m.text }}</span></span>
        </a>
      }
    </div>
  `,
  styles: `
    :host { display: block; margin-bottom: 40px; }
    h2 { margin: 0 0 14px; }
    .strip { display: grid; grid-template-columns: repeat(auto-fill, minmax(230px, 1fr)); gap: 16px; }
    .tile { display: flex; gap: 14px; align-items: center; padding: 18px; text-decoration: none; color: inherit; }
    .tile strong { display: block; margin-bottom: 2px; }
    .tile .muted { font-size: 0.88rem; }
    .icon { font-size: 1.7rem; width: 50px; height: 50px; flex: none; display: grid; place-items: center; border-radius: 14px;
      background: var(--surface-strong); border: 1px solid var(--border); }
  `,
})
export class FriendsStrip {
  protected readonly modes: Mode[] = [
    { icon: '💞', title: 'How well do you know me?', text: 'Write a quiz about you, see who knows you best.', link: '/know-me/new' },
    { icon: '📺', title: 'Party mode', text: 'Game on the TV, everyone plays on their phone.', link: '/party' },
    { icon: '✨', title: 'AI quiz on anything', text: 'Type a topic, the AI writes a quiz to share.', link: '/ai-quiz' },
  ];
}
