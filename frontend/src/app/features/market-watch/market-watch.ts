import { DecimalPipe } from '@angular/common';
import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { InstrumentResponse, Quote } from '../../../generated/extensions';
import { InstrumentCatalog } from '../../core/api/instrument-catalog';
import { watchPrices } from '../../core/market/live-prices';
import { MARKET_WATCH_LIMIT, MarketWatchList } from '../../core/market/market-watch-list';
import { ErrorMessage } from '../../shared/error-message/error-message';
import { InstrumentSearch } from '../../shared/instrument-search/instrument-search';

export type Trend = 'up' | 'down' | 'flat';

/** Up, down or unchanged on the day. A fund's NAV has no day change here. */
export function trendOf(quote: Quote | undefined): Trend {
  const change = quote?.change;
  if (change === null || change === undefined || change === 0) {
    return 'flat';
  }
  return change > 0 ? 'up' : 'down';
}

/**
 * The market watch: the instruments a customer keeps an eye on, each with its
 * live price and the day's change, and a search to add more. Click a row for
 * its chart; B and S open the order window. Prices re-read every 15 seconds
 * while the tab is visible.
 */
@Component({
  selector: 'app-market-watch',
  imports: [DecimalPipe, RouterLink, ErrorMessage, InstrumentSearch],
  templateUrl: './market-watch.html',
  styleUrl: './market-watch.css',
})
export class MarketWatch {
  protected readonly list = inject(MarketWatchList);
  protected readonly catalog = inject(InstrumentCatalog);
  protected readonly limit = MARKET_WATCH_LIMIT;
  protected readonly notice = signal<string | null>(null);

  /** The list, less any symbol nobody lists any more. */
  private readonly shown = computed(() => this.list.symbols().filter((symbol) => !this.catalog.isMissing(symbol)));
  protected readonly live = watchPrices(this.shown);

  protected readonly rows = computed(() =>
    this.shown().map((symbol) => {
      const instrument = this.catalog.get(symbol);
      const quote = this.live.quote(symbol);
      return {
        symbol,
        label: this.catalog.label(symbol),
        detail: instrument === undefined ? '' : instrument.type === 'MF' ? 'Mutual fund' : (instrument.exchange ?? instrument.type),
        fund: instrument?.type === 'MF',
        quote,
        trend: trendOf(quote),
      };
    }),
  );

  constructor() {
    effect(() => {
      const symbols = this.list.symbols();
      untracked(() => void this.catalog.resolve(symbols));
    });
  }

  protected add(instrument: InstrumentResponse): void {
    const label = this.catalog.label(instrument.symbol);
    if (this.list.has(instrument.symbol)) {
      this.notice.set(`${label} is already in your market watch.`);
    } else if (!this.list.add(instrument.symbol)) {
      this.notice.set(`Your market watch is full: ${this.limit} instruments. Remove one to add another.`);
    } else {
      this.notice.set(null);
    }
  }

  protected remove(symbol: string): void {
    this.list.remove(symbol);
    this.notice.set(null);
  }
}
