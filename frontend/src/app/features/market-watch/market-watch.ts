import { DecimalPipe } from '@angular/common';
import { Component, DestroyRef, computed, effect, inject, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { InstrumentResponse, Quote } from '../../../generated/extensions';
import { InstrumentCatalog } from '../../core/api/instrument-catalog';
import { LIVE_PRICES_POLICY, watchPrices } from '../../core/market/live-prices';
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

/** Up, down or unchanged by the day's percentage change, for a price the stream carried. */
function trendOfPercent(changePercent: number | null | undefined): Trend {
  if (changePercent === null || changePercent === undefined || changePercent === 0) {
    return 'flat';
  }
  return changePercent > 0 ? 'up' : 'down';
}

/**
 * The market watch: the instruments a customer keeps an eye on, each with its
 * live price and the day's change, and a search to add more. Click a row for
 * its chart; B and S open the order window.
 *
 * Kept on the server by the Sprint 10 watchlists module, up to five watchlists
 * as tabs. A stock's price is the latest the market-data stream carried for
 * it; one the stream has not reached yet, and every fund (a NAV has no
 * stream), is priced from the quotes route. Both re-read every 15 seconds
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
  /** What the stream has not priced: read from the quotes route instead. */
  private readonly unstreamed = computed(() => this.shown().filter((symbol) => this.streamed(symbol) === null));
  protected readonly live = watchPrices(this.unstreamed);

  protected readonly rows = computed(() =>
    this.shown().map((symbol) => {
      const instrument = this.catalog.get(symbol);
      const stream = this.streamed(symbol);
      const quote = stream === null ? this.live.quote(symbol) : undefined;
      return {
        symbol,
        label: this.catalog.label(symbol),
        detail: instrument === undefined ? '' : instrument.type === 'MF' ? 'Mutual fund' : (instrument.exchange ?? instrument.type),
        fund: instrument?.type === 'MF',
        source: stream === null ? 'quote' : 'stream',
        price: stream?.price ?? quote?.price ?? null,
        changePercent: stream === null ? quote?.changePercent : stream.changePercent,
        stale: stream?.stale ?? quote?.stale ?? false,
        trend: stream === null ? trendOf(quote) : trendOfPercent(stream.changePercent),
      };
    }),
  );

  constructor() {
    effect(() => {
      const symbols = this.list.symbols();
      untracked(() => void this.catalog.resolve(symbols));
    });
    const policy = inject(LIVE_PRICES_POLICY);
    const stop = policy.every(() => {
      if (policy.visible()) {
        void this.list.refresh();
      }
    }, policy.intervalMs);
    inject(DestroyRef).onDestroy(stop);
  }

  /** The stream's latest price for an entry, or null before one has arrived. */
  private streamed(symbol: string): { price: number; changePercent: number | null | undefined; stale: boolean } | null {
    const item = this.list.item(symbol);
    return item?.lastPrice === null || item?.lastPrice === undefined
      ? null
      : { price: item.lastPrice, changePercent: item.changePercent, stale: item.stale };
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
