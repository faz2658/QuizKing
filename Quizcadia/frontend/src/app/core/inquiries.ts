import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

/** A private quiz night (EVENT) or trivia for a venue (VENUE). */
export type InquiryKind = 'EVENT' | 'VENUE';
/** Where an admin is with an inquiry. */
export type InquiryStatus = 'NEW' | 'CONTACTED' | 'BOOKED' | 'CLOSED';

/** A request for a private quiz night (EVENT) or venue trivia (VENUE), sent from /events or /venues. */
export interface InquiryRequest {
  kind: InquiryKind;
  name: string;
  email: string;
  organisation?: string;
  occasion?: string;
  date?: string;
  groupSize?: number | null;
  city?: string;
  message?: string;
  /** Hidden from people; only bots fill it in. */
  website?: string;
}

/** An inquiry as admins see it. */
export interface Inquiry extends InquiryRequest {
  id: string;
  status: InquiryStatus;
  createdAt: string;
  updatedAt: string;
}

/** Inquiries: anyone can send one, admins list and update them. */
@Injectable({ providedIn: 'root' })
export class InquiryApi {
  private readonly http = inject(HttpClient);

  send(r: InquiryRequest): Observable<{ received: boolean }> {
    return this.http.post<{ received: boolean }>('/api/v1/reports/inquiries', r);
  }

  /** Admins only. */
  list(): Observable<Inquiry[]> {
    return this.http.get<Inquiry[]>('/api/v1/reports/inquiries');
  }

  /** Admins only. */
  setStatus(id: string, status: InquiryStatus): Observable<Inquiry> {
    return this.http.patch<Inquiry>(`/api/v1/reports/inquiries/${id}`, { status });
  }
}
