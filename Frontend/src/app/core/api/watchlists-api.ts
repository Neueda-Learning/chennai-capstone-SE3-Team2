import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AlertRequest, AlertsService, PriceAlert, Watchlist, WatchlistsService } from '../../../generated/watchlists';

/** The customer's watchlists and price alerts (Sprint 10), through the generated clients. */
@Injectable({ providedIn: 'root' })
export class WatchlistsApi {
  private readonly watchlists = inject(WatchlistsService);
  private readonly alerts = inject(AlertsService);

  /** GET /api/v1/accounts/{id}/watchlists: in order, each entry with the latest price the stream carried. */
  list(accountId: number): Promise<Watchlist[]> {
    return firstValueFrom(this.watchlists.getWatchlists(accountId));
  }

  create(accountId: number, name: string): Promise<Watchlist> {
    return firstValueFrom(this.watchlists.createWatchlist(accountId, { name }));
  }

  async remove(accountId: number, watchlistId: number): Promise<void> {
    await firstValueFrom(this.watchlists.deleteWatchlist(accountId, watchlistId));
  }

  /** Adding one already there changes nothing. */
  async addItem(accountId: number, watchlistId: number, symbol: string): Promise<void> {
    await firstValueFrom(this.watchlists.addWatchlistItem(accountId, watchlistId, symbol));
  }

  async removeItem(accountId: number, watchlistId: number, symbol: string): Promise<void> {
    await firstValueFrom(this.watchlists.removeWatchlistItem(accountId, watchlistId, symbol));
  }

  /** GET /api/v1/accounts/{id}/alerts: newest first, in every state. */
  alertsOf(accountId: number): Promise<PriceAlert[]> {
    return firstValueFrom(this.alerts.getAlerts(accountId));
  }

  setAlert(accountId: number, request: AlertRequest): Promise<PriceAlert> {
    return firstValueFrom(this.alerts.createAlert(accountId, request));
  }

  async cancelAlert(accountId: number, alertId: number): Promise<void> {
    await firstValueFrom(this.alerts.cancelAlert(accountId, alertId));
  }

  rearmAlert(accountId: number, alertId: number): Promise<PriceAlert> {
    return firstValueFrom(this.alerts.rearmAlert(accountId, alertId));
  }
}
