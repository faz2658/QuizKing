import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface KnowMeQuestion {
  text: string;
  options: string[];
  /** Index of the right option: null until you've answered (always set for the owner). */
  answer: number | null;
}

export interface KnowMeEntry {
  rank: number;
  name: string;
  score: number;
  you: boolean;
}

export interface KnowMeView {
  id: string;
  ownerName: string;
  own: boolean;
  questions: KnowMeQuestion[];
  yourAnswers: number[] | null;
  yourScore: number | null;
  players: number;
  leaderboard: KnowMeEntry[];
  createdAt: string;
}

export interface KnowMeMine {
  id: string;
  questions: number;
  players: number;
  createdAt: string;
}

export interface KnowMeDraft {
  text: string;
  options: string[];
  answer: number;
}

@Injectable({ providedIn: 'root' })
export class KnowMeApi {
  private readonly http = inject(HttpClient);

  create(questions: KnowMeDraft[]): Observable<KnowMeView> {
    return this.http.post<KnowMeView>('/api/v1/knowme', { questions });
  }

  get(id: string): Observable<KnowMeView> {
    return this.http.get<KnowMeView>(`/api/v1/knowme/${encodeURIComponent(id)}`);
  }

  mine(): Observable<KnowMeMine[]> {
    return this.http.get<KnowMeMine[]>('/api/v1/knowme/mine');
  }

  answer(id: string, answers: number[]): Observable<KnowMeView> {
    return this.http.post<KnowMeView>(`/api/v1/knowme/${encodeURIComponent(id)}/answers`, { answers });
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`/api/v1/knowme/${encodeURIComponent(id)}`);
  }
}

/** A friendly verdict for a score, the line people screenshot. */
export function friendshipTitle(score: number, total: number): string {
  const p = total ? score / total : 0;
  if (p === 1) return 'Soulmate level 💞';
  if (p >= 0.8) return 'Best friend material 🫶';
  if (p >= 0.6) return 'Solid friend 😊';
  if (p >= 0.4) return 'Friendly acquaintance 🤝';
  return 'Do we even know each other? 😅';
}

/** Starting questions; the owner fills in the options (or writes their own). */
export const KNOW_ME_PROMPTS: string[] = [
  "What's my favourite food?",
  'What would I order at a coffee shop?',
  "What's my dream holiday destination?",
  "What's my biggest pet peeve?",
  'Cats or dogs?',
  "What's my go-to karaoke song?",
  'What was my first job?',
  'What am I most scared of?',
  "What's my favourite season?",
  'Which superpower would I pick?',
  'What do I do on a lazy Sunday?',
  "What's my hidden talent?",
  'Early bird or night owl?',
  "What's my comfort TV show?",
  'Which sport would I watch?',
  'What would I buy first if I won the lottery?',
  "What's my favourite dessert?",
  'Where did I grow up?',
  'What did I want to be as a kid?',
  'Which emoji do I use the most?',
];
