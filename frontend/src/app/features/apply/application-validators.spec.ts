import { FormControl, ValidatorFn } from '@angular/forms';
import { adultOn, bankAccountShape, ifscShape, mobileShape, panShape } from './application-validators';

const passes = (validator: ValidatorFn, value: string) => validator(new FormControl(value)) === null;

describe('application validators', () => {
  it('accept a PAN, an IFSC, a mobile and a bank account in the shapes the server accepts', () => {
    expect(passes(panShape, 'ABCPM1234Q')).toBe(true);
    expect(passes(panShape, 'abcpm1234q')).toBe(true);
    expect(passes(panShape, 'ABCPM1234')).toBe(false);
    expect(passes(ifscShape, 'HDFC0001234')).toBe(true);
    expect(passes(ifscShape, 'HDFC1001234')).toBe(false);
    expect(passes(mobileShape, '9812345611')).toBe(true);
    expect(passes(mobileShape, '98123 45611')).toBe(true);
    expect(passes(mobileShape, '5812345611')).toBe(false);
    expect(passes(bankAccountShape, '509876543210')).toBe(true);
    expect(passes(bankAccountShape, '12345678')).toBe(false);
    expect(passes(bankAccountShape, '5098765432AB')).toBe(false);
  });

  it('accept an adult, refuse a minor, and refuse a date that does not exist', () => {
    const adult = adultOn(() => new Date(2026, 9, 3));

    expect(passes(adult, '2008-10-03')).toBe(true);
    expect(adult(new FormControl('2008-10-04'))).toEqual({ underage: true });
    expect(adult(new FormControl('1990-02-30'))).toEqual({ dob: true });
  });
});
