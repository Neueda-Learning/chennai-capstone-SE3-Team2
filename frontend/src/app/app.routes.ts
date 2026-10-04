import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';

/**
 * Every screen, lazily loaded so each feature is its own chunk.
 *
 * Sign-in and opening an account are the only routes outside the guard -- a
 * visitor has no login yet for either. Everything else is a child of the
 * guarded parent, so a route added later is guarded without anyone
 * remembering to: the default is closed. Anything unknown goes home, which
 * the guard covers too.
 */
export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'landing',
  },
  {
    path: 'landing',
    title: 'YELLOW Trading Platform',
    loadComponent: () => import('./features/landing/landing').then((m) => m.Landing),
  },
  {
    path: 'sign-in',
    title: 'Sign in',
    loadComponent: () => import('./features/sign-in/sign-in').then((m) => m.SignIn),
  },
  {
    path: 'apply',
    title: 'Open an account',
    loadComponent: () => import('./features/apply/apply').then((m) => m.Apply),
  },
  {
    path: '',
    canActivateChild: [authGuard],
    children: [
      {
        path: '',
        pathMatch: 'full',
        redirectTo: 'stocks',
      },
      {
        path: 'stocks',
        title: 'Stocks dashboard',
        loadComponent: () => import('./features/stocks-dashboard/stocks-dashboard').then((m) => m.StocksDashboard),
      },
      {
        path: 'mutual-funds',
        title: 'Mutual funds dashboard',
        loadComponent: () =>
          import('./features/mutual-funds-dashboard/mutual-funds-dashboard').then((m) => m.MutualFundsDashboard),
      },
      {
        path: 'trade',
        title: 'Place an order',
        loadComponent: () => import('./features/order-ticket/order-ticket').then((m) => m.OrderTicket),
      },
      {
        path: 'cash',
        title: 'Cash',
        loadComponent: () => import('./features/cash/cash').then((m) => m.Cash),
      },
      { path: '**', redirectTo: 'stocks' },
    ],
  },
];
