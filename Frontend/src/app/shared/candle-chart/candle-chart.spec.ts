import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Candle } from '../../../generated/extensions';
import { CHART_LIBRARY, CandleChart, ChartLibrary, toChartData } from './candle-chart';

const DAYS: Candle[] = [
  { date: '2026-10-05', open: 100, high: 110, low: 95, close: 108, volume: 5000 },
  { date: '2026-10-06', open: 108, high: 109, low: 99, close: 101, volume: null },
];

/** Records what the chart was asked to draw. */
function fakeLibrary() {
  const calls = { created: 0, removed: 0, candles: [] as unknown[][], volumes: [] as unknown[][], element: null as HTMLElement | null };
  const series = (sink: unknown[][]) => ({
    setData: (data: unknown[]) => sink.push(data),
    priceScale: () => ({ applyOptions: () => undefined }),
  });
  let added = 0;
  const library = {
    CandlestickSeries: { kind: 'candles' },
    HistogramSeries: { kind: 'volume' },
    createChart: (element: HTMLElement) => {
      calls.created++;
      calls.element = element;
      return {
        addSeries: () => (added++ === 0 ? series(calls.candles) : series(calls.volumes)),
        timeScale: () => ({ fitContent: () => undefined }),
        remove: () => calls.removed++,
      };
    },
  };
  return { calls, library: library as unknown as ChartLibrary };
}

@Component({
  imports: [CandleChart],
  template: `@if (shown()) {
    <app-candle-chart [candles]="candles()" label="MRF.NS, six months" />
  }`,
})
class Host {
  readonly candles = signal<readonly Candle[]>(DAYS);
  readonly shown = signal(true);
}

describe('toChartData', () => {
  it('maps each day, and colours its volume by how the day closed; a day without volume has no bar', () => {
    const data = toChartData(DAYS, 'green', 'red');

    expect(data.candles).toEqual([
      { time: '2026-10-05', open: 100, high: 110, low: 95, close: 108 },
      { time: '2026-10-06', open: 108, high: 109, low: 99, close: 101 },
    ]);
    expect(data.volumes).toEqual([{ time: '2026-10-05', value: 5000, color: 'green' }]);
  });
});

describe('CandleChart', () => {
  async function render() {
    const fake = fakeLibrary();
    TestBed.configureTestingModule({ providers: [{ provide: CHART_LIBRARY, useValue: () => Promise.resolve(fake.library) }] });
    const fixture = TestBed.createComponent(Host);
    await fixture.whenStable();
    for (let i = 0; i < 5; i++) {
      await Promise.resolve();
    }
    await fixture.whenStable();
    return { fixture, calls: fake.calls };
  }

  it('draws the candles and the volume into its own box, labelled for a screen reader', async () => {
    const { fixture, calls } = await render();
    const box = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="candle-chart"]')!;

    expect(calls.created).toBe(1);
    expect(calls.element).toBe(box);
    expect(box.getAttribute('role')).toBe('img');
    expect(box.getAttribute('aria-label')).toBe('MRF.NS, six months');
    expect(calls.candles.at(-1)).toHaveLength(2);
    expect(calls.volumes.at(-1)).toHaveLength(1);
  });

  it('redraws when the candles change, in the same chart', async () => {
    const { fixture, calls } = await render();

    fixture.componentInstance.candles.set(DAYS.slice(0, 1));
    await fixture.whenStable();

    expect(calls.created).toBe(1);
    expect(calls.candles.at(-1)).toHaveLength(1);
  });

  it('removes the chart with the component', async () => {
    const { fixture, calls } = await render();

    fixture.componentInstance.shown.set(false);
    await fixture.whenStable();

    expect(calls.removed).toBe(1);
  });
});
