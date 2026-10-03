import { CurrencyPipe } from '@angular/common';
import { Component, effect, inject, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { UserResponse } from '../../../generated/auth';
import { AccountResponse, PositionResponse } from '../../../generated/trade';
import { AuthApi } from '../../core/api/auth-api';
import { TradeApi } from '../../core/api/trade-api';
import { Session } from '../../core/session/session';
import { ErrorMessage } from '../../shared/error-message/error-message';
import { Blotter } from '../blotter/blotter';
import { Holdings } from '../holdings/holdings';

/**
 * Where a signed-in customer lands: who they are, their account, what it
 * holds, and every order on it. The account is the token's; nothing here lets
 * it be changed. When an order settles, the cash and the holdings it moved
 * are read again.
 */
@Component({
  selector: 'app-dashboard',
  imports: [CurrencyPipe, RouterLink, Blotter, Holdings, ErrorMessage],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css',
})
export class Dashboard {
  private readonly tradeApi = inject(TradeApi);
  private readonly authApi = inject(AuthApi);
  protected readonly accountId = inject(Session).accountId;

  protected readonly account = signal<AccountResponse | null>(null);
  protected readonly user = signal<UserResponse | null>(null);
  protected readonly positions = signal<readonly PositionResponse[] | null>(null);
  protected readonly error = signal<unknown>(null);

  constructor() {
    effect(() => {
      const accountId = this.accountId();
      if (accountId !== null) {
        untracked(() => void this.load(accountId));
      }
    });
  }

  private async load(accountId: number): Promise<void> {
    try {
      const [account, user, positions] = await Promise.all([
        this.tradeApi.account(accountId),
        this.authApi.currentUser(),
        this.tradeApi.positions(accountId),
      ]);
      this.account.set(account);
      this.user.set(user);
      this.positions.set(positions);
    } catch (failure) {
      this.error.set(failure);
    }
  }

  /** An order settled: what it moved is read again. Who is signed in has not changed. */
  protected async orderSettled(): Promise<void> {
    const accountId = this.accountId();
    if (accountId === null) {
      return;
    }
    try {
      const [account, positions] = await Promise.all([this.tradeApi.account(accountId), this.tradeApi.positions(accountId)]);
      this.account.set(account);
      this.positions.set(positions);
    } catch (failure) {
      this.error.set(failure);
    }
  }
}
