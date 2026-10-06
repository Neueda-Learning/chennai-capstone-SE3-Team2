import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CandleSeries } from '../../../generated/extensions';
import { InstrumentCatalog } from '../../core/api/instrument-catalog';
import { CHART_RANGES, ChartRange, MarketDataApi } from '../../core/api/market-data-api';
import { watchPrices } from '../../core/market/live-prices';
import { MarketWatchList } from '../../core/market/market-watch-list';
import { CandleChart } from '../../shared/candle-chart/candle-chart';
import { ErrorMessage } from '../../shared/error-message/error-message';
import { trendOf } from '../market-watch/market-watch';

const RANGE_NAMES: Readonly<Record<ChartRange, string>> = {
  '1M': 'one month',
  '3M': 'three months',
  '6M': 'six months',
  '1Y': 'one year',
  '5Y': 'five years',
};

/**
 * One instrument: its live price and the day's change, a daily chart over a
 * chosen range, the day's figures and the 52-week range, and Buy and Sell.
 * A fund shows its NAV; there is no fund chart yet.
 */
@Component({
  selector: 'app-instrument-page',
  imports: [CandleChart, CurrencyPipe, DatePipe, DecimalPipe, ErrorMessage, RouterLink],
  templateUrl: './instrument-page.html',
  styleUrl: './instrument-page.css',
})
export class InstrumentPage {
  /** From the route: /instrument/:symbol. */
  readonly symbol = input.required<string>();

  private readonly catalog = inject(InstrumentCatalog);
  private readonly market = inject(MarketDataApi);
  protected readonly watchList = inject(MarketWatchList);
  protected readonly live = watchPrices(() => [this.symbol()]);

  protected readonly instrument = computed(() => this.catalog.get(this.symbol()));
  protected readonly missing = computed(() => this.catalog.isMissing(this.symbol()));
  protected readonly fund = computed(() => this.instrument()?.type === 'MF');
  protected readonly quote = computed(() => this.live.quote(this.symbol()));
  protected readonly trend = computed(() => trendOf(this.quote()));
  protected readonly watched = computed(() => this.watchList.symbols().includes(this.symbol()));

  protected readonly ranges = CHART_RANGES;
  protected readonly range = signal<ChartRange>('6M');
  protected readonly rangeName = computed(() => RANGE_NAMES[this.range()]);
  protected readonly series = signal<CandleSeries | null>(null);
  protected readonly chartError = signal<unknown>(null);
  /** A year of candles, for the 52-week range whatever range the chart shows. */
  private readonly year = signal<CandleSeries | null>(null);

  /** The latest day's candle: its open, high, low and volume. */
  protected readonly lastDay = computed(() => this.series()?.candles.at(-1) ?? this.year()?.candles.at(-1) ?? null);
  protected readonly yearRange = computed(() => {
    const candles = this.year()?.candles ?? [];
    if (candles.length === 0) {
      return null;
    }
    return { high: Math.max(...candles.map((c) => c.high)), low: Math.min(...candles.map((c) => c.low)) };
  });

  /** Only the latest range asked for is drawn, however the answers arrive. */
  private chartRequest = 0;

  constructor() {
    effect(() => {
      const symbol = this.symbol();
      untracked(() => void this.catalog.resolve([symbol]));
    });
    effect(() => {
      const instrument = this.instrument();
      const range = this.range();
      if (instrument !== undefined && instrument.type !== 'MF') {
        untracked(() => void this.loadChart(instrument.symbol, range));
      }
    });
    effect(() => {
      const instrument = this.instrument();
      if (instrument !== undefined && instrument.type !== 'MF') {
        untracked(() => void this.loadYear(instrument.symbol));
      }
    });
  }

  protected choose(range: ChartRange): void {
    this.range.set(range);
  }

  protected toggleWatch(): void {
    if (this.watched()) {
      this.watchList.remove(this.symbol());
    } else {
      this.watchList.add(this.symbol());
    }
  }

  private async loadChart(symbol: string, range: ChartRange): Promise<void> {
    const mine = ++this.chartRequest;
    try {
      const series = await this.market.candles(symbol, range);
      if (mine === this.chartRequest) {
        this.series.set(series);
        this.chartError.set(null);
      }
    } catch (failure) {
      if (mine === this.chartRequest) {
        this.series.set(null);
        this.chartError.set(failure);
      }
    }
  }

  private async loadYear(symbol: string): Promise<void> {
    try {
      this.year.set(await this.market.candles(symbol, '1Y'));
    } catch {
      // The 52-week range is left blank; the chart says what went wrong.
      this.year.set(null);
    }
  }
}
