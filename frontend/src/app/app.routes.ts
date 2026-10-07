import { inject } from '@angular/core';
import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { Session } from './core/session/session';

/**
 * Every screen, lazily loaded so each feature is its own chunk.
 *
 * The landing page, sign-in and opening an account are the only routes
 * outside the guard -- a visitor has no login yet for any of them. Everything
 * else is a child of the guarded parent, so a route added later is guarded
 * without anyone remembering to: the default is closed. Anything unknown goes
 * to the dashboard, which the guard covers too.
 *
 * Home (/) depends on who asks: a signed-in customer's home is their
 * dashboard, a visitor's the landing page. Sign-in lands on / by default.
 */
export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: () => (inject(Session).accessToken() ? '/dashboard' : '/landing'),
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
        redirectTo: 'dashboard',
      },
      {
        path: 'dashboard',
        title: 'Dashboard',
        loadComponent: () => import('./features/dashboard/dashboard').then((m) => m.Dashboard),
      },
      {
        path: 'orders',
        title: 'Orders',
        loadComponent: () => import('./features/orders/orders').then((m) => m.Orders),
      },
      {
        path: 'holdings',
        title: 'Holdings',
        loadComponent: () => import('./features/holdings/holdings').then((m) => m.Holdings),
      },
      {
        path: 'funds',
        title: 'Funds',
        loadComponent: () => import('./features/cash/cash').then((m) => m.Cash),
      },
      {
        path: 'trade',
        title: 'Place an order',
        loadComponent: () => import('./features/order-ticket/order-ticket').then((m) => m.OrderTicket),
      },
      {
        // One instrument's price, chart and Buy/Sell. The symbol is its title.
        path: 'instrument/:symbol',
        title: (route) => route.paramMap.get('symbol') ?? 'Instrument',
        loadComponent: () => import('./features/instrument/instrument-page').then((m) => m.InstrumentPage),
      },
      {
        path: 'alerts',
        title: 'Price alerts',
        loadComponent: () => import('./features/alerts/alerts').then((m) => m.Alerts),
      },
      {
        path: 'notifications',
        title: 'Notifications',
        loadComponent: () => import('./features/notifications/notifications').then((m) => m.Notifications),
      },
      {
        path: 'settings',
        title: 'Settings',
        loadComponent: () => import('./features/settings/settings').then((m) => m.Settings),
      },
      {
        path: 'strategies',
        title: 'Strategies',
        loadComponent: () => import('./features/strategies/strategies').then((m) => m.Strategies),
      },
      {
        // On a narrow screen the market watch has no room beside the page: it is a page of its own.
        path: 'market-watch',
        title: 'Watchlist',
        loadComponent: () => import('./features/market-watch/market-watch').then((m) => m.MarketWatch),
      },
      // Before Kite's menu: the stocks and funds dashboards, and Cash.
      { path: 'stocks', redirectTo: 'dashboard' },
      { path: 'mutual-funds', redirectTo: 'dashboard' },
      { path: 'cash', redirectTo: 'funds' },
      { path: '**', redirectTo: 'dashboard' },
    ],
  },
];
