import { Component, InjectionToken, computed, inject, input, output, signal } from '@angular/core';
import { InstrumentResponse } from '../../../generated/extensions';
import { InstrumentCatalog } from '../../core/api/instrument-catalog';
import { InstrumentType, InstrumentsApi } from '../../core/api/instruments-api';
import { ErrorMessage } from '../error-message/error-message';

/** How long typing must pause before the search is sent. 0 in tests. */
export const SEARCH_DELAY_MS = new InjectionToken<number>('SEARCH_DELAY_MS', { factory: () => 150 });

let nextId = 0;

/**
 * Find an instrument by typing: a ticker, a company or a fund's name. A
 * combobox: the arrows move through the results, Enter picks, Escape closes.
 * Four thousand instruments are searched on the server; only the best eight
 * come back.
 */
@Component({
  selector: 'app-instrument-search',
  imports: [ErrorMessage],
  templateUrl: './instrument-search.html',
  styleUrl: './instrument-search.css',
})
export class InstrumentSearch {
  private readonly api = inject(InstrumentsApi);
  private readonly catalog = inject(InstrumentCatalog);
  private readonly delay = inject(SEARCH_DELAY_MS);

  /** Only this type, e.g. funds on the funds screen. */
  readonly type = input<InstrumentType | undefined>(undefined);
  readonly placeholder = input('Search eg: infy, tata steel, parag parikh');
  /** The input's id, for a label elsewhere on the page. */
  readonly inputId = input(`instrument-search-${nextId++}`);
  /** Read out with the box when it has no visible label. */
  readonly ariaLabel = input<string | null>(null);
  readonly invalid = input(false);
  readonly describedBy = input<string | null>(null);

  readonly picked = output<InstrumentResponse>();

  protected readonly text = signal('');
  protected readonly results = signal<readonly InstrumentResponse[] | null>(null);
  protected readonly active = signal(-1);
  protected readonly error = signal<unknown>(null);
  protected readonly listId = computed(() => `${this.inputId()}-list`);

  /** Only the answer to the latest text is shown. */
  private sequence = 0;
  private timer: ReturnType<typeof setTimeout> | null = null;

  protected optionId(index: number): string {
    return `${this.listId()}-${index}`;
  }

  /** A stock or ETF by its ticker, a fund by its name. */
  protected primary(instrument: InstrumentResponse): string {
    return instrument.type === 'MF' ? instrument.name : instrument.symbol;
  }

  protected secondary(instrument: InstrumentResponse): string {
    return instrument.type === 'MF' ? 'Mutual fund' : `${instrument.name} · ${instrument.exchange ?? instrument.type}`;
  }

  protected typed(value: string): void {
    this.text.set(value);
    if (this.timer !== null) {
      clearTimeout(this.timer);
      this.timer = null;
    }
    const query = value.trim();
    const mine = ++this.sequence;
    if (query === '') {
      this.close();
      return;
    }
    if (this.delay > 0) {
      this.timer = setTimeout(() => void this.search(query, mine), this.delay);
    } else {
      void this.search(query, mine);
    }
  }

  private async search(query: string, mine: number): Promise<void> {
    try {
      const found = await this.api.search(query, this.type());
      if (mine !== this.sequence) {
        return;
      }
      this.catalog.remember(found);
      this.error.set(null);
      this.results.set(found);
      this.active.set(found.length > 0 ? 0 : -1);
    } catch (failure) {
      if (mine === this.sequence) {
        this.results.set(null);
        this.error.set(failure);
      }
    }
  }

  protected keydown(event: KeyboardEvent): void {
    const results = this.results() ?? [];
    switch (event.key) {
      case 'ArrowDown':
        if (results.length > 0) {
          this.active.set((this.active() + 1) % results.length);
          event.preventDefault();
        }
        break;
      case 'ArrowUp':
        if (results.length > 0) {
          this.active.set((this.active() - 1 + results.length) % results.length);
          event.preventDefault();
        }
        break;
      case 'Enter':
        if (this.active() >= 0 && this.active() < results.length) {
          this.choose(results[this.active()]);
          event.preventDefault();
        }
        break;
      case 'Escape':
        this.close();
        break;
    }
  }

  protected choose(instrument: InstrumentResponse): void {
    this.picked.emit(instrument);
    this.text.set('');
    this.sequence++;
    this.close();
  }

  /** Leaving the box closes the list; a pick happens on mousedown, before the blur. */
  protected close(): void {
    this.results.set(null);
    this.active.set(-1);
  }
}
