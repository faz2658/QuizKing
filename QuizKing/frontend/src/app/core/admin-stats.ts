import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface DayCount { day: string; count: number }
export interface Member { userId: number; userName: string; email: string; createdAt: string; google: boolean; admin: boolean }
export interface AccountStatsView {
  total: number; google: number; password: number; admins: number;
  today: number; last7Days: number; last30Days: number;
  signupsByDay: DayCount[]; recent: Member[];
}
export interface ActivityDay { day: string; games: number; players: number; guestGames: number }
export interface NameCount { name: string; count: number }
export interface TopPlayer { userId: number; name: string; country: string | null; games: number; points: number }
export interface ActivityStatsView {
  gamesAllTime: number; gamesInPeriod: number;
  activeToday: number; active7Days: number; active30Days: number; guestPlayers30Days: number;
  byDay: ActivityDay[]; modes: NameCount[]; topGames: NameCount[]; countries: NameCount[]; topPlayers: TopPlayer[];
}

/** Admin-only numbers: accounts from the login service, play from the reporting service. */
@Injectable({ providedIn: 'root' })
export class AdminStatsApi {
  private readonly http = inject(HttpClient);

  accounts(days: number): Observable<AccountStatsView> {
    return this.http.get<AccountStatsView>('/api/v1/auth/admin/stats', { params: new HttpParams().set('days', days) });
  }

  activity(days: number): Observable<ActivityStatsView> {
    return this.http.get<ActivityStatsView>('/api/v1/reports/admin/stats', { params: new HttpParams().set('days', days) });
  }
}
