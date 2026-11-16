import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { errorMessage } from '../core/http-errors';
import { Inquiry, InquiryApi, InquiryStatus } from '../core/inquiries';
import { AdminTabs } from '../ui/admin-tabs';

/** Admin: requests from /events and /venues, newest first, with a status to track each one. */
@Component({
  selector: 'mv-requests',
  imports: [AdminTabs, DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <mv-admin-tabs />
    <h1>Event and venue requests</h1>
    <p class="muted">From quizcadia.com/events and /venues. Reply by email, then set the status so you can see where each one is.</p>

    <div class="row filters">
      <div class="pill-tabs" role="group" aria-label="Show">
        @for (f of filters; track f.id) {
          <button type="button" [class.on]="filter() === f.id" [attr.aria-pressed]="filter() === f.id" (click)="filter.set(f.id)">{{ f.label }} ({{ count(f.id) }})</button>
        }
      </div>
      <span class="spacer"></span>
      <button type="button" class="btn btn-ghost" (click)="load()">↻ Refresh</button>
    </div>

    @if (error()) { <div class="alert" role="alert">{{ error() }}</div> }
    @if (loading()) {
      <div class="skeleton" style="height: 120px"></div>
    } @else if (!shown().length) {
      <p class="muted empty">No {{ filter() === 'ALL' ? '' : filter().toLowerCase() + ' ' }}requests yet.</p>
    } @else {
      <ul class="list">
        @for (i of shown(); track i.id) {
          <li class="card item" [class.new]="i.status === 'NEW'">
            <div class="row top">
              <span class="chip">{{ i.kind === 'EVENT' ? '🎉 Quiz night' : '🍻 Venue' }}</span>
              <strong>{{ i.organisation || i.name }}</strong>
              <span class="muted small">{{ i.createdAt | date: 'd MMM y, HH:mm' }}</span>
              <span class="spacer"></span>
              <label class="small">Status
                <select class="input status" [value]="i.status" (change)="setStatus(i, $any($event.target).value)" [attr.aria-label]="'Status for ' + i.name">
                  @for (s of statuses; track s) { <option [value]="s" [selected]="s === i.status">{{ s.charAt(0) + s.slice(1).toLowerCase() }}</option> }
                </select>
              </label>
            </div>
            <p class="who">{{ i.name }} · <a [href]="mailto(i)">{{ i.email }}</a>@if (i.city) { · {{ i.city }} }</p>
            <p class="facts muted">
              @if (i.occasion) { <span>{{ i.kind === 'EVENT' ? 'Occasion' : 'Night' }}: {{ i.occasion }}</span> }
              @if (i.date) { <span>Date: {{ i.date }}</span> }
              @if (i.groupSize) { <span>{{ i.kind === 'EVENT' ? 'People' : 'Guests' }}: {{ i.groupSize }}</span> }
            </p>
            @if (i.message) { <p class="msg">{{ i.message }}</p> }
          </li>
        }
      </ul>
    }
  `,
  styles: `
    .filters { margin: 8px 0 16px; }
    .list { list-style: none; padding: 0; margin: 0; display: grid; gap: 12px; }
    .item { padding: 18px; }
    .item.new { border-color: var(--accent); }
    .top { gap: 10px; }
    .small { font-size: 0.85rem; }
    .status { padding: 6px 10px; margin-left: 6px; }
    .who { margin: 10px 0 4px; }
    .facts { display: flex; flex-wrap: wrap; gap: 16px; margin: 0; font-size: 0.9rem; }
    .msg { margin: 10px 0 0; white-space: pre-wrap; }
    .empty { padding: 24px 0; }
  `,
})
export class Requests {
  private readonly api = inject(InquiryApi);
  protected readonly statuses: InquiryStatus[] = ['NEW', 'CONTACTED', 'BOOKED', 'CLOSED'];
  protected readonly filters: { id: InquiryStatus | 'ALL'; label: string }[] = [
    { id: 'NEW', label: 'New' }, { id: 'CONTACTED', label: 'Contacted' }, { id: 'BOOKED', label: 'Booked' },
    { id: 'CLOSED', label: 'Closed' }, { id: 'ALL', label: 'All' },
  ];
  protected readonly filter = signal<InquiryStatus | 'ALL'>('NEW');
  protected readonly items = signal<Inquiry[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly shown = computed(() => this.items().filter((i) => this.filter() === 'ALL' || i.status === this.filter()));

  constructor() {
    this.load();
  }

  protected count(id: InquiryStatus | 'ALL'): number {
    return id === 'ALL' ? this.items().length : this.items().filter((i) => i.status === id).length;
  }

  protected load(): void {
    this.loading.set(true);
    this.api.list().subscribe({
      next: (list) => { this.items.set(list); this.loading.set(false); this.error.set(null); },
      error: (e) => { this.loading.set(false); this.error.set(errorMessage(e)); },
    });
  }

  protected setStatus(i: Inquiry, status: InquiryStatus): void {
    this.api.setStatus(i.id, status).subscribe({
      next: (saved) => this.items.update((list) => list.map((x) => (x.id === saved.id ? saved : x))),
      error: (e) => this.error.set(errorMessage(e)),
    });
  }

  protected mailto(i: Inquiry): string {
    const subject = i.kind === 'EVENT' ? 'Your QuizCadia quiz night' : 'Trivia nights at ' + (i.organisation ?? 'your venue');
    return `mailto:${i.email}?subject=${encodeURIComponent(subject)}`;
  }
}
