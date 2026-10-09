import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, DestroyRef, InjectionToken, effect, inject, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { PriceAlert } from '../../../generated/watchlists';
import { WatchlistsApi } from '../../core/api/watchlists-api';
import { Session } from '../../core/session/session';
import { ErrorMessage } from '../../shared/error-message/error-message';

/** How often the open page reads the alerts again, to show one firing. */
export const ALERTS_POLL_MS = new InjectionToken<number>('ALERTS_POLL_MS', { factory: () => 15_000 });

/**
 * The customer's price alerts (Sprint 10), newest first, in every state: one
 * waiting, one that has fired and what it sent, one cancelled. An alert fires
 * once; re-arming it waits for the next crossing (decision log 0007).
 */
@Component({
  selector: 'app-alerts',
  imports: [CurrencyPipe, DatePipe, ErrorMessage, RouterLink],
  templateUrl: './alerts.html',
  styleUrl: './alerts.css',
})
export class Alerts {
  private readonly api = inject(WatchlistsApi);
  private readonly accountId = inject(Session).accountId;

  protected readonly alerts = signal<PriceAlert[] | null>(null);
  protected readonly error = signal<unknown>(null);
  protected readonly busy = signal<number | null>(null);

  constructor() {
    effect(() => {
      const accountId = this.accountId();
      if (accountId !== null) {
        untracked(() => void this.load(accountId));
      }
    });
    const poll = setInterval(() => {
      const accountId = this.accountId();
      if (accountId !== null && this.busy() === null) {
        void this.load(accountId);
      }
    }, inject(ALERTS_POLL_MS));
    inject(DestroyRef).onDestroy(() => clearInterval(poll));
  }

  private async load(accountId: number): Promise<void> {
    try {
      this.alerts.set(await this.api.alertsOf(accountId));
    } catch (failure) {
      this.error.set(failure);
    }
  }

  protected condition(alert: PriceAlert): string {
    return alert.direction === 'ABOVE' ? 'Rises to' : 'Falls to';
  }

  async cancel(alert: PriceAlert): Promise<void> {
    await this.act(alert, (accountId) => this.api.cancelAlert(accountId, alert.id));
  }

  async rearm(alert: PriceAlert): Promise<void> {
    await this.act(alert, (accountId) => this.api.rearmAlert(accountId, alert.id));
  }

  private async act(alert: PriceAlert, action: (accountId: number) => Promise<unknown>): Promise<void> {
    const accountId = this.accountId();
    if (accountId === null) {
      return;
    }
    this.busy.set(alert.id);
    this.error.set(null);
    try {
      await action(accountId);
      await this.load(accountId);
    } catch (failure) {
      this.error.set(failure);
    } finally {
      this.busy.set(null);
    }
  }
}
