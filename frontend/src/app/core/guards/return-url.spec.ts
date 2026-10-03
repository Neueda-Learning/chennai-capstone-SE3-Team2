import { DEFAULT_RETURN_URL, safeReturnUrl } from './return-url';

const ORIGIN = 'http://localhost:4200';

describe('safeReturnUrl', () => {
  it('accepts a path on this origin, with its query and fragment', () => {
    expect(safeReturnUrl('/trade', ORIGIN)).toBe('/trade');
    expect(safeReturnUrl('/orders?status=NEW#latest', ORIGIN)).toBe('/orders?status=NEW#latest');
  });

  it('refuses an off-origin return address', () => {
    const offOrigin = [
      'https://evil.example/login',
      'http://localhost:4200.evil.example/',
      '//evil.example/login',
      '/\\evil.example',
      '/\t/evil.example',
      'javascript:alert(1)',
      'data:text/html,<script>alert(1)</script>',
      'trade',
      '',
    ];
    for (const value of offOrigin) {
      expect(safeReturnUrl(value, ORIGIN), value).toBe(DEFAULT_RETURN_URL);
    }
  });

  it('refuses anything that is not a string', () => {
    expect(safeReturnUrl(undefined, ORIGIN)).toBe(DEFAULT_RETURN_URL);
    expect(safeReturnUrl(['/trade'], ORIGIN)).toBe(DEFAULT_RETURN_URL);
  });

  it('does not return to the sign-in page itself', () => {
    expect(safeReturnUrl('/sign-in?returnUrl=%2Ftrade', ORIGIN)).toBe(DEFAULT_RETURN_URL);
  });
});
