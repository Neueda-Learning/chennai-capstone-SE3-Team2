import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { provideRouter, withComponentInputBinding } from '@angular/router';

import { environment } from '../environments/environment';
import { routes } from './app.routes';
import { provideClients } from './core/api/provide-clients';
import { authInterceptor } from './core/http/auth.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // Route and query parameters arrive as signal inputs, not as observables.
    provideRouter(routes, withComponentInputBinding()),
    // The one interceptor, registered once: the only place an Authorization header is set.
    provideHttpClient(withFetch(), withInterceptors([authInterceptor])),
    provideClients(environment.api),
  ],
};
