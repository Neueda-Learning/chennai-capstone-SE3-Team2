import { Quote } from '../../../generated/extensions';
import { PricedPosition } from '../../../generated/portfolio';
import { PositionResponse } from '../../../generated/trade';
import { fromPortfolio, unpriced } from './valuation';

const priced = (symbol: string, quantity: number, averageCost: number, lastPrice: number | null): PricedPosition => ({
  accountId: 3,
  symbol,
  quantity,
  averageCost,
  costBasis: quantity * averageCost,
  lastPrice,
  marketValue: lastPrice === null ? null : quantity * lastPrice,
  unrealisedPnl: lastPrice === null ? null : quantity * (lastPrice - averageCost),
  unrealisedPnlPercent: lastPrice === null ? null : ((lastPrice - averageCost) / averageCost) * 100,
  currency: 'INR',
  priceAsOf: lastPrice === null ? null : '2026-10-07T04:00:00Z',
  stale: lastPrice === null,
});
const quote = (symbol: string, change: number | null): Quote => ({ symbol, price: 1, change, currency: 'INR', stale: false });

describe('fromPortfolio', () => {
  it("takes each holding's figures from the portfolio routes, and the day change from the quote", () => {
    const valued = fromPortfolio([priced('SBIN.NS', 10, 900, 950)], new Map([['SBIN.NS', quote('SBIN.NS', -5)]]));
    const sbin = valued.rows[0];

    expect(sbin.invested).toBe(9000);
    expect(sbin.lastPrice).toBe(950);
    expect(sbin.currentValue).toBe(9500);
    expect(sbin.pnl).toBe(500);
    expect(sbin.pnlPercent).toBeCloseTo(5.5556, 4);
    expect(sbin.dayChange).toBe(-50);
  });

  it('totals what was priced, and says when the totals leave some out', () => {
    const valued = fromPortfolio(
      [priced('SBIN.NS', 10, 900, 950), priced('ITC.NS', 100, 250, 260), priced('GONE.NS', 5, 10, null)],
      new Map([
        ['SBIN.NS', quote('SBIN.NS', -5)],
        ['ITC.NS', quote('ITC.NS', 2)],
      ]),
    );

    expect(valued.totals.invested).toBe(9000 + 25000 + 50);
    expect(valued.totals.currentValue).toBe(9500 + 26000);
    expect(valued.totals.pnl).toBe(500 + 1000);
    expect(valued.totals.pnlPercent).toBeCloseTo((1500 / 34000) * 100, 6);
    expect(valued.totals.dayChange).toBe(-50 + 200);
    expect(valued.totals.partial).toBe(true);
    expect(valued.rows[2].currentValue).toBeNull();
    expect(valued.rows[2].stale).toBe(true);
  });

  it('has no day change for a fund, whose NAV comes without one', () => {
    const valued = fromPortfolio([priced('122639', 56, 88, 88.762)], new Map([['122639', quote('122639', null)]]));

    expect(valued.rows[0].dayChange).toBeNull();
    expect(valued.totals.partial).toBe(false);
  });

  it('is empty and whole with no holdings', () => {
    expect(fromPortfolio([], new Map()).totals).toEqual({
      invested: 0, currentValue: 0, pnl: 0, pnlPercent: null, dayChange: 0, partial: false,
    });
  });
});

describe('unpriced', () => {
  it('keeps every holding at its cost, with no price, and stale: the contract degradation for MKT-503', () => {
    const held: PositionResponse[] = [{ accountId: 3, symbol: 'ITC.NS', quantity: 10, averageCost: 250 }];

    const [itc] = unpriced(held);

    expect(itc.costBasis).toBe(2500);
    expect(itc.lastPrice).toBeNull();
    expect(itc.stale).toBe(true);
    expect(fromPortfolio(unpriced(held), new Map()).totals.partial).toBe(true);
  });
});
