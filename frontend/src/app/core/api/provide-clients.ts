import { EnvironmentProviders, makeEnvironmentProviders } from '@angular/core';
import { provideApi as provideAuthApi } from '../../../generated/auth';
import { provideApi as provideExtensionsApi } from '../../../generated/extensions';
import { provideApi as provideNotificationsApi } from '../../../generated/notifications';
import { provideApi as providePreferencesApi } from '../../../generated/preferences';
import { provideApi as providePortfolioApi } from '../../../generated/portfolio';
import { provideApi as provideTradeApi } from '../../../generated/trade';
import { provideApi as provideWatchlistsApi } from '../../../generated/watchlists';
import { API_CONFIG, ApiConfig } from '../config/api-config';

/**
 * Points each generated client at its service. The clients themselves are
 * machine output and never edited; this is where they meet our configuration.
 */
export function provideClients(config: ApiConfig): EnvironmentProviders {
  return makeEnvironmentProviders([
    { provide: API_CONFIG, useValue: config },
    provideTradeApi(config.tradeApiUrl),
    provideAuthApi(config.authApiUrl),
    // Our own routes on the Trade REST API: same server, its own description.
    provideExtensionsApi(config.tradeApiUrl),
    // The Sprint 10 modules: routes on the Trade REST API, one description each.
    providePreferencesApi(config.tradeApiUrl),
    provideNotificationsApi(config.tradeApiUrl),
    provideWatchlistsApi(config.tradeApiUrl),
    // The Sprint 10 portfolio module: contracts/portfolio-api.yaml, served by the Trade REST API.
    providePortfolioApi(config.tradeApiUrl),
  ]);
}
