import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { StrategiesService, Strategy, StrategyRequest, StrategyRun } from '../../../generated/strategy';

/** The customer's strategies (Sprint 10), through the generated client. Firing is the platform's, never the browser's. */
@Injectable({ providedIn: 'root' })
export class StrategiesApi {
  private readonly strategies = inject(StrategiesService);

  /** GET /api/v1/accounts/{id}/strategies: newest first, in every state. */
  list(accountId: number): Promise<Strategy[]> {
    return firstValueFrom(this.strategies.getStrategies(accountId));
  }

  /** Created switched off: nothing fires until it is switched on. */
  create(accountId: number, request: StrategyRequest): Promise<Strategy> {
    return firstValueFrom(this.strategies.createStrategy(accountId, request));
  }

  /** Off stops it at once; on arms it again, its failures forgotten. */
  setEnabled(accountId: number, strategyId: number, enabled: boolean): Promise<Strategy> {
    return firstValueFrom(this.strategies.setStrategyEnabled(accountId, strategyId, { enabled }));
  }

  async remove(accountId: number, strategyId: number): Promise<void> {
    await firstValueFrom(this.strategies.deleteStrategy(accountId, strategyId));
  }

  /** Every firing, refusal and outcome, newest first. */
  runs(accountId: number, strategyId: number): Promise<StrategyRun[]> {
    return firstValueFrom(this.strategies.getStrategyRuns(accountId, strategyId));
  }
}
