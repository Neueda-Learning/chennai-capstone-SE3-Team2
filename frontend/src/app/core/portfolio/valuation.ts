import { Quote } from '../../../generated/extensions';
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
 * Holdings priced on screen from live prices: value and unrealised P&L by
 * holding, and in total, as Kite's Holdings page shows them. The Sprint 10
 * portfolio module is to compute these on the server
 * (contracts/portfolio-api.yaml); until then the screen does, by the same
 * definitions: cost basis = quantity x average cost, market value = quantity
 * x last price, unrealised P&L = market value - cost basis.
 */
export function value(
  positions: readonly PositionResponse[],
  quotes: ReadonlyMap<string, Quote | undefined>,
): { rows: ValuedHolding[]; totals: HoldingsTotals } {
  const rows = positions.map((position): ValuedHolding => {
    const quote = quotes.get(position.symbol);
    const invested = position.quantity * position.averageCost;
    const lastPrice = quote?.price ?? null;
    const currentValue = lastPrice === null ? null : position.quantity * lastPrice;
    const pnl = currentValue === null ? null : currentValue - invested;
    return {
      symbol: position.symbol,
      quantity: position.quantity,
      averageCost: position.averageCost,
      invested,
      lastPrice,
      currentValue,
      pnl,
      pnlPercent: pnl === null || invested === 0 ? null : (pnl / invested) * 100,
      dayChange: lastPrice === null || quote?.change === null || quote?.change === undefined ? null : position.quantity * quote.change,
      stale: quote?.stale ?? false,
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
