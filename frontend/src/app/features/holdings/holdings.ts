import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { PositionResponse } from '../../../generated/trade';
import { InstrumentCatalog, Segment } from '../../core/api/instrument-catalog';
import { TradeApi } from '../../core/api/trade-api';
import { watchPrices } from '../../core/market/live-prices';
import { value } from '../../core/portfolio/valuation';
import { Session } from '../../core/session/session';
import { ErrorMessage } from '../../shared/error-message/error-message';

export type HoldingsFilter = 'all' | Segment;

/**
 * Kite's Holdings: everything the account holds at its live price -- current
 * value, P&L against cost, the day's change -- and the totals. Worked out on
 * screen from live prices (core/portfolio/valuation.ts) until the Sprint 10
 * portfolio module serves them. Each still-tradable holding can be bought
 * more of or sold; a delisted one has nowhere to trade.
 */
@Component({
  selector: 'app-holdings',
  imports: [CurrencyPipe, DecimalPipe, ErrorMessage, RouterLink],
  templateUrl: './holdings.html',
  styleUrl: './holdings.css',
})
export class Holdings {
  private readonly tradeApi = inject(TradeApi);
  /** A stock by its ticker, a fund by its name, and which is which. */
  protected readonly catalog = inject(InstrumentCatalog);
  private readonly accountId = inject(Session).accountId;

  protected readonly positions = signal<readonly PositionResponse[] | null>(null);
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
    return value(shown, new Map(shown.map((p) => [p.symbol, this.live.quote(p.symbol)])));
  });

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
      const positions = await this.tradeApi.positions(accountId);
      this.positions.set(positions);
      this.error.set(null);
      void this.catalog.resolve(positions.map((p) => p.symbol));
    } catch (failure) {
      this.error.set(failure);
    }
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
