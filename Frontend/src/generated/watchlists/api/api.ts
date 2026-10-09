export * from './alerts.service';
import { AlertsService } from './alerts.service';
export * from './watchlists.service';
import { WatchlistsService } from './watchlists.service';
export const APIS = [AlertsService, WatchlistsService];
