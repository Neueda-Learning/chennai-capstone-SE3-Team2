import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject, input, signal } from '@angular/core';
import { Signal } from '../../../generated/advice';
import { AdviceApi } from '../../core/api/advice-api';
import { ErrorMessage } from '../../shared/error-message/error-message';

/**
 * A stock's signal (Sprint 10): BUY, SELL or HOLD, how strong, the sentence
 * and the figures behind it, and, always in view, that it is information
 * and not advice. Read only when the customer asks: a signal nobody looks at
 * costs nothing.
 */
@Component({
  selector: 'app-advice-signal',
  imports: [DatePipe, DecimalPipe, ErrorMessage],
  templateUrl: './advice-signal.html',
  styleUrl: './advice-signal.css',
})
export class AdviceSignal {
  readonly symbol = input.required<string>();

  private readonly api = inject(AdviceApi);
  protected readonly reading = signal<Signal | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<unknown>(null);

  async read(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      this.reading.set(await this.api.signal(this.symbol()));
    } catch (failure) {
      this.error.set(failure);
    } finally {
      this.loading.set(false);
    }
  }
}
