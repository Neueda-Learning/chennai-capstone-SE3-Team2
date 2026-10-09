import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { CandleSeries, MarketDataService, Quote } from '../../../generated/extensions';

export type ChartRange = '1M' | '3M' | '6M' | '1Y' | '5Y';
export const CHART_RANGES: readonly ChartRange[] = ['1M', '3M', '6M', '1Y', '5Y'];

/** Prices and charts, through the generated extensions client. Never Fauxnance directly. */
@Injectable({ providedIn: 'root' })
export class MarketDataApi {
  private readonly market = inject(MarketDataService);

  /** GET /api/v1/quotes: the latest price of each listed symbol, at most 50. */
  quotes(symbols: readonly string[]): Promise<Quote[]> {
    return firstValueFrom(this.market.getQuotes([...symbols]));
  }

  /** GET /api/v1/instruments/{symbol}/candles: daily candles, oldest first. */
  candles(symbol: string, range: ChartRange): Promise<CandleSeries> {
    return firstValueFrom(this.market.getCandles(symbol, range));
  }
}
