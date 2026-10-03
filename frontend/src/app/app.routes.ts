import { Routes } from '@angular/router';

/**
 * Every screen, lazily loaded so each feature is its own chunk. Anything
 * unknown goes home rather than to a blank page.
 */
export const routes: Routes = [
  {
    path: 'sign-in',
    title: 'Sign in',
    loadComponent: () => import('./features/sign-in/sign-in').then((m) => m.SignIn),
  },
  {
    path: '',
    title: 'Dashboard',
    loadComponent: () => import('./features/dashboard/dashboard').then((m) => m.Dashboard),
  },
  { path: '**', redirectTo: '' },
];
