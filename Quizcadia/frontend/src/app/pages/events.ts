import { ChangeDetectionStrategy, Component } from '@angular/core';
import { InquiryForm } from '../ui/inquiry-form';

/** Private quiz nights for teams, schools and parties: what's included, how it works, and a request form. */
@Component({
  selector: 'mv-events',
  imports: [InquiryForm],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="hero">
      <span class="badge"><span class="dot"></span>Private quiz nights</span>
      <h1>A quiz night made for your group</h1>
      <p class="lead muted">Team events, office parties, classes, birthdays and weddings. Questions about your company, your
        topic or the people in the room. Everyone plays on their phone, with the scoreboard live on the big screen.</p>
      <a class="btn btn-primary" href="#request">Request a quiz night</a>
    </section>

    <section class="features">
      @for (f of features; track f.title) {
        <div class="card feature"><span class="icon" aria-hidden="true">{{ f.icon }}</span><h3>{{ f.title }}</h3><p class="muted">{{ f.text }}</p></div>
      }
    </section>

    <section class="how card">
      <h2>How it works</h2>
      <ol>
        <li><strong>Tell us about your group</strong> with the form below: the occasion, the date and roughly how many people.</li>
        <li><strong>We reply by email</strong> with ideas, the rounds and a quote.</li>
        <li><strong>We prepare your quiz</strong>: your own questions, our trivia, or a mix.</li>
        <li><strong>On the day</strong>, put QuizCadia on a TV or projector, everyone joins with a short code on their phone, and play.
          Works in the office, at home, or over a video call.</li>
      </ol>
    </section>

    <section class="ideas">
      <h2>Popular quiz nights</h2>
      <div class="chips">
        @for (i of ideas; track i) { <span class="chip">{{ i }}</span> }
      </div>
    </section>

    <section id="request" class="request">
      <mv-inquiry-form kind="EVENT" />
    </section>
  `,
  styles: `
    :host { display: flex; flex-direction: column; gap: 32px; }
    .hero { display: flex; flex-direction: column; align-items: flex-start; gap: 14px; padding-top: 8px; }
    .hero h1 { margin: 0; font-size: clamp(2rem, 5vw, 3.2rem); }
    .lead { font-size: 1.1rem; max-width: 720px; margin: 0; }
    .features { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; }
    .feature { padding: 20px; }
    .feature h3 { margin: 8px 0 6px; }
    .feature p { margin: 0; }
    .icon { font-size: 1.8rem; }
    .how { padding: 24px; }
    .how h2, .ideas h2 { margin-top: 0; }
    .how ol { margin: 0; padding-left: 22px; display: grid; gap: 10px; }
    .chips { display: flex; flex-wrap: wrap; gap: 8px; }
    .request { scroll-margin-top: 90px; }
    @media (max-width: 860px) { .features { grid-template-columns: 1fr; } }
  `,
})
export class Events {
  protected readonly features = [
    { icon: '📺', title: 'Big screen, phones in hand', text: 'Party mode shows questions and the live scoreboard on a TV; players answer on their own phones. No app to install.' },
    { icon: '✍️', title: 'Your own questions', text: 'Questions about your team, your products, your class topic or the guests of honour, mixed with our trivia if you like.' },
    { icon: '💞', title: '"How well do you know…?"', text: 'Perfect for birthdays and weddings: guests answer questions about the couple or the birthday star.' },
    { icon: '🏆', title: 'A real competition', text: 'Faster right answers score more, so everyone stays on the edge of their seat until the final question.' },
    { icon: '✨', title: 'Any topic', text: 'From 90s music to your industry: our AI helps write rounds on any subject, checked before your event.' },
    { icon: '🔒', title: 'Private to your group', text: 'Your game has its own join code. Nobody outside your group can get in.' },
  ];
  protected readonly ideas = ['Team-building', 'Holiday party', 'New starter icebreaker', 'Class revision', 'Birthday', 'Wedding or engagement', 'Family reunion', 'Club night', 'Charity fundraiser'];
}
