import { Quote } from '../../../generated/extensions';
import { PositionResponse } from '../../../generated/trade';
import { value } from './valuation';

const position = (symbol: string, quantity: number, averageCost: number): PositionResponse => ({ accountId: 3, symbol, quantity, averageCost });
const quote = (symbol: string, price: number | null, change: number | null = null): Quote =>
  ({ symbol, price, change, currency: 'INR', stale: false });

describe('value', () => {
  it('prices each holding at its live price: value, P&L against cost, and the day change', () => {
    const valued = value([position('SBIN.NS', 10, 900)], new Map([['SBIN.NS', quote('SBIN.NS', 950, -5)]]));
    const sbin = valued.rows[0];

    expect(sbin.invested).toBe(9000);
    expect(sbin.lastPrice).toBe(950);
    expect(sbin.currentValue).toBe(9500);
    expect(sbin.pnl).toBe(500);
    expect(sbin.pnlPercent).toBeCloseTo(5.5556, 4);
    expect(sbin.dayChange).toBe(-50);
  });

  it('totals the holdings, and says when the totals leave some out', () => {
    const valued = value(
      [position('SBIN.NS', 10, 900), position('ITC.NS', 100, 250), position('GONE.NS', 5, 10)],
      new Map([
        ['SBIN.NS', quote('SBIN.NS', 950, -5)],
        ['ITC.NS', quote('ITC.NS', 260, 2)],
        ['GONE.NS', quote('GONE.NS', null)],
      ]),
    );

    expect(valued.totals.invested).toBe(9000 + 25000 + 50);
    // The unpriced holding counts at cost in neither the value nor the P&L.
    expect(valued.totals.currentValue).toBe(9500 + 26000);
    expect(valued.totals.pnl).toBe(500 + 1000);
    expect(valued.totals.pnlPercent).toBeCloseTo((1500 / 34000) * 100, 6);
    expect(valued.totals.dayChange).toBe(-50 + 200);
    expect(valued.totals.partial).toBe(true);
    expect(valued.rows[2].currentValue).toBeNull();
  });

  it('has no day change for a fund, whose NAV comes without one', () => {
    const valued = value([position('122639', 56, 88)], new Map([['122639', quote('122639', 88.762)]]));

    expect(valued.rows[0].dayChange).toBeNull();
    expect(valued.rows[0].currentValue).toBeCloseTo(4970.672, 6);
    expect(valued.totals.partial).toBe(false);
  });

  it('is empty and whole with no holdings', () => {
    expect(value([], new Map()).totals).toEqual({
      invested: 0, currentValue: 0, pnl: 0, pnlPercent: null, dayChange: 0, partial: false,
    });
  });
});
