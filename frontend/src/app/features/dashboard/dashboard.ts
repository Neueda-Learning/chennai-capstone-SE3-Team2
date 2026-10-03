import { CurrencyPipe } from '@angular/common';
import { Component, effect, inject, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { UserResponse } from '../../../generated/auth';
import { AccountResponse } from '../../../generated/trade';
import { AuthApi } from '../../core/api/auth-api';
import { TradeApi } from '../../core/api/trade-api';
import { Session } from '../../core/session/session';
import { ErrorMessage } from '../../shared/error-message/error-message';
import { Blotter } from '../blotter/blotter';

/**
 * Where a signed-in customer lands: who they are, their account, and every
 * order on it. The account is the token's; nothing here lets it be changed.
 */
@Component({
  selector: 'app-dashboard',
  imports: [CurrencyPipe, RouterLink, Blotter, ErrorMessage],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css',
})
export class Dashboard {
  private readonly tradeApi = inject(TradeApi);
  private readonly authApi = inject(AuthApi);
  protected readonly accountId = inject(Session).accountId;

  protected readonly account = signal<AccountResponse | null>(null);
  protected readonly user = signal<UserResponse | null>(null);
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
      const [account, user] = await Promise.all([this.tradeApi.account(accountId), this.authApi.currentUser()]);
      this.account.set(account);
      this.user.set(user);
    } catch (failure) {
      this.error.set(failure);
    }
  }
}
