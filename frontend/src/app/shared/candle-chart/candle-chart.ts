import {
  Component,
  DestroyRef,
  ElementRef,
  InjectionToken,
  Injector,
  afterNextRender,
  effect,
  inject,
  input,
  viewChild,
} from '@angular/core';
import { Candle } from '../../../generated/extensions';

export type ChartLibrary = Pick<typeof import('lightweight-charts'), 'createChart' | 'CandlestickSeries' | 'HistogramSeries'>;

/**
 * Loads TradingView's lightweight-charts (Apache-2.0) when a chart is first
 * drawn, so it is a chunk of its own rather than part of every page. The
 * specs put a fake in its place.
 */
export const CHART_LIBRARY = new InjectionToken<() => Promise<ChartLibrary>>('CHART_LIBRARY', {
  providedIn: 'root',
  factory: () => () => import('lightweight-charts'),
});

export interface ChartData {
  readonly candles: ReadonlyArray<{ time: string; open: number; high: number; low: number; close: number }>;
  readonly volumes: ReadonlyArray<{ time: string; value: number; color: string }>;
}

/** Candles as the library takes them; a day's volume bar is coloured as its candle closed. */
export function toChartData(candles: readonly Candle[], up: string, down: string): ChartData {
  return {
    candles: candles.map((c) => ({ time: c.date, open: c.open, high: c.high, low: c.low, close: c.close })),
    volumes: candles
      .filter((c) => c.volume !== null && c.volume !== undefined)
      .map((c) => ({ time: c.date, value: c.volume!, color: c.close >= c.open ? up : down })),
  };
}

/** A daily candlestick chart with volume beneath, in the page's own colours. */
@Component({
  selector: 'app-candle-chart',
  template: `<div #host class="chart" data-testid="candle-chart" role="img" [attr.aria-label]="label()"></div>`,
  styles: `
    :host {
      display: block;
    }
    .chart {
      width: 100%;
      height: 22rem;
    }
  `,
})
export class CandleChart {
  readonly candles = input.required<readonly Candle[]>();
  /** What a screen reader hears in place of the picture. */
  readonly label = input('Price chart');

  private readonly host = viewChild.required<ElementRef<HTMLElement>>('host');
  private readonly loadLibrary = inject(CHART_LIBRARY);
  private readonly injector = inject(Injector);
  private chart: { remove(): void } | null = null;
  private destroyed = false;

  constructor() {
    afterNextRender(() => void this.draw());
    inject(DestroyRef).onDestroy(() => {
      this.destroyed = true;
      this.chart?.remove();
    });
  }

  private async draw(): Promise<void> {
    const library = await this.loadLibrary();
    if (this.destroyed) {
      return;
    }
    const element = this.host().nativeElement;
    const style = getComputedStyle(element);
    const colour = (name: string, fallback: string) => style.getPropertyValue(name).trim() || fallback;
    const up = colour('--success', '#5ee2a0');
    const down = colour('--danger', '#ff6b6b');
    const lines = colour('--border', '#26324b');

    const chart = library.createChart(element, {
      autoSize: true,
      layout: { background: { color: 'transparent' }, textColor: colour('--muted', '#8fa2c6'), fontFamily: style.fontFamily },
      grid: { vertLines: { color: lines }, horzLines: { color: lines } },
      rightPriceScale: { borderColor: lines },
      timeScale: { borderColor: lines },
    });
    this.chart = chart;
    const prices = chart.addSeries(library.CandlestickSeries, {
      upColor: up,
      downColor: down,
      wickUpColor: up,
      wickDownColor: down,
      borderVisible: false,
    });
    const volume = chart.addSeries(library.HistogramSeries, { priceFormat: { type: 'volume' }, priceScaleId: '' });
    volume.priceScale().applyOptions({ scaleMargins: { top: 0.8, bottom: 0 } });

    effect(
      () => {
        const data = toChartData(this.candles(), up, down);
        prices.setData([...data.candles]);
        volume.setData([...data.volumes]);
        chart.timeScale().fitContent();
      },
      { injector: this.injector },
    );
  }
}
