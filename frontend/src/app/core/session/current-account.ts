import { Injectable, effect, inject, signal, untracked } from '@angular/core';
import { AccountResponse } from '../../../generated/trade';
import { TradeApi } from '../api/trade-api';
import { Session } from './session';

/**
 * The signed-in customer's account as they know it: their name and their
 * account reference (ACC-000003). The header and the order ticket show these
 * rather than the token's numeric account key, which is the database's and
 * means nothing to a customer.
 *
 * Read once per session, and again when a screen that moved its cash asks
 * (refresh): the order window shows the cash available.
 */
@Injectable({ providedIn: 'root' })
export class CurrentAccount {
  private readonly tradeApi = inject(TradeApi);
  private readonly session = inject(Session);

  /** null while signed out, and until the first read answers. */
  readonly account = signal<AccountResponse | null>(null);

  constructor() {
    effect(() => {
      const accountId = this.session.accountId();
      untracked(() => {
        this.account.set(null);
        if (accountId !== null) {
          void this.read(accountId);
        }
      });
    });
  }

  /** Reads the account again: an order or a transfer has moved its cash. */
  refresh(): Promise<void> {
    const accountId = this.session.accountId();
    return accountId === null ? Promise.resolve() : this.read(accountId);
  }

  private async read(accountId: number): Promise<void> {
    try {
      const account = await this.tradeApi.account(accountId);
      if (this.session.accountId() === accountId) {
        this.account.set(account);
      }
    } catch {
      // Nothing to show is better than a wrong account: the header stays blank.
    }
  }
}
