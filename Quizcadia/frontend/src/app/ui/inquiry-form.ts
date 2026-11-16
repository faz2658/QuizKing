import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { track } from '../core/analytics';
import { errorMessage } from '../core/http-errors';
import { InquiryApi, InquiryKind } from '../core/inquiries';

/** The request form on /events and /venues. Works without an account; the owner sees requests on the admin page. */
@Component({
  selector: 'mv-inquiry-form',
  imports: [FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (sent()) {
      <div class="card done" role="status">
        <h3>Thanks, {{ name }}! 🎉</h3>
        <p class="muted">We've got your request and will reply to <strong>{{ email }}</strong> with the details and a quote.</p>
      </div>
    } @else {
      <form class="card form" (ngSubmit)="submit()" #f="ngForm">
        <h3>{{ kind() === 'EVENT' ? 'Request a private quiz night' : 'Ask about trivia nights at your venue' }}</h3>
        <div class="grid">
          <label class="field">Your name *
            <input class="input" name="name" [(ngModel)]="name" required maxlength="80" autocomplete="name" />
          </label>
          <label class="field">Email *
            <input class="input" type="email" name="email" [(ngModel)]="email" required maxlength="120" autocomplete="email" />
          </label>
          <label class="field">{{ kind() === 'EVENT' ? 'Company, school or group' : 'Venue name' }}{{ kind() === 'VENUE' ? ' *' : '' }}
            <input class="input" name="organisation" [(ngModel)]="organisation" [required]="kind() === 'VENUE'" maxlength="120" autocomplete="organization" />
          </label>
          <label class="field">City
            <input class="input" name="city" [(ngModel)]="city" maxlength="80" autocomplete="address-level2" />
          </label>
          @if (kind() === 'EVENT') {
            <label class="field">Occasion
              <select class="input" name="occasion" [(ngModel)]="occasion">
                <option value="">Choose…</option>
                @for (o of occasions; track o) { <option [value]="o">{{ o }}</option> }
              </select>
            </label>
            <label class="field">Preferred date
              <input class="input" type="date" name="date" [(ngModel)]="date" [min]="today" />
            </label>
            <label class="field">How many people?
              <input class="input" type="number" name="groupSize" [(ngModel)]="groupSize" min="1" max="5000" inputmode="numeric" />
            </label>
          } @else {
            <label class="field">Which night would suit you?
              <select class="input" name="occasion" [(ngModel)]="occasion">
                <option value="">Choose…</option>
                @for (o of nights; track o) { <option [value]="o">{{ o }}</option> }
              </select>
            </label>
            <label class="field">Guests on a typical evening
              <input class="input" type="number" name="groupSize" [(ngModel)]="groupSize" min="1" max="5000" inputmode="numeric" />
            </label>
          }
        </div>
        <label class="field">{{ kind() === 'EVENT' ? 'Tell us about it (topics, in person or online, anything special)' : 'Anything we should know?' }}
          <textarea class="input" name="message" rows="4" [(ngModel)]="message" maxlength="2000"></textarea>
        </label>
        <!-- People never see this; bots fill it in -->
        <label class="trap" aria-hidden="true">Website <input name="website" [(ngModel)]="website" tabindex="-1" autocomplete="off" /></label>
        @if (error()) { <p class="alert" role="alert">{{ error() }}</p> }
        <div class="row">
          <button class="btn btn-primary" type="submit" [disabled]="busy() || !f.valid">{{ busy() ? 'Sending…' : 'Send request' }}</button>
          <span class="muted small">No account needed. We only use your details to reply to you.</span>
        </div>
      </form>
    }
  `,
  styles: `
    .form { display: flex; flex-direction: column; gap: 14px; padding: 24px; }
    .form h3 { margin: 0; }
    .grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
    .trap { position: absolute; left: -10000px; width: 1px; height: 1px; overflow: hidden; }
    .small { font-size: 0.85rem; }
    .done { padding: 24px; }
    .done h3 { margin-top: 0; }
    @media (max-width: 640px) { .grid { grid-template-columns: 1fr; } }
  `,
})
export class InquiryForm {
  readonly kind = input.required<InquiryKind>();
  private readonly api = inject(InquiryApi);

  protected readonly occasions = ['Team event', 'Office party', 'School or class', 'Birthday', 'Wedding or engagement', 'Club or community', 'Other'];
  protected readonly nights = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Weekend', 'Not sure yet'];
  protected readonly today = new Date().toISOString().slice(0, 10);

  protected name = '';
  protected email = '';
  protected organisation = '';
  protected city = '';
  protected occasion = '';
  protected date = '';
  protected groupSize: number | null = null;
  protected message = '';
  protected website = '';

  protected readonly busy = signal(false);
  protected readonly sent = signal(false);
  protected readonly error = signal<string | null>(null);

  protected submit(): void {
    if (this.busy()) return;
    this.busy.set(true);
    this.error.set(null);
    this.api.send({
      kind: this.kind(), name: this.name, email: this.email, organisation: this.organisation, city: this.city,
      occasion: this.occasion, date: this.date, groupSize: this.groupSize, message: this.message, website: this.website,
    }).subscribe({
      next: () => {
        this.sent.set(true);
        this.busy.set(false);
        track('generate_lead', { lead_type: this.kind().toLowerCase() });
      },
      error: (e) => {
        this.busy.set(false);
        this.error.set(e?.status === 429 ? 'Too many requests from here. Please try again in an hour.'
          : e?.status === 400 ? 'Please check the fields: a valid email is needed, and dates look like 2026-12-31.'
          : errorMessage(e));
      },
    });
  }
}
