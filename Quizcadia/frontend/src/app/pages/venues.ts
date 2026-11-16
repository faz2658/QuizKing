import { ChangeDetectionStrategy, Component } from '@angular/core';
import { InquiryForm } from '../ui/inquiry-form';

/** Weekly trivia nights for bars, cafés and restaurants: the pitch for venue owners, and a request form. */
@Component({
  selector: 'mv-venues',
  imports: [InquiryForm],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="hero">
      <span class="badge"><span class="dot"></span>For bars, pubs and cafés</span>
      <h1>Fill your quiet night with trivia</h1>
      <p class="lead muted">A weekly quiz night that brings regulars back: fresh questions every week on your TV, players
        answering on their phones, and a leaderboard they'll want to top. No quizmaster, no paper, no setup.</p>
      <a class="btn btn-primary" href="#request">Ask about your venue</a>
    </section>

    <section class="why">
      @for (w of reasons; track w.title) {
        <div class="card reason"><span class="icon" aria-hidden="true">{{ w.icon }}</span><h3>{{ w.title }}</h3><p class="muted">{{ w.text }}</p></div>
      }
    </section>

    <section class="night card">
      <h2>What a night looks like</h2>
      <ol>
        <li>Open QuizCadia on the bar's TV. A short code appears on screen.</li>
        <li>Guests join on their phones as a team or alone, in seconds. No download and no sign-up for them.</li>
        <li>Questions appear on the TV; answers come in from phones. Faster right answers score more.</li>
        <li>The live leaderboard keeps every table watching, and the winners get bragging rights (and your prize, if you like).</li>
      </ol>
    </section>

    <section class="need card">
      <h2>All you need</h2>
      <ul>
        <li>A TV or projector with a browser (a laptop or streaming stick plugged in works).</li>
        <li>Wi-Fi or mobile data for your guests.</li>
        <li>A night of the week you'd like to be busier.</li>
      </ul>
    </section>

    <section id="request" class="request">
      <mv-inquiry-form kind="VENUE" />
    </section>
  `,
  styles: `
    :host { display: flex; flex-direction: column; gap: 32px; }
    .hero { display: flex; flex-direction: column; align-items: flex-start; gap: 14px; padding-top: 8px; }
    .hero h1 { margin: 0; font-size: clamp(2rem, 5vw, 3.2rem); }
    .lead { font-size: 1.1rem; max-width: 720px; margin: 0; }
    .why { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; }
    .reason { padding: 20px; }
    .reason h3 { margin: 8px 0 6px; }
    .reason p { margin: 0; }
    .icon { font-size: 1.8rem; }
    .night, .need { padding: 24px; }
    .night h2, .need h2 { margin-top: 0; }
    .night ol, .need ul { margin: 0; padding-left: 22px; display: grid; gap: 10px; }
    .request { scroll-margin-top: 90px; }
    @media (max-width: 960px) { .why { grid-template-columns: 1fr 1fr; } }
    @media (max-width: 560px) { .why { grid-template-columns: 1fr; } }
  `,
})
export class Venues {
  protected readonly reasons = [
    { icon: '📅', title: 'A reason to come back', text: 'A weekly fixture turns first-time visitors into regulars who bring friends.' },
    { icon: '🍻', title: 'Longer, busier evenings', text: 'Teams settle in for the whole quiz instead of one quick drink.' },
    { icon: '🆕', title: 'Fresh questions every week', text: 'Thousands of questions across dozens of topics, plus themed nights on request.' },
    { icon: '🙌', title: 'Runs itself', text: 'No quizmaster to book and no answer sheets to mark: scoring and the leaderboard are automatic.' },
  ];
}
