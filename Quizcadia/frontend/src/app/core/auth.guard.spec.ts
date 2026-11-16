import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, provideRouter, RouterStateSnapshot, UrlTree } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { AuthService } from './auth.service';
import { welcomeGuard } from './auth.guard';

function run(role: string | null): boolean | UrlTree {
  const user = signal(role ? { userId: 1, userName: 'A', email: '', role } : null);
  TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: AuthService, useValue: { user } }] });
  return TestBed.runInInjectionContext(() =>
    welcomeGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot)) as boolean | UrlTree;
}

describe('welcomeGuard', () => {
  it('sends account holders home', () => {
    expect(String(run('USER'))).toBe('/');
    TestBed.resetTestingModule();
    expect(String(run('ADMIN'))).toBe('/');
  });

  it('lets signed-out visitors and guests in', () => {
    expect(run(null)).toBe(true);
    TestBed.resetTestingModule();
    expect(run('GUEST')).toBe(true);
  });
});
