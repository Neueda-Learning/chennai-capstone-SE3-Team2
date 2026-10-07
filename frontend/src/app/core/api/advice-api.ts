import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AdviceService, Signal } from '../../../generated/advice';

/** A stock's signal (Sprint 10), through the generated client. */
@Injectable({ providedIn: 'root' })
export class AdviceApi {
  private readonly advice = inject(AdviceService);

  /** GET /api/v1/advice/{symbol}. */
  signal(symbol: string): Promise<Signal> {
    return firstValueFrom(this.advice.getSignal(symbol));
  }
}
