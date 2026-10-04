import { Injectable, computed, inject, signal } from '@angular/core';
import { InstrumentResponse } from '../../../generated/extensions';
import { InstrumentsApi } from './instruments-api';

/** The two dashboards: stocks and ETFs on one, mutual funds on the other. */
export type Segment = 'stocks' | 'mutual-funds';

/**
 * Every tradable instrument, read once per session and shared by every
 * screen: the order ticket's picker, and how holdings and order history name
 * an instrument and which dashboard it belongs on.
 *
 * A customer sees a stock by its ticker and a fund by its name. An AMFI
 * scheme code means nothing to them.
 */
@Injectable({ providedIn: 'root' })
export class InstrumentCatalog {
  private readonly api = inject(InstrumentsApi);
  private readonly list = signal<readonly InstrumentResponse[] | null>(null);
  private readonly bySymbol = computed(() => new Map((this.list() ?? []).map((i) => [i.symbol, i])));
  private loading: Promise<void> | null = null;

  /** null until read. */
  readonly instruments = this.list.asReadonly();
  /** Why the last read failed, for the screen to say; null otherwise. */
  readonly error = signal<unknown>(null);

  /** Reads the list once; later calls share that read. A failed read is tried again by the next call. */
  load(): Promise<void> {
    this.loading ??= this.api.tradable().then(
      (list) => {
        this.list.set(list);
        this.error.set(null);
      },
      (failure) => {
        this.error.set(failure);
        this.loading = null;
      },
    );
    return this.loading;
  }

  /** What a customer sees for a symbol: a stock's ticker, a fund's name. */
  label(symbol: string): string {
    const instrument = this.bySymbol().get(symbol);
    return instrument?.type === 'MF' ? instrument.name : symbol;
  }

  /**
   * Which dashboard a symbol belongs on, by its type. An instrument no longer
   * tradable is not in the list; then the symbol decides -- AMFI scheme codes
   * are digits.
   */
  segmentOf(symbol: string): Segment {
    const instrument = this.bySymbol().get(symbol);
    if (instrument) {
      return instrument.type === 'MF' ? 'mutual-funds' : 'stocks';
    }
    return /^\d+$/.test(symbol) ? 'mutual-funds' : 'stocks';
  }

  /** Still tradable, so a holding of it can be sold from the dashboard. */
  isTradable(symbol: string): boolean {
    return this.bySymbol().has(symbol);
  }
}
