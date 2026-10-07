import { Quote } from '../../../generated/extensions';
import { PricedPosition } from '../../../generated/portfolio';
import { PositionResponse } from '../../../generated/trade';

/** One holding at its live price. The priced figures are null while it has no price. */
export interface ValuedHolding {
  readonly symbol: string;
  readonly quantity: number;
  readonly averageCost: number;
  /** quantity x average cost. */
  readonly invested: number;
  readonly lastPrice: number | null;
  readonly currentValue: number | null;
  /** Unrealised: current value less what it cost. */
  readonly pnl: number | null;
  /** Percentage points against cost. */
  readonly pnlPercent: number | null;
  /** quantity x the price's change on the day; null for a fund. */
  readonly dayChange: number | null;
  readonly stale: boolean;
}

export interface HoldingsTotals {
  readonly invested: number;
  /** Of the priced holdings only. */
  readonly currentValue: number;
  /** Of the priced holdings only, against their own cost. */
  readonly pnl: number;
  readonly pnlPercent: number | null;
  readonly dayChange: number;
  /** At least one holding could not be priced, so the totals leave it out. */
  readonly partial: boolean;
}

/**
 * Holdings as the Sprint 10 portfolio module priced them
 * (GET /api/v1/portfolio/{id}/positions, contracts/portfolio-api.yaml): cost
 * basis, value and unrealised P&L by holding come from the server, by the
 * contract's definitions, and are only summed here. The day's change is not in
 * the contract; it comes from the quotes the screen already reads.
 */
export function fromPortfolio(
  positions: readonly PricedPosition[],
  quotes: ReadonlyMap<string, Quote | undefined>,
): { rows: ValuedHolding[]; totals: HoldingsTotals } {
  const rows = positions.map((position): ValuedHolding => {
    const change = quotes.get(position.symbol)?.change;
    const lastPrice = position.lastPrice ?? null;
    return {
      symbol: position.symbol,
      quantity: position.quantity,
      averageCost: position.averageCost,
      invested: position.costBasis,
      lastPrice,
      currentValue: position.marketValue ?? null,
      pnl: position.unrealisedPnl ?? null,
      pnlPercent: position.unrealisedPnlPercent ?? null,
      dayChange: lastPrice === null || change === null || change === undefined ? null : position.quantity * change,
      stale: position.stale,
    };
  });

  const priced = rows.filter((row) => row.currentValue !== null);
  const pricedCost = priced.reduce((sum, row) => sum + row.invested, 0);
  const pnl = priced.reduce((sum, row) => sum + row.pnl!, 0);
  return {
    rows,
    totals: {
      invested: rows.reduce((sum, row) => sum + row.invested, 0),
      currentValue: priced.reduce((sum, row) => sum + row.currentValue!, 0),
      pnl,
      pnlPercent: pricedCost === 0 ? null : (pnl / pricedCost) * 100,
      dayChange: rows.reduce((sum, row) => sum + (row.dayChange ?? 0), 0),
      partial: priced.length < rows.length,
    },
  };
}

/**
 * Holdings with no price at all: what the screen shows when the portfolio
 * routes answer MKT-503. The contract asks for exactly this degradation:
 * positions and cost from the Trade REST API, never an empty portfolio.
 */
export function unpriced(positions: readonly PositionResponse[]): PricedPosition[] {
  return positions.map((position) => ({
    accountId: position.accountId,
    symbol: position.symbol,
    quantity: position.quantity,
    averageCost: position.averageCost,
    costBasis: position.quantity * position.averageCost,
    lastPrice: null,
    marketValue: null,
    unrealisedPnl: null,
    unrealisedPnlPercent: null,
    currency: 'INR',
    priceAsOf: null,
    stale: true,
  }));
}
