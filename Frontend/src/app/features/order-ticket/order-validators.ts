import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/**
 * The ticket's checks, so the obvious mistakes never reach the wire. Not
 * enforcement: business rules 1 to 8 live in the Trade REST API and stay
 * there, and the error rendering exists because the rest of them will.
 */

/**
 * The symbol shapes the contract allows (PlaceOrderRequest.symbol, 1-20
 * characters): a plain ticker, a `.NS` or `.BO` suffix for NSE or BSE,
 * `FX:` and a currency pair, `X:` and a crypto symbol. Checked upper-cased,
 * because the ticket sends it upper-cased: `infy.ns` is INFY.NS.
 */
export const SYMBOL_PATTERN = /^(?:[A-Z0-9]{1,15}(?:\.(?:NS|BO))?|FX:[A-Z]{6}|X:[A-Z0-9]{2,17})$/;

/** A whole number of units, greater than zero. Business rule 4. */
export const wholeNumberAboveZero: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const value = String(control.value ?? '').trim();
  if (value === '') {
    return null; // `required` reports an empty field.
  }
  if (!/^\d+$/.test(value) || Number(value) < 1) {
    return { wholeNumberAboveZero: true };
  }
  return Number(value) > 2_147_483_647 ? { tooLarge: true } : null;
};

/** Greater than zero, at most two decimal places. Business rule 5. */
export const priceAboveZeroTwoDecimals: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const value = String(control.value ?? '').trim();
  if (value === '') {
    return null;
  }
  if (!/^\d+(?:\.\d{1,2})?$/.test(value)) {
    return { priceFormat: true };
  }
  return Number(value) > 0 ? null : { priceAboveZero: true };
};

export const symbolShape: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const value = String(control.value ?? '').trim().toUpperCase();
  if (value === '') {
    return null;
  }
  return value.length <= 20 && SYMBOL_PATTERN.test(value) ? null : { symbolShape: true };
};
