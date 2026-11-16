import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface TopicQuizEntry {
  rank: number;
  name: string;
  score: number;
  you: boolean;
}

export interface TopicQuizView {
  id: string;
  topic: string;
  creatorName: string;
  status: 'GENERATING' | 'READY' | 'FAILED';
  written: number;
  target: number;
  problem: string | null;
  questions: { text: string; options: string[]; answer: number | null }[];
  yourAnswers: number[] | null;
  yourScore: number | null;
  players: number;
  leaderboard: TopicQuizEntry[];
  createdAt: string;
}

const BASE = '/api/v1/question-generator/topic-quizzes';

@Injectable({ providedIn: 'root' })
export class TopicQuizApi {
  private readonly http = inject(HttpClient);

  status(): Observable<{ available: boolean }> {
    return this.http.get<{ available: boolean }>(`${BASE}/status`);
  }

  create(topic: string): Observable<TopicQuizView> {
    return this.http.post<TopicQuizView>(BASE, { topic });
  }

  get(id: string): Observable<TopicQuizView> {
    return this.http.get<TopicQuizView>(`${BASE}/${encodeURIComponent(id)}`);
  }

  answer(id: string, answers: number[]): Observable<TopicQuizView> {
    return this.http.post<TopicQuizView>(`${BASE}/${encodeURIComponent(id)}/answers`, { answers });
  }
}

/** Ideas to get people started; tapping one fills the topic box. */
export const TOPIC_IDEAS = [
  'Taylor Swift', 'Harry Potter', 'The Office', 'Formula 1', 'Pokémon', '90s cartoons', 'World Cup history',
  'Space exploration', 'Marvel movies', 'Ancient Egypt', 'Dinosaurs', 'K-pop', 'Cricket', 'Coffee',
];
