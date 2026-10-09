import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AccountAdvice, AdviceService, Signal } from '../../../generated/advice';

/** Signals (Sprint 10), through the generated client: one stock's, or an account's holdings and watchlists. */
@Injectable({ providedIn: 'root' })
export class AdviceApi {
  private readonly advice = inject(AdviceService);

  /** GET /api/v1/advice/{symbol}. */
  signal(symbol: string): Promise<Signal> {
    return firstValueFrom(this.advice.getSignal(symbol));
  }

  /** GET /api/v1/accounts/{id}/advice: every stock held or watched, holdings first. */
  forAccount(accountId: number): Promise<AccountAdvice> {
    return firstValueFrom(this.advice.getAccountAdvice(accountId));
  }
}
