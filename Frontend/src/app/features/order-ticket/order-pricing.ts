import { Quote } from '../../../generated/extensions';
import { OrderSide } from '../../../generated/trade';

/**
 * How far a market order's limit sits from the live price: Kite's "market
 * protection". The platform takes limit orders only, so a market order is a
 * limit with room to move; the executor still fills it at the price it finds,
 * and a move past the room rejects it rather than filling at any price.
 */
export const MARKET_PROTECTION = 0.005;

/** Rounds to the paisa, never up through float noise (100.1 x 100 = 10009.999999999998). */
const up = (value: number) => Math.ceil(value * 100 - 1e-7) / 100;
const down = (value: number) => Math.floor(value * 100 + 1e-7) / 100;

/**
 * The limit a market order is sent with, in paise; null without a price. A
 * fund deals at its NAV, struck once a day, so it needs no room: its NAV
 * rounded the way that still meets it.
 */
export function marketLimit(side: OrderSide, quote: Quote, fund: boolean): number | null {
  if (fund) {
    const nav = quote.price;
    if (nav === null || nav === undefined) {
      return null;
    }
    return side === OrderSide.Buy ? up(nav) : down(nav);
  }
  const reference = expectedFillPrice(side, quote, fund);
  if (reference === null) {
    return null;
  }
  return side === OrderSide.Buy ? up(reference * (1 + MARKET_PROTECTION)) : down(reference * (1 - MARKET_PROTECTION));
}

/** What the order should fill at now: a buy at the ask, a sell at the bid, a fund at its NAV. */
export function expectedFillPrice(side: OrderSide, quote: Quote, fund: boolean): number | null {
  const touch = fund ? null : side === OrderSide.Buy ? quote.ask : quote.bid;
  return touch ?? quote.price ?? null;
}

/** The whole units an amount buys at a NAV. A fraction of a unit is not sold here. */
export function unitsForAmount(amount: number, nav: number): number {
  return Math.floor(amount / nav + 1e-9);
}
