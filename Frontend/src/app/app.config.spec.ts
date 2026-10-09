import { formatCurrency, formatNumber } from '@angular/common';
import { LOCALE_ID } from '@angular/core';
import { appConfig } from './app.config';

describe('appConfig', () => {
  const provided = (token: unknown) =>
    (appConfig.providers as Array<{ provide?: unknown; useValue?: unknown }>).find((p) => p?.provide === token)?.useValue;

  it('writes money and numbers the Indian way: lakhs and crores', () => {
    const locale = provided(LOCALE_ID) as string;

    expect(locale).toBe('en-IN');
    expect(formatCurrency(429877.57, locale, '₹', 'INR', '1.2-2')).toBe('₹4,29,877.57');
    expect(formatNumber(12345678.9, locale, '1.2-2')).toBe('1,23,45,678.90');
  });
});
