import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/** Lets signed-in players (guests too) in; anyone else goes to /welcome and comes back here after signing in. */
export const playerGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  return auth.isLoggedIn() ? true : inject(Router).createUrlTree(['/welcome'], { queryParams: { next: state.url } });
};

/** Lets admins in; anyone else goes to the games page. */
export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.isAdmin() ? true : inject(Router).createUrlTree(['/']);
};

/** The welcome page is for signing in or up: someone with an account already goes home (guests may still upgrade). */
export const welcomeGuard: CanActivateFn = () => {
  const role = inject(AuthService).user()?.role;
  return role === 'USER' || role === 'ADMIN' ? inject(Router).createUrlTree(['/']) : true;
};
