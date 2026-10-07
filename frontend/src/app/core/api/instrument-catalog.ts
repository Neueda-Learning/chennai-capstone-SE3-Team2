import { Injectable, inject, signal } from '@angular/core';
import { InstrumentResponse } from '../../../generated/extensions';
import { InstrumentsApi } from './instruments-api';

/** The two dashboards: stocks and ETFs on one, mutual funds on the other. */
export type Segment = 'stocks' | 'mutual-funds';

/** The most symbols one lookup takes. */
const LOOKUP_LIMIT = 50;

/**
 * What the instruments on screen are, shared by every screen: how holdings,
 * orders and the market watch name an instrument, which dashboard it belongs
 * on, and whether it can still be sold.
 *
 * Four thousand instruments are too many to read up front, so a screen asks
 * for the symbols it shows (resolve) and a search shares what it found
 * (remember). Each symbol is asked for once a session.
 *
 * A customer sees a stock by its ticker and a fund by its name. An AMFI
 * scheme code means nothing to them.
 */
@Injectable({ providedIn: 'root' })
export class InstrumentCatalog {
  private readonly api = inject(InstrumentsApi);
  private readonly known = signal<ReadonlyMap<string, InstrumentResponse>>(new Map());
  /** Looked up already, found or not, or being looked up now. */
  private readonly asked = new Set<string>();
  /** Lookups under way, by symbol: a second screen asking waits for the first answer. */
  private readonly pending = new Map<string, Promise<unknown>>();
  /** Looked up, and nobody lists them. */
  private readonly missing = signal<ReadonlySet<string>>(new Set());

  /** Why the last lookup failed, for the screen to say; null otherwise. */
  readonly error = signal<unknown>(null);

  /**
   * Looks up the symbols not asked for yet, and waits for any another screen
   * is already looking up, so the caller can read them when this returns. A
   * failed lookup is asked again next time.
   */
  async resolve(symbols: Iterable<string>): Promise<void> {
    const distinct = [...new Set(symbols)];
    const underWay = new Set(distinct.flatMap((symbol) => this.pending.get(symbol) ?? []));
    const wanted = distinct.filter((symbol) => !this.asked.has(symbol));
    wanted.forEach((symbol) => this.asked.add(symbol));
    for (let start = 0; start < wanted.length; start += LOOKUP_LIMIT) {
      const chunk = wanted.slice(start, start + LOOKUP_LIMIT);
      const lookup = this.api.lookup(chunk);
      chunk.forEach((symbol) => this.pending.set(symbol, lookup));
      try {
        const found = await lookup;
        this.remember(found);
        const listed = new Set(found.map((instrument) => instrument.symbol));
        const absent = chunk.filter((symbol) => !listed.has(symbol));
        if (absent.length > 0) {
          this.missing.update((missing) => new Set([...missing, ...absent]));
        }
        this.error.set(null);
      } catch (failure) {
        chunk.forEach((symbol) => this.asked.delete(symbol));
        this.error.set(failure);
      } finally {
        chunk.forEach((symbol) => this.pending.delete(symbol));
      }
    }
    // Another screen's lookup of some of these: its answer, whatever it is, before this returns.
    for (const lookup of underWay) {
      await lookup.catch(() => undefined);
    }
  }

  /** Instruments a search or a lookup returned. */
  remember(instruments: readonly InstrumentResponse[]): void {
    if (instruments.length === 0) {
      return;
    }
    instruments.forEach((instrument) => this.asked.add(instrument.symbol));
    this.known.update((known) => new Map([...known, ...instruments.map((i) => [i.symbol, i] as const)]));
  }

  /** Looked up, and not listed: a symbol from an old list, or one never seeded. */
  isMissing(symbol: string): boolean {
    return this.missing().has(symbol);
  }

  get(symbol: string): InstrumentResponse | undefined {
    return this.known().get(symbol);
  }

  /** What a customer sees for a symbol: a stock's ticker, a fund's name. */
  label(symbol: string): string {
    const instrument = this.get(symbol);
    return instrument?.type === 'MF' ? instrument.name : symbol;
  }

  /**
   * Which dashboard a symbol belongs on, by its type. Before the lookup
   * answers, the symbol decides: AMFI scheme codes are digits.
   */
  segmentOf(symbol: string): Segment {
    const instrument = this.get(symbol);
    if (instrument) {
      return instrument.type === 'MF' ? 'mutual-funds' : 'stocks';
    }
    return /^\d+$/.test(symbol) ? 'mutual-funds' : 'stocks';
  }

  /** Still tradable, so a holding of it can be sold. Unknown until looked up. */
  isTradable(symbol: string): boolean {
    return this.get(symbol)?.tradable === true;
  }
}
