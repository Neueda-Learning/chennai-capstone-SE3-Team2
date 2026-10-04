import { registerLocaleData } from '@angular/common';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import localeEnIn from '@angular/common/locales/en-IN';
import {
  ApplicationConfig,
  DEFAULT_CURRENCY_CODE,
  LOCALE_ID,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';

import { environment } from '../environments/environment';
import { routes } from './app.routes';
import { provideClients } from './core/api/provide-clients';
import { authInterceptor } from './core/http/auth.interceptor';
import { SessionRefresh } from './core/session/session-refresh';

// Money and numbers in lakhs and crores (₹4,29,877.57), as an Indian
// customer reads them. Every number, currency and date pipe follows LOCALE_ID.
registerLocaleData(localeEnIn, 'en-IN');

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    { provide: LOCALE_ID, useValue: 'en-IN' },
    { provide: DEFAULT_CURRENCY_CODE, useValue: 'INR' },
    // Route and query parameters arrive as signal inputs, not as observables.
    provideRouter(routes, withComponentInputBinding()),
    // The one interceptor, registered once: the only place an Authorization header is set.
    provideHttpClient(withFetch(), withInterceptors([authInterceptor])),
    provideClients(environment.api),
    // Renews the access token before it runs out, from the first screen on.
    provideAppInitializer(() => void inject(SessionRefresh)),
  ],
};
