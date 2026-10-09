import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { Component, DestroyRef, computed, effect, inject, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { PortfolioSummary, PricedPosition } from '../../../generated/portfolio';
import { InstrumentCatalog, Segment } from '../../core/api/instrument-catalog';
import { PortfolioApi, isPricingUnavailable } from '../../core/api/portfolio-api';
import { TradeApi } from '../../core/api/trade-api';
import { LIVE_PRICES_POLICY, watchPrices } from '../../core/market/live-prices';
import { fromPortfolio, unpriced } from '../../core/portfolio/valuation';
import { Session } from '../../core/session/session';
import { ErrorMessage } from '../../shared/error-message/error-message';

export type HoldingsFilter = 'all' | Segment;

/**
 * Kite's Holdings: everything the account holds at its live price -- current
 * value, P&L against cost, the day's change -- and the totals, with the P&L
 * already realised by sales. Priced by the Sprint 10 portfolio module
 * (GET /api/v1/portfolio/{id} and /positions); the day's change, which the
 * contract does not carry, from the quotes. When nothing can be priced
 * (MKT-503) the holdings still show, at cost, unpriced. Each still-tradable
 * holding can be bought more of or sold; a delisted one has nowhere to trade.
 */
@Component({
  selector: 'app-holdings',
  imports: [CurrencyPipe, DecimalPipe, ErrorMessage, RouterLink],
  templateUrl: './holdings.html',
  styleUrl: './holdings.css',
})
export class Holdings {
  private readonly tradeApi = inject(TradeApi);
  private readonly portfolioApi = inject(PortfolioApi);
  /** A stock by its ticker, a fund by its name, and which is which. */
  protected readonly catalog = inject(InstrumentCatalog);
  private readonly accountId = inject(Session).accountId;

  protected readonly positions = signal<readonly PricedPosition[] | null>(null);
  protected readonly summary = signal<PortfolioSummary | null>(null);
  /** The portfolio routes answered MKT-503: the holdings show at cost, unpriced. */
  protected readonly pricingUnavailable = signal(false);
  protected readonly error = signal<unknown>(null);
  protected readonly filter = signal<HoldingsFilter>('all');
  protected readonly filters: ReadonlyArray<{ readonly value: HoldingsFilter; readonly label: string }> = [
    { value: 'all', label: 'All' },
    { value: 'stocks', label: 'Stocks' },
    { value: 'mutual-funds', label: 'Mutual funds' },
  ];

  private readonly shown = computed(() => {
    const filter = this.filter();
    const positions = this.positions() ?? [];
    return filter === 'all' ? positions : positions.filter((p) => this.catalog.segmentOf(p.symbol) === filter);
  });
  protected readonly live = watchPrices(() => (this.positions() ?? []).map((p) => p.symbol));
  protected readonly valued = computed(() => {
    const shown = this.shown();
    return fromPortfolio(shown, new Map(shown.map((p) => [p.symbol, this.live.quote(p.symbol)])));
  });

  constructor() {
    effect(() => {
      const accountId = this.accountId();
      if (accountId !== null) {
        untracked(() => void this.load(accountId));
      }
    });
    const policy = inject(LIVE_PRICES_POLICY);
    const stop = policy.every(() => {
      const accountId = this.accountId();
      if (accountId !== null && policy.visible()) {
        void this.load(accountId);
      }
    }, policy.intervalMs);
    inject(DestroyRef).onDestroy(stop);
  }

  private async load(accountId: number): Promise<void> {
    try {
      const [positions, summary] = await Promise.all([
        this.portfolioApi.positions(accountId),
        this.portfolioApi.summary(accountId),
      ]);
      this.show(positions, summary, false);
    } catch (failure) {
      if (!isPricingUnavailable(failure)) {
        this.error.set(failure);
        return;
      }
      try {
        this.show(unpriced(await this.tradeApi.positions(accountId)), null, true);
      } catch (fallback) {
        this.error.set(fallback);
      }
    }
  }

  private show(positions: PricedPosition[], summary: PortfolioSummary | null, pricingUnavailable: boolean): void {
    this.positions.set(positions);
    this.summary.set(summary);
    this.pricingUnavailable.set(pricingUnavailable);
    this.error.set(null);
    void this.catalog.resolve(positions.map((p) => p.symbol));
  }

  protected isFund(symbol: string): boolean {
    return this.catalog.segmentOf(symbol) === 'mutual-funds';
  }

  /** Up, down or level: the colour a figure is shown in. */
  protected sign(amount: number | null): 'up' | 'down' | '' {
    if (amount === null || amount === 0) {
      return '';
    }
    return amount > 0 ? 'up' : 'down';
  }
}
