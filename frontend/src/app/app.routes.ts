import { Routes } from '@angular/router';

/**
 * Every screen, lazily loaded so each feature is its own chunk. Anything
 * unknown goes home rather than to a blank page.
 */
export const routes: Routes = [
  {
    path: '',
    title: 'Dashboard',
    loadComponent: () => import('./features/dashboard/dashboard').then((m) => m.Dashboard),
  },
  { path: '**', redirectTo: '' },
];
