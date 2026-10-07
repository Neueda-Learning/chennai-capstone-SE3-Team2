import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { PLService, PnlResponse, PortfolioService, PortfolioSummary, PricedPosition } from '../../../generated/portfolio';

/** The Sprint 10 portfolio routes, through the generated client (contracts/portfolio-api.yaml). */
@Injectable({ providedIn: 'root' })
export class PortfolioApi {
  private readonly portfolio = inject(PortfolioService);
  private readonly pl = inject(PLService);

  /** GET /api/v1/portfolio/{accountId}: cash, value, cost, unrealised and realised P&L. */
  summary(accountId: number): Promise<PortfolioSummary> {
    return firstValueFrom(this.portfolio.getPortfolioSummary(accountId));
  }

  /** GET /api/v1/portfolio/{accountId}/positions: every holding, priced where it could be. */
  positions(accountId: number): Promise<PricedPosition[]> {
    return firstValueFrom(this.portfolio.getPricedPositions(accountId));
  }

  pnl(accountId: number, bySymbol = false): Promise<PnlResponse> {
    return firstValueFrom(this.pl.getPnl(accountId, undefined, undefined, bySymbol));
  }
}

/** The portfolio routes' 503: no holding could be priced. Positions are still to be had unpriced. */
export function isPricingUnavailable(failure: unknown): boolean {
  return failure instanceof HttpErrorResponse && failure.status === 503 && failure.error?.errorCode === 'MKT-503';
}
