import { Quote } from '../../../generated/extensions';
import { OrderSide } from '../../../generated/trade';
import { MARKET_PROTECTION, expectedFillPrice, marketLimit, unitsForAmount } from './order-pricing';

const stock: Quote = { symbol: 'SBIN.NS', price: 951.85, bid: 951.7, ask: 952, currency: 'INR', stale: false };
const fund: Quote = { symbol: '122639', price: 88.762, currency: 'INR', stale: false };

describe('marketLimit', () => {
  it(`buys with ${MARKET_PROTECTION * 100}% of room above the ask, rounded up to the paisa`, () => {
    // 952 x 1.005 = 956.76
    expect(marketLimit(OrderSide.Buy, stock, false)).toBe(956.76);
  });

  it('sells with the same room below the bid, rounded down to the paisa', () => {
    // 951.70 x 0.995 = 946.9415
    expect(marketLimit(OrderSide.Sell, stock, false)).toBe(946.94);
  });

  it('deals a fund at its NAV: rounded up to buy, down to sell, so the NAV always meets it', () => {
    expect(marketLimit(OrderSide.Buy, fund, true)).toBe(88.77);
    expect(marketLimit(OrderSide.Sell, fund, true)).toBe(88.76);
  });

  it('falls back to the last price without a bid or ask, and gives up without any price', () => {
    expect(marketLimit(OrderSide.Buy, { ...stock, ask: null }, false)).toBe(956.61);
    expect(marketLimit(OrderSide.Buy, { ...stock, price: null, ask: null }, false)).toBeNull();
    expect(marketLimit(OrderSide.Buy, { ...fund, price: null }, true)).toBeNull();
  });

  it('does not round a price already in paise up a paisa through float noise', () => {
    expect(marketLimit(OrderSide.Buy, { ...fund, price: 100.1 }, true)).toBe(100.1);
  });
});

describe('expectedFillPrice', () => {
  it('is the ask for a buy, the bid for a sell, the NAV for a fund', () => {
    expect(expectedFillPrice(OrderSide.Buy, stock, false)).toBe(952);
    expect(expectedFillPrice(OrderSide.Sell, stock, false)).toBe(951.7);
    expect(expectedFillPrice(OrderSide.Sell, fund, true)).toBe(88.762);
  });
});

describe('unitsForAmount', () => {
  it('buys the whole units the amount covers at the NAV', () => {
    // 5000 / 88.762 = 56.33
    expect(unitsForAmount(5000, 88.762)).toBe(56);
    expect(unitsForAmount(88.762, 88.762)).toBe(1);
    expect(unitsForAmount(50, 88.762)).toBe(0);
  });
});
