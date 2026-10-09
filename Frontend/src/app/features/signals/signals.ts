import { Component, effect, inject, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AccountAdvice, AdviceItem } from '../../../generated/advice';
import { AdviceApi } from '../../core/api/advice-api';
import { InstrumentCatalog } from '../../core/api/instrument-catalog';
import { Session } from '../../core/session/session';
import { ErrorMessage } from '../../shared/error-message/error-message';

/** What every view is computed with: the one methodology, said once on the page. */
const METHOD = '20/50-day moving average crossover, confirmed by RSI(14)';
const DISCLAIMER = 'Information, not advice. Computed from delayed educational data; past prices do not predict future ones.';

/**
 * A view on every stock the customer holds or watches (Sprint 10): BUY, SELL
 * or HOLD, how strong, and the sentence saying what produced it; or, where
 * the data cannot support a view, no signal and why. It informs; nothing
 * here places an order. Read when the page opens, and again when asked.
 */
@Component({
  selector: 'app-signals',
  imports: [ErrorMessage, RouterLink],
  templateUrl: './signals.html',
  styleUrl: './signals.css',
})
export class Signals {
  private readonly api = inject(AdviceApi);
  private readonly accountId = inject(Session).accountId;
  protected readonly catalog = inject(InstrumentCatalog);

  protected readonly advice = signal<AccountAdvice | null>(null);
  protected readonly error = signal<unknown>(null);
  protected readonly loading = signal(false);
  protected readonly method = METHOD;
  protected readonly disclaimer = DISCLAIMER;

  constructor() {
    effect(() => {
      const accountId = this.accountId();
      if (accountId !== null) {
        untracked(() => void this.load(accountId));
      }
    });
  }

  async refresh(): Promise<void> {
    const accountId = this.accountId();
    if (accountId !== null) {
      await this.load(accountId);
    }
  }

  private async load(accountId: number): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      const advice = await this.api.forAccount(accountId);
      this.advice.set(advice);
      // A fund is named, not numbered: look the symbols up once.
      void this.catalog.resolve(advice.items.map((item) => item.symbol));
    } catch (failure) {
      this.error.set(failure);
    } finally {
      this.loading.set(false);
    }
  }

  protected from(item: AdviceItem): string {
    return [item.held ? 'Held' : null, item.watched ? 'Watched' : null].filter(Boolean).join(' · ');
  }
}
