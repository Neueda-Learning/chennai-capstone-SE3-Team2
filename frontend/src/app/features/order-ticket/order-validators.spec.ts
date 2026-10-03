import { FormControl } from '@angular/forms';
import { priceAboveZeroTwoDecimals, symbolShape, wholeNumberAboveZero } from './order-validators';

const errors = (validator: typeof wholeNumberAboveZero, value: string) => validator(new FormControl(value));

describe('order validators', () => {
  it('accept a whole quantity above zero, and nothing else', () => {
    expect(errors(wholeNumberAboveZero, '10')).toBeNull();
    for (const bad of ['0', '-1', '1.5', '1e3', 'ten', ' 2 3']) {
      expect(errors(wholeNumberAboveZero, bad), bad).not.toBeNull();
    }
  });

  it('accept a price above zero with at most two decimal places', () => {
    for (const good of ['25', '25.5', '25.50', '0.01']) {
      expect(errors(priceAboveZeroTwoDecimals, good), good).toBeNull();
    }
    for (const bad of ['0', '0.00', '25.505', '-1', '1,000', 'abc']) {
      expect(errors(priceAboveZeroTwoDecimals, bad), bad).not.toBeNull();
    }
  });

  it('accept the symbol shapes the contract allows', () => {
    for (const good of ['ACME', 'AAPL', 'INFY.NS', 'infy.ns', 'TCS.BO', 'FX:EURUSD', 'X:BTCUSD']) {
      expect(errors(symbolShape, good), good).toBeNull();
    }
    for (const bad of ['INFY.LN', 'FX:EUR', 'A B', 'AAPL;DROP', 'ABCDEFGHIJKLMNOPQRSTU']) {
      expect(errors(symbolShape, bad), bad).not.toBeNull();
    }
  });
});
