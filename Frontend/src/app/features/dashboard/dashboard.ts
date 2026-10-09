import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { UserResponse } from '../../../generated/auth';
import { PortfolioSummary, PricedPosition } from '../../../generated/portfolio';
import { AccountResponse, OrderHistoryEntry, OrderStatus } from '../../../generated/trade';
import { AuthApi } from '../../core/api/auth-api';
import { InstrumentCatalog } from '../../core/api/instrument-catalog';
import { PortfolioApi, isPricingUnavailable } from '../../core/api/portfolio-api';
import { TradeApi } from '../../core/api/trade-api';
import { watchPrices } from '../../core/market/live-prices';
import { fromPortfolio, unpriced } from '../../core/portfolio/valuation';
import { Session } from '../../core/session/session';
import { ErrorMessage } from '../../shared/error-message/error-message';
import { StatusBadge } from '../../shared/status-badge/status-badge';

/** How many of the latest orders the dashboard lists. */
const RECENT = 5;

/**
 * Where a signed-in customer lands, as on Kite: a greeting; the cash
 * available; the holdings at their live value and P&L; what is still working
 * and the latest orders. Each card leads to its page.
 */
@Component({
  selector: 'app-dashboard',
  imports: [CurrencyPipe, DatePipe, DecimalPipe, ErrorMessage, RouterLink, StatusBadge],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css',
})
export class Dashboard {
  private readonly tradeApi = inject(TradeApi);
  private readonly portfolioApi = inject(PortfolioApi);
  private readonly authApi = inject(AuthApi);
  protected readonly catalog = inject(InstrumentCatalog);
  protected readonly accountId = inject(Session).accountId;

  protected readonly account = signal<AccountResponse | null>(null);
  protected readonly user = signal<UserResponse | null>(null);
  protected readonly positions = signal<readonly PricedPosition[] | null>(null);
  /** The portfolio module's summary; null while unread, or when nothing could be priced. */
  protected readonly summary = signal<PortfolioSummary | null>(null);
  protected readonly orders = signal<readonly OrderHistoryEntry[] | null>(null);
  protected readonly error = signal<unknown>(null);

  protected readonly live = watchPrices(() => (this.positions() ?? []).map((p) => p.symbol));
  protected readonly holdings = computed(() => {
    const positions = this.positions() ?? [];
    return fromPortfolio(positions, new Map(positions.map((p) => [p.symbol, this.live.quote(p.symbol)])));
  });

  /** The first name, as Kite greets: "Hi, Rohan". */
  protected readonly firstName = computed(() => this.account()?.holderName.split(/\s+/)[0] ?? '');
  protected readonly open = computed(() => (this.orders() ?? []).filter((o) => o.status === OrderStatus.New).length);
  protected readonly recent = computed(() =>
    [...(this.orders() ?? [])].sort((a, b) => Date.parse(b.createdOn) - Date.parse(a.createdOn)).slice(0, RECENT),
  );

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
      const [account, user, orders] = await Promise.all([
        this.tradeApi.account(accountId),
        this.authApi.currentUser(),
        this.tradeApi.orderHistory(accountId),
      ]);
      this.account.set(account);
      this.user.set(user);
      this.orders.set(orders);
      void this.catalog.resolve(orders.map((o) => o.symbol));
    } catch (failure) {
      this.error.set(failure);
    }
    await this.loadHoldings(accountId);
  }

  /** From the Sprint 10 portfolio module; at cost, unpriced, when it answers MKT-503. */
  private async loadHoldings(accountId: number): Promise<void> {
    try {
      const [positions, summary] = await Promise.all([
        this.portfolioApi.positions(accountId),
        this.portfolioApi.summary(accountId),
      ]);
      this.positions.set(positions);
      this.summary.set(summary);
    } catch (failure) {
      if (!isPricingUnavailable(failure)) {
        this.error.set(failure);
        return;
      }
      try {
        this.positions.set(unpriced(await this.tradeApi.positions(accountId)));
      } catch (fallback) {
        this.error.set(fallback);
        return;
      }
    }
    void this.catalog.resolve((this.positions() ?? []).map((p) => p.symbol));
  }

  protected sign(amount: number | null): 'up' | 'down' | '' {
    if (amount === null || amount === 0) {
      return '';
    }
    return amount > 0 ? 'up' : 'down';
  }
}
