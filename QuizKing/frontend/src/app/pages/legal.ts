import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Analytics } from '../core/analytics';

const UPDATED = '15 November 2026';

const styles = `
  :host { display: block; max-width: 760px; margin: 0 auto; padding: 8px 0 56px; line-height: 1.6; }
  h1 { margin: 8px 0 4px; }
  h2 { margin: 28px 0 8px; font-size: 1.15rem; }
  p, li { color: var(--text); }
  ul { padding-left: 22px; }
  .updated { margin-top: 0; }
  .linkish { background: none; border: 0; padding: 0; color: var(--accent); text-decoration: underline; cursor: pointer; font: inherit; }
`;

/** Plain-language privacy policy (linked from the footer and from Google's sign-in screen). */
@Component({
  selector: 'mv-privacy',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1>Privacy policy</h1>
    <p class="muted updated">Last updated {{ updated }}</p>
    <p>Quiz King is a quiz game. This page explains what we store about you, why, and what you can do about it.</p>

    <h2>What we collect</h2>
    <ul>
      <li><strong>Account details:</strong> your display name and email address. Passwords are stored only as a secure
        one-way hash; we can never see them.</li>
      <li><strong>Sign in with Google:</strong> if you use it, Google tells us your name, your email address and a Google
        account identifier. We don't receive your Google password or access anything else in your Google account.</li>
      <li><strong>Game activity:</strong> games you play, your answers, scores, XP, badges, streaks, and quizzes you
        create (including "How well do you know me?" questions and AI quiz topics).</li>
      <li><strong>Country (optional):</strong> if you pick one, it's shown as a flag next to your name and used for the
        country leaderboards.</li>
      <li><strong>Guests:</strong> playing as a guest needs only a nickname; no account or email is stored.</li>
      <li><strong>Your browser:</strong> we keep your sign-in session and a few preferences (sound, country, your
        analytics choice) in your browser's local storage. We don't use advertising cookies.</li>
      <li><strong>Google Analytics (only if you allow it):</strong> when you first visit we ask whether we may use
        Google Analytics. If you say yes, Google sets analytics cookies and receives which pages you open, rough
        location from your IP address, your device and browser type, and events such as signing in, signing up or
        sharing a link. We never send your name, email or account id to Google, and advertising features are turned
        off. If you say no, the analytics script never loads.</li>
    </ul>

    <h2>How we use it</h2>
    <ul>
      <li>To run the game: sign you in, save your progress, and show leaderboards and results.</li>
      <li>To show your display name, flag and scores to other players on leaderboards, in multiplayer matches and on
        quizzes you share.</li>
      <li>To send password reset emails when you ask for one. We don't send marketing email.</li>
      <li>To understand how the game is used (with Google Analytics, if you allow it) so we can improve it.</li>
    </ul>
    <p>We don't sell your data or share it with advertisers. Google processes analytics data as described in
      <a href="https://policies.google.com/technologies/partner-sites" target="_blank" rel="noopener">How Google uses information from sites that use its services</a>.</p>

    <h2>Where it's kept</h2>
    <p>Quiz King runs on servers in Oracle Cloud's Toronto, Canada region. Data travels over encrypted HTTPS connections.</p>

    <h2>How long we keep it</h2>
    <p>We keep your account and game history while your account exists. Password reset links expire after 30 minutes and
      are deleted after a day.</p>

    <h2>Your choices</h2>
    <ul>
      <li>You can play as a guest without giving us any personal details.</li>
      <li>You can clear your country at any time.</li>
      <li>You can turn analytics off at any time: <button type="button" class="linkish" (click)="optOut()">stop analytics</button>
        @if (optedOut()) { <span class="muted">(done)</span> }. You can also block it with Google's
        <a href="https://tools.google.com/dlpage/gaoptout" target="_blank" rel="noopener">opt-out add-on</a>.</li>
      <li>You can ask us to delete your account and game history by writing to the support email shown on the Google
        sign-in screen; we'll do it within 30 days.</li>
    </ul>

    <h2>Children</h2>
    <p>Quiz King isn't directed at children under 13, and we don't knowingly collect their personal information.</p>

    <h2>Changes</h2>
    <p>If this policy changes, we'll update the date above. See also our <a routerLink="/terms">terms of service</a>.</p>
  `,
  styles,
})
export class Privacy {
  private readonly analytics = inject(Analytics);
  protected readonly updated = UPDATED;
  protected readonly optedOut = signal(false);

  protected optOut(): void {
    this.analytics.choose('denied');
    this.optedOut.set(true);
  }
}

/** Plain-language terms of service. */
@Component({
  selector: 'mv-terms',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1>Terms of service</h1>
    <p class="muted updated">Last updated {{ updated }}</p>
    <p>By playing Quiz King you agree to these terms. They're short, so please read them.</p>

    <h2>Playing fair</h2>
    <ul>
      <li>Don't cheat, automate play, or try to break or overload the service.</li>
      <li>Choose a display name, quiz questions and topics that are fine for everyone to see. No hate, harassment,
        sexual content or personal information about other people.</li>
      <li>We may remove content or close accounts that break these rules.</li>
    </ul>

    <h2>Your content</h2>
    <p>You keep ownership of quizzes you write. By sharing them in Quiz King you let us store them and show them to the
      people you share them with.</p>

    <h2>AI-written questions</h2>
    <p>Some questions are written by an AI model and can occasionally be wrong. They're for fun, not a source of facts.</p>

    <h2>Trivia sources</h2>
    <p>Many questions come from the Open Trivia Database, used under the CC BY-SA 4.0 licence.</p>

    <h2>No warranty</h2>
    <p>Quiz King is provided as is, without guarantees that it will always be available or error-free. To the extent the
      law allows, we're not liable for losses from using it.</p>

    <h2>Changes</h2>
    <p>We may update these terms; the date above shows the latest version. See also our
      <a routerLink="/privacy">privacy policy</a>.</p>
  `,
  styles,
})
export class Terms {
  protected readonly updated = UPDATED;
}
