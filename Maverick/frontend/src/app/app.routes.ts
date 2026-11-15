import { Routes } from '@angular/router';
import { adminGuard, playerGuard } from './core/auth.guard';

export const routes: Routes = [
  { path: 'welcome', title: 'Welcome · Quiz King', loadComponent: () => import('./pages/welcome').then((m) => m.Welcome) },
  { path: '', title: 'Games · Quiz King', canActivate: [playerGuard], loadComponent: () => import('./pages/home').then((m) => m.Home) },
  { path: 'play/:gameId', title: 'Playing · Quiz King', canActivate: [playerGuard], loadComponent: () => import('./pages/play').then((m) => m.Play) },
  { path: 'result/:sessionId', title: 'Results · Quiz King', canActivate: [playerGuard], loadComponent: () => import('./pages/result').then((m) => m.Result) },
  { path: 'adaptive/:gameId', title: 'Adaptive · Quiz King', canActivate: [playerGuard], loadComponent: () => import('./pages/adaptive').then((m) => m.Adaptive) },
  { path: 'multiplayer/:gameId', title: 'Lobby · Quiz King', canActivate: [playerGuard], loadComponent: () => import('./pages/lobby').then((m) => m.Lobby) },
  { path: 'match/:matchId', title: 'Match · Quiz King', canActivate: [playerGuard], loadComponent: () => import('./pages/match').then((m) => m.MatchPage) },
  { path: 'me', title: 'My stats · Quiz King', canActivate: [playerGuard], loadComponent: () => import('./pages/profile').then((m) => m.Profile) },
  { path: 'admin', title: 'Create a game · Quiz King', canActivate: [playerGuard, adminGuard], loadComponent: () => import('./pages/admin').then((m) => m.Admin) },
  { path: '**', redirectTo: '' },
];
